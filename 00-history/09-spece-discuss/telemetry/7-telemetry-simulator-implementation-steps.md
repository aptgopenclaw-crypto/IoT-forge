# Telemetry 模擬程式實作步驟（可直接開工版）

> 日期：2026-07-07
> 延續：`6-telemetry-simulator-plan.md`
> 目的：把模擬程式規劃拆成工程可直接實作的步驟、檔案、命令、驗收點。
> 範圍：**先做 CLI + HTTP ingest mode**，不先做 MQTT，不先做 replay。
> 原則：先打通最小閉環，再補 anomaly / rule profile。

---

## 一、本次實作範圍定義

### 1.1 首版要做到的事

首版模擬程式必須能：

1. 透過 CLI 啟動
2. 用既有 `X-API-Key` / `X-API-Secret` 呼叫 `/v1/ingest/telemetry/batch`
3. 從系統查詢設備與 schema
4. 依 telemetry schema 自動生成合法遙測值
5. 以固定間隔持續送資料
6. 輸出成功/失敗統計
7. 支援 `normal` 與 `anomaly` 兩種模式

### 1.2 首版不做的事

以下延後：

- MQTT mode
- replay mode
- Web UI
- 壓測併發 worker
- 外部檔案腳本驅動
- Prometheus 指標輸出

---

## 二、建議實作位置

### 2.1 採 CLI 子命令方案

建議放在現有 `01-cli/` 體系內。

```text
01-cli/
├── cmd/iotforge/
├── pkg/
│   ├── client/
│   ├── dto/
│   └── telemetrysim/   ← 新增
```

### 2.2 建議新增檔案

```text
01-cli/pkg/telemetrysim/
├── config.go              # 參數與預設值
├── runner.go              # 主執行流程
├── schema_loader.go       # 查設備與 schema
├── generator.go           # 隨機值生成
├── profiles.go            # 預設欄位 profile / anomaly profile
├── sender.go              # HTTP ingest 封裝
├── summary.go             # 執行統計
└── types.go               # 內部模型

01-cli/cmd/iotforge/
└── telemetry_sim.go       # Cobra / CLI command 註冊（若現有架構採 cobra-like）
```

---

## 三、前置盤點

### 3.1 先確認 CLI 目前能力

開工前先確認：

- `01-cli` 目前命令框架怎麼掛新 command
- 現有 `pkg/client` 是否已有共用 HTTP client，可直接複用 auth/base URL
- 目前 CLI 是否已有 device list / schema 相關 API wrapper

### 3.2 若 CLI 缺少 API wrapper，需先補

首版至少需要這幾個 client 方法：

1. `ListDevices(...)`
2. `GetTelemetrySchema(deviceType)`
3. `IngestTelemetryBatch(...)`

若 `01-cli/pkg/client` 已有 device list，則只需再補：

- telemetry schema query
- ingest batch post

---

## 四、步驟拆解

---

## Step 1 — 補齊 CLI API client 能力 ✅

### 1.1 目標

讓 CLI 可呼叫：

- 查設備清單
- 查指定 deviceType 的 telemetry schema
- 送批次 telemetry ingest

### 1.2 建議檔案

```text
01-cli/pkg/client/devices.go           # 若已存在則擴充
01-cli/pkg/client/telemetry.go         # 新增
01-cli/pkg/dto/telemetry.go            # 新增 DTO
```

### 1.3 建議 client 介面

```go
func (c *Client) ListDevices(ctx context.Context, filter *ListDevicesFilter) ([]dto.DeviceResponse, int64, error)
func (c *Client) GetTelemetrySchema(ctx context.Context, deviceType string) (map[string]any, error)
func (c *Client) IngestTelemetryBatch(ctx context.Context, req []dto.TelemetryIngestRequest, apiKey, apiSecret string) ([]dto.TelemetryIngestResult, error)
```

### 1.4 API 對應

| 用途 | API |
|---|---|
| 設備清單 | `GET /v1/auth/devices` |
| telemetry schema | `GET /v1/auth/device-templates/{deviceType}/schema/telemetry` |
| 批次 ingest | `POST /v1/ingest/telemetry/batch` |

