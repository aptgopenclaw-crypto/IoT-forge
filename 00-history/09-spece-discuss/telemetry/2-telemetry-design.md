# Telemetry 模組設計 — JSON 格式與 Schema 驗證

> 日期：2026-06-29
> 相關決策來自 `00-history/telemetry/1.md` 的選單結構建議

---

## 一、架構總覽

```
┌──────────────┐     MQTT topic: device/{deviceCode}/telemetry
│   IoT Device │ ──────────────────────────────────────►
└──────────────┘                                         │
                                                         ▼
                                              ┌──────────────────┐
                                              │   MQTT Handler   │
                                              │ (TelemetryMqtt   │
                                              │  Handler)        │
                                              └────────┬─────────┘
                                                       │
                                              ┌────────▼─────────┐
                                              │  JSON Schema     │
                                              │  Validator       │
                                              │ (networknt)      │
                                              └────────┬─────────┘
                                                       │ valid?
                                              ┌────────▼─────────┐
                                              │  TelemetryData   │
                                              │  Service         │
                                              └────────┬─────────┘
                                                       │
                                              ┌────────▼─────────┐
                                              │  TimescaleDB     │
                                              │  telemetry_data  │
                                              └──────────────────┘
```

---

## 二、Telemetry JSON 格式（方案 B：含 metadata）

### 設備端上送格式（MQTT payload）

```json
{
  "ts": "2026-06-29T10:30:00Z",
  "values": {
    "temperature": 25.3,
    "humidity": 68.5,
    "voltage": 220.1,
    "switch": "on",
    "power": 45.2
  }
}
```

| 欄位 | 型態 | 必填 | 說明 |
|---|---|---|---|
| `ts` | ISO8601 string | 否 | 設備端時間戳；若無則由伺服器以 `received_at` 填入 |
| `values` | object | 是 | 實際遙測 key-value 對，需符合 DeviceTemplate 定義的 telemetry schema |

### MQTT topic 識別

- Topic 格式：`device/{deviceCode}/telemetry`
- `deviceCode` 從 topic pattern 中提取，對應 `devices.device_code`
- 若 deviceCode 不存在於系統中 → 回傳錯誤，不寫入

---

## 三、DeviceTemplate.schema 拆分

`device_templates.schema`（JSONB）拆分為兩層：

```json
{
  "attributes": {
    "type": "object",
    "properties": {
      "rated_power": {
        "type": "number",
        "unit": "watt",
        "description": "額定功率"
      },
      "manufacturer": {
        "type": "string",
        "description": "製造商"
      },
      "install_position": {
        "type": "string",
        "enum": ["pole_top", "pole_middle", "ground"],
        "description": "安裝位置"
      }
    },
    "required": ["rated_power"]
  },
  "telemetry": {
    "type": "object",
    "properties": {
      "temperature": {
        "type": "number",
        "unit": "celsius",
        "description": "溫度",
        "minimum": -40,
        "maximum": 85
      },
      "humidity": {
        "type": "number",
        "unit": "percent",
        "description": "濕度",
        "minimum": 0,
        "maximum": 100
      },
      "voltage": {
        "type": "number",
        "unit": "volt",
        "description": "電壓",
        "minimum": 0,
        "maximum": 380
      },
      "switch": {
        "type": "string",
        "enum": ["on", "off", "fault"],
        "description": "開關狀態"
      },
      "power": {
        "type": "number",
        "unit": "watt",
        "description": "功率"
      }
    },
    "required": ["switch"]
  }
}
```

| 區塊 | 用途 | 驗證時機 |
|---|---|---|
| `attributes` | Device 的固定屬性欄位 | 建立/編輯 Device 時 |
| `telemetry` | 設備上送的遙測欄位定義 | 每次 MQTT telemetry 進來時 |

---

## 四、Schema 比對驗證（JSON Schema Validator）

### 使用函式庫