### 1.5 ⚠️ telemetry schema 格式說明（重要）

`GET /v1/auth/device-templates/{deviceType}/schema/telemetry` 回傳的**不是**標準 JSON Schema，
而是前端設備模板編輯器儲存的 `fields[]` 格式：

```json
{
  "fields": [
    { "key": "voltage",     "type": "number", "minimum": 0, "maximum": 300, "required": true },
    { "key": "brightness",  "type": "number", "minimum": 0, "maximum": 100, "required": true },
    { "key": "powerFactor", "type": "number", "minimum": 0, "maximum": 1,   "required": true },
    { "key": "rssi",        "type": "number", "minimum": -150, "maximum": 0, "required": false },
    { "key": "controllerSerial", "type": "text", "required": true },
    { "key": "reportTime",  "type": "date",   "required": true }
  ]
}
```

Step 3 的 schema_loader 需要**解析 `fields[]` 陣列**，而非 JSON Schema `properties` 物件。

### 1.5 驗收

- 能從 CLI 層成功打到三個 API
- DTO 能正確 parse 回應

---

## Step 2 — 定義 simulator 設定與內部模型 ✅

### 2.1 目標

建立 simulator 的可配置輸入，避免後續邏輯散在各處。

### 2.2 建議檔案

```text
01-cli/pkg/telemetrysim/config.go
01-cli/pkg/telemetrysim/types.go
```

### 2.3 建議資料結構

#### `SimulatorConfig`

```go
type SimulatorConfig struct {
    BaseURL       string
    APIKey        string
    APISecret     string
    TenantID      string
    DeviceType    string
    DeviceCode    string
    DeviceLimit   int
    Interval      time.Duration
    BatchSize     int
    Duration      time.Duration
    Mode          string
    RuleProfile   string
    Seed          int64
    DryRun        bool
    Verbose       bool
}
```

#### `SimDevice`

```go
type SimDevice struct {
    DeviceID    int64
    DeviceCode  string
    DeviceType  string
    TenantID    string
}
```

#### `FieldSchema`

對應 `device_templates.schema.telemetry.fields[]` 的單一欄位，一對一映射：

```go
type FieldSchema struct {
    Name     string   // field.key
    Title    string   // field.title（可選，顯示用）
    Type     string   // "number" / "text" / "date"
    Required bool     // field.required
    Enum     []string // field.enum（若有）
    Minimum  *float64 // field.minimum
    Maximum  *float64 // field.maximum
}
```

> ⚠️ schema 格式為 `{"fields": [...]}` 而非標準 JSON Schema。`Type` 的值為 `"text"` 而非 `"string"`。

### 2.4 驗收

- 可從 CLI 參數完整組出 `SimulatorConfig`
- 內部模型足夠支撐後續 generator

---

## Step 3 — 載入設備與 schema ✅

### 3.1 目標

從目標場域拉出「要模擬的設備集合」以及每種 deviceType 的 telemetry schema。

### 3.2 建議檔案

```text
01-cli/pkg/telemetrysim/schema_loader.go
```

### 3.3 邏輯步驟

1. 若指定 `deviceCode`：只取該設備
2. 若指定 `deviceType`：拉該 type 的設備
3. 否則：拉該 tenant 全部設備，受 `deviceLimit` 限制
4. 對設備依 `deviceType` 去重
5. 逐一查詢 telemetry schema（`/v1/auth/device-templates/{deviceType}/schema/telemetry`）
6. 解析 `fields[]` 陣列為 `[]FieldSchema`

#### 解析邏輯

回傳格式為 `{"fields": [{"key":..., "type":..., "minimum":..., ...}]}`，
**不是**標準 JSON Schema `properties`：

```go
rawFields, _ := schema["fields"].([]interface{})
for _, f := range rawFields {
    m := f.(map[string]interface{})
    fs := FieldSchema{
        Name:     strVal(m, "key"),
        Title:    strVal(m, "title"),
        Type:     strVal(m, "type"),  // "number" / "text" / "date"
        Required: boolVal(m, "required"),
        Minimum:  optFloat(m, "minimum"),
        Maximum:  optFloat(m, "maximum"),
    }
    if enum, ok := m["enum"].([]interface{}); ok {
        for _, e := range enum {
            fs.Enum = append(fs.Enum, fmt.Sprintf("%v", e))
        }
    }
    fields = append(fields, fs)
}
```

### 3.4 錯誤策略

- 查不到設備：直接 fail-fast
- 某個 deviceType 無 telemetry schema：
  - 預設跳過該類設備
  - 在 summary 中記錄 skipped types

### 3.5 驗收

- 給 tenant + deviceLimit，可成功取得設備列表
- 可得到 `map[deviceType][]FieldSchema`

---

## Step 4 — 建立合法資料 generator ✅

### 4.1 目標

根據 `FieldSchema` 自動產生合法 `values`。

### 4.2 建議檔案

```text
01-cli/pkg/telemetrysim/generator.go
01-cli/pkg/telemetrysim/profiles.go
```

### 4.3 generator 介面建議

```go
func GenerateValues(fields []FieldSchema, mode string, profile string, rnd *rand.Rand) map[string]any
```

### 4.4 實作規則

#### 合法模式 `normal`

- `number`：
  - 有 `minimum` / `maximum` → 在區間內取隨機浮點數（精度 2 位）
  - 只有 `minimum` → `minimum ~ minimum*3`（至少有意義的正值）
  - 都無 → 依欄位名 heuristic（見 `profiles.go`）
- `text`：
  - 有 `enum` → 從 enum 隨機抽樣
  - 無 enum → 生成 `"SIM-{fieldName}-{rand4}"`
- `date` / `datetime`：填入**當下 UTC 時間** ISO8601 字串
- `boolean`：隨機 true/false
- `required: true` → 一定生成；`required: false` → 70% 機率生成

#### 已知欄位名 heuristic（在 `profiles.go` 內建）

| 欄位名 | 建議 normal 範圍 |
|---|---|
| `brightness` | 30 ~ 100 |
| `voltage` | 200 ~ 240 |
| `current` | 0.1 ~ 5.0 |
| `power` | 0.05 ~ 1.5 |
| `powerFactor` | 0.85 ~ 0.99 |
| `rssi` | -90 ~ -50 |
| `rsrp` | -110 ~ -80 |
| `rsrq` | -15 ~ -5 |
| `temperature` | 15 ~ 40 |
| `humidity` | 30 ~ 80 |
| `signal` | -90 ~ -50 |

#### 異常模式 `anomaly`

根據 `ruleProfile` 做偏移，已知設備欄位：

- `high-brightness` → `brightness` = 100（過亮警告）
- `low-voltage` → `voltage` < 150
- `low-power-factor` → `powerFactor` < 0.7
- `poor-signal` → `rssi` < -120、`rsrp` < -130
- `invalid-schema` → 故意缺 required 欄位、或給錯 type

### 4.5 驗收

- `normal` 模式生成的 payload 能通過 schema 驗證
- `anomaly` 模式可穩定生成特定異常值

---

## Step 5 — 實作 batch sender ✅

### 5.1 目標

把多筆 `TelemetryIngestRequest` 用 batch API 送出。

### 5.2 建議檔案

```text
01-cli/pkg/telemetrysim/sender.go
```

### 5.3 sender 介面

```go
func SendBatch(ctx context.Context, client *client.Client, batch []dto.TelemetryIngestRequest, cfg SimulatorConfig) ([]dto.TelemetryIngestResult, error)
```

### 5.4 請求格式

每筆建議長這樣：

```json
{
  "deviceCode": "SLM-001",
  "ts": "2026-07-07T16:00:00Z",
  "values": {
    "temperature": 25.4,
    "switch": "on"
  }
}
```

### 5.5 回應處理

每次 batch 送出後：

- 累加 sent / success / failed
- 記錄單筆錯誤摘要
- `verbose` 模式下列出失敗 deviceCode 與 message

### 5.6 驗收

- 能實際寫入 telemetry_data
- 錯誤時能拿到 `TelemetryIngestResult` 回應並正確統計

---

## Step 6 — 主 runner 事件循環 ✅

### 6.1 目標