採用 [networknt/json-schema-validator](https://github.com/networknt/json-schema-validator)（基於 Jackson，與專案現有 Jackson 相容）。

### Maven 依賴

```xml
<dependency>
    <groupId>com.networknt</groupId>
    <artifactId>json-schema-validator</artifactId>
    <version>1.5.6</version>
</dependency>
```

### 驗證流程

```
1. MQTT Handler 收到 topic device/{deviceCode}/telemetry
2. 從 topic 提取 deviceCode → 查詢 Device (deviceCode + tenantId)
3. 從 Device 取得 deviceType → 查詢 DeviceTemplate 取得 schema
4. 從 schema 提取 $.telemetry 作為驗證用 JSON Schema
5. 從 MQTT payload 提取 $.values 作為待驗證資料
6. 執行 JSON Schema validation
7a. 驗證通過 → 寫入 telemetry_data
7b. 驗證失敗 → 記錄錯誤日誌（仍可選擇性寫入，標記 valid=false）
```

### 驗證規則

| JSON Schema 關鍵字 | 支援 | 用途 |
|---|---|---|
| `type` | ✅ | number / string / boolean / integer |
| `required` | ✅ | 必送欄位檢查 |
| `minimum` / `maximum` | ✅ | 數值範圍 |
| `minLength` / `maxLength` | ✅ | 字串長度 |
| `enum` | ✅ | 列舉值 |
| `pattern` | ✅ | 正則表達式 |
| `properties` | ✅ | 欄位定義 |

---

## 五、資料庫設計（原生 PostgreSQL 分區表）

> **儲存方案定案（2026-06-30）**：採**原生 PostgreSQL declarative range partitioning**，不依賴 TimescaleDB。
> 原因：本機 PG 18.3 僅能裝 `+dfsg` 重打包版（極可能移除 `add_retention_policy`／continuous aggregates 等 TSL 功能），且裝它需改 `shared_preload_libraries` + 重啟 Postgres（共享基礎設施變更）。
> 原生分區表 + BRIN + 排程 retention 即可滿足需求，schema-agnostic、dev/test 皆可跑，未來規模上來可透過 storage 抽象層平滑換 hypertable。

### telemetry_data 表（按 ts 月分區）

```sql
-- 父表：declarative range partition by ts
-- 注意：分區鍵 ts 必須是每個 UNIQUE/PK 的一部分 → 複合主鍵 (ts, id)
CREATE TABLE telemetry_data (
    id              BIGINT GENERATED ALWAYS AS IDENTITY, -- 或 BIGSERIAL
    tenant_id       VARCHAR(50)  NOT NULL,
    device_id       BIGINT       NOT NULL REFERENCES devices(id),
    device_type     VARCHAR(30)  NOT NULL,
    ts              TIMESTAMP    NOT NULL,              -- 資料時間點（payload.ts 或 received_at）
    received_at     TIMESTAMP    NOT NULL DEFAULT now(),
    source          VARCHAR(20)  NOT NULL DEFAULT 'MQTT',  -- 來源通道（見多來源接入）
    source_client_id VARCHAR(50),                          -- 哪個廠商客戶端
    payload         JSONB        NOT NULL,              -- 通過驗證後的 values
    raw_payload     JSONB,                               -- 原始 payload（偵錯用）
    valid           BOOLEAN      NOT NULL DEFAULT true,
    validation_msg  VARCHAR(500),                        -- 驗證失敗原因
    PRIMARY KEY (ts, id)
) PARTITION BY RANGE (ts);

-- 索引（定義在父表，自動套用到各分區）
CREATE INDEX idx_telemetry_device_ts ON telemetry_data (device_id, ts DESC);
CREATE INDEX idx_telemetry_tenant_ts ON telemetry_data (tenant_id, ts DESC);
CREATE INDEX idx_telemetry_type_ts   ON telemetry_data (device_type, ts DESC);
-- ts 本身用 BRIN（時序資料天然單調遞增，BRIN 體積極小、掃描高效）
CREATE INDEX idx_telemetry_ts_brin   ON telemetry_data USING BRIN (ts);

-- 初始分區（範例：當月 + 預建下月）
CREATE TABLE telemetry_data_2026_06 PARTITION OF telemetry_data
    FOR VALUES FROM ('2026-06-01') TO ('2026-07-01');
CREATE TABLE telemetry_data_2026_07 PARTITION OF telemetry_data
    FOR VALUES FROM ('2026-07-01') TO ('2026-08-01');
```

### 分區維護與保留（排程取代 TimescaleDB policy）

由 `@Scheduled` job（沿用既有 `@EnableScheduling`）負責：

| 工作 | 頻率 | 行為 |
|---|---|---|
| **預建分區** | 每日 | 確保「當月 + 未來 1~2 個月」分區已存在（缺則 `CREATE TABLE ... PARTITION OF`） |
| **保留清理** | 每日 | 超過保留期（如 90 天）的舊分區 `DROP TABLE`（或 `DETACH` 後封存）——比 `DELETE` 快且不產生 bloat |

> 用 `DROP`/`DETACH` 整個分區做 retention，是原生分區相對 `DELETE` 的最大優勢：O(1) 中繼資料操作、無 vacuum 壓力。

### 預計後續擴充

- `telemetry_data_hourly` / `telemetry_data_daily`：彙總表，由 `@Scheduled` job 維護（取代 continuous aggregates）。
- 規模上來時，可透過 `telemetry.storage` 抽象層，把實作換成 TimescaleDB hypertable 而不動上層。

---

## 六、後端模組結構

```
src/main/java/com/taipei/iot/telemetry/
├── config/
│   └── TelemetryMqttConfig.java          ← MQTT topic 設定
├── controller/
│   └── TelemetryController.java          ← REST API（歷史查詢、即時查詢）
├── dto/
│   ├── TelemetryDataRequest.java         ← API 查詢參數
│   └── TelemetryDataResponse.java        ← API 回應
├── entity/
│   └── TelemetryData.java                ← JPA entity
├── repository/
│   └── TelemetryDataRepository.java      ← Spring Data JPA + 自訂查詢
├── service/
│   ├── TelemetryDataService.java         ← 寫入 + 查詢邏輯
│   ├── TelemetryValidationService.java   ← JSON Schema 驗證邏輯
│   └── TelemetryMqttHandler.java         ← MQTT 訊息處理
```

---

## 七、REST API 設計

| Method | Path | 權限 | 說明 |
|---|---|---|---|
| `GET` | `/v1/auth/telemetry/data` | `TELEMETRY_VIEW` | 歷史查詢（分頁 + deviceType / deviceId / 時間範圍） |
| `GET` | `/v1/auth/telemetry/data/{id}` | `TELEMETRY_VIEW` | 單筆查詢 |
| `GET` | `/v1/auth/telemetry/data/latest` | `TELEMETRY_VIEW` | 即時資料（各 device 最新一筆） |
| `GET` | `/v1/auth/telemetry/data/stats` | `TELEMETRY_VIEW` | 統計資料（總筆數、設備數等） |

---

## 八、選單結構（後續 DB migration 加入）

```
telemetry     /telemetry
  ├─ 即時資料   /telemetry/live          (TELEMETRY_VIEW)
  ├─ 歷史查詢   /telemetry/history       (TELEMETRY_VIEW)
  └─ 資料驗證規則 /telemetry/schemas     (TELEMETRY_MANAGE)
```

---

## 九、實作步驟

- [ ] **Step 1**：DB migration — 新增 telemetry_data 表 + TimescaleDB hypertable
- [ ] **Step 2**：更新 DeviceTemplate — schema 拆分為 attributes / telemetry 兩層（撰寫 migration 腳本更新現有資料）
- [ ] **Step 3**：pom.xml — 加入 json-schema-validator + timescaledb JDBC 依賴
- [ ] **Step 4**：Entity + Repository — TelemetryData JPA entity + TelemetryDataRepository
- [ ] **Step 5**：Service — TelemetryValidationService（JSON Schema 驗證）
- [ ] **Step 6**：Service — TelemetryMqttHandler（MQTT 監聽 + 驗證 + 寫入）
- [ ] **Step 7**：Service — TelemetryDataService（查詢 API）
- [ ] **Step 8**：Controller — TelemetryController（REST API）
- [ ] **Step 9**：更新 DeviceService.validateAttributes — 改用 JSON Schema validator
- [ ] **Step 10**：更新 ErrorCode — 新增 telemetry 相關錯誤碼
- [ ] **Step 11**：Frontend — 新增 telemetry API client
- [ ] **Step 12**：Frontend — TelemetryLiveView.vue（即時資料頁面）
- [ ] **Step 13**：Frontend — TelemetryHistoryView.vue（歷史查詢頁面）
- [ ] **Step 14**：Router — 加入 telemetry routes
- [ ] **Step 15**：DB migration — 加入 telemetry 選單資料