把「取設備 → 生成資料 → 湊 batch → 上送 → 睡眠」串成可持續跑的主流程。

### 6.2 建議檔案

```text
01-cli/pkg/telemetrysim/runner.go
01-cli/pkg/telemetrysim/summary.go
```

### 6.3 建議流程

```text
Load config
  ↓
Init rand(seed)
  ↓
Load devices and schemas
  ↓
Start ticker(interval)
  ↓
For each tick:
  - pick N devices
  - generate values
  - build batch
  - send batch
  - update summary
  ↓
Duration reached / context cancelled
  ↓
Print final summary
```

### 6.4 批次策略

建議首版：

- 每個 tick 送一個 batch
- batch 內挑 `batchSize` 台設備
- 若設備數少於 batchSize，就全送

### 6.5 驗收

- 指定 60 秒 duration 可穩定跑完
- summary 輸出合理

---

## Step 7 — CLI command 接線 ✅

### 7.1 目標

把 simulator 接到 `iotforge` CLI。

### 7.2 建議命令形式

```bash
iotforge telemetry sim \
  --base-url http://localhost:8080 \
  --api-key xxx \
  --api-secret yyy \
  --tenant-id T_D029426BA10C \
  --device-type STREET_LIGHT \
  --interval-ms 5000 \
  --batch-size 20 \
  --duration-sec 120 \
  --mode normal \
  --seed 42
```

### 7.3 子命令建議

第一版可以只做：

- `iotforge telemetry sim run`

第二版可擴：

- `iotforge telemetry sim validate-profile`
- `iotforge telemetry sim dry-run`

### 7.4 驗收

- `--help` 可見完整參數
- 能直接由 CLI 啟動模擬

---

## Step 8 — 首版測試策略

### 8.1 單元測試

建議測：

1. `generator.go`
   - number 有 min/max
   - enum 抽樣
   - optional 欄位生成率
   - anomaly profile 是否正確偏移

2. `schema_loader.go`
   - schema 解析成功
   - 無 telemetry schema 時略過

3. `summary.go`
   - success/failure 計數正確

### 8.2 整合測試

至少一條 happy path：

- mock HTTP client / 或打本機測試環境
- 送 batch 成功
- summary 更新成功

### 8.3 手動驗證腳本

手動驗證順序：

1. 啟動 backend
2. 準備 ingest client key/secret
3. 啟動 simulator 60 秒
4. 打開 telemetry live / history 頁
5. 查 DB `telemetry_data`
6. 若 anomaly 模式，檢查 `event_rule_trigger_log`

---

## Step 9 — Phase 2 擴充點（先預留）

### 9.1 規則導向 profile

之後補：

- `high-temperature`
- `low-voltage`
- `fault-switch`
- `flapping`

### 9.2 schema-invalid profile

故意構造：

- 缺 required 欄位
- 錯誤 enum
- number 超 range
- 錯誤型別

### 9.3 dry-run / export payload

支援：

- 只印 JSON，不送 HTTP
- 將生成的 payload 落檔，供 replay 或問題重現

---

## 五、建議實作順序（一天版）

### Day 1 最小閉環

1. 補 CLI client：device list / telemetry schema / ingest batch
2. 建 `SimulatorConfig` / `FieldSchema`
3. 做 `schema_loader`
4. 做 `normal` generator
5. 做 batch sender
6. 做 runner
7. 接 CLI command
8. 手動驗證 live/history 頁面有資料

### Day 2 規則驗證版

1. 補 anomaly mode
2. 補 rule profiles
3. 補 summary / verbose output
4. 驗證 event-rule 觸發

---

## 六、交付物清單

首版完成時，應至少有：

- CLI command 可執行
- 可送合法 telemetry batch
- 可看到 live/history 資料
- summary 輸出
- README / 使用說明
- 基本單元測試

---

## 七、建議結論

最務實的開工順序是：

**先補 CLI client → 再做 schema-driven generator → 再接 batch sender → 最後接 CLI 命令。**

這樣能最快形成一條真正可跑通的 telemetry 資料鏈，並直接支撐：

- 遙測資料頁面展示
- 規則觸發驗證
- 場域 demo
- 後續壓測擴充
