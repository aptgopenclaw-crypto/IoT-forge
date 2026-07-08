# Telemetry 模擬程式 — 操作指南

> 日期：2026-07-07
> 對應實作：`7-telemetry-simulator-implementation-steps.md`（Step 1–7 已完成）
> 工具位置：`01-cli/cmd/iotforge/telemetry/sim.go`

---

## 一、前置準備

### 1.1 啟動後端

```bash
cd backend && mvn spring-boot:run
```

預設監聽 `http://localhost:8080`。

### 1.2 建立 ingest 憑證

模擬程式走 M2M HTTP ingest API（`/v1/ingest/telemetry/batch`），需要先在 DB 建立一組 API key / secret。

```sql
INSERT INTO telemetry_ingest_client
    (tenant_id, client_name, api_key, secret_hash, enabled, rate_limit_per_min, device_scope, created_at, updated_at)
VALUES
    (
      'T_7BA4E9A8DA6B',
      'Telemetry Simulator',
      'telemetry-sim-client-20260707',
      '$2y$10$GkrKcXJWAskUuvaXTPL0TOkD0qhDD2UzD6mxxLRpQz2QyitZ9fBmG',
      true,
      1200,
      null,
      now(),
      now()
    );
```

對應明文憑證：

| 欄位 | 值 |
|---|---|
| `X-API-Key` | `telemetry-sim-client-20260707` |
| `X-API-Secret` | `telemetry-sim-secret-20260707` |

### 1.3 確認設備已存在

模擬程式需要送資料到真實設備，請先確認目標場域內有 device：

```sql
SELECT device_code, device_name, device_type FROM devices
WHERE tenant_id = 'T_7BA4E9A8DA6B';
```

---

## 二、快速啟動（inline 模式）

> 不需登入，直接提供 schema JSON 與設備代碼即可啟動。

### 2.1 單設備單次模擬

```bash
cd 01-cli

go run ./cmd/iotforge telemetry sim \
  --api-key telemetry-sim-client-20260707 \
  --api-secret telemetry-sim-secret-20260707 \
  --schema-json '{"fields":[{"key":"voltage","type":"number","minimum":0,"maximum":300,"required":true},{"key":"brightness","type":"number","minimum":0,"maximum":100,"required":true},{"key":"current","type":"number","minimum":0,"required":true},{"key":"power","type":"number","minimum":0,"required":true},{"key":"powerFactor","type":"number","minimum":0,"maximum":1,"required":true},{"key":"rssi","type":"number","minimum":-150,"maximum":0,"required":false},{"key":"rsrp","type":"number","minimum":-150,"maximum":0,"required":false},{"key":"rsrq","type":"number","minimum":-50,"maximum":0,"required":false},{"key":"controllerSerial","type":"text","required":true},{"key":"reportTime","type":"date","required":true},{"key":"cell","type":"text","required":false}]}' \
  --device-codes "SLM-001" \
  --interval-ms 5000 \
  --duration-sec 30 \
  --verbose
```

### 2.2 多設備批次模擬

```bash
go run ./cmd/iotforge telemetry sim \
  --api-key telemetry-sim-client-20260707 \
  --api-secret telemetry-sim-secret-20260707 \
  --schema-json '{"fields":[{"key":"voltage","type":"number","minimum":0,"maximum":300,"required":true},{"key":"brightness","type":"number","minimum":0,"maximum":100,"required":true},{"key":"powerFactor","type":"number","minimum":0,"maximum":1,"required":true},{"key":"rssi","type":"number","minimum":-150,"maximum":0,"required":false},{"key":"controllerSerial","type":"text","required":true},{"key":"reportTime","type":"date","required":true}]}' \
  --device-codes "SLM-001,SLM-002,SLM-003" \
  --interval-ms 3000 \
  --duration-sec 60 \
  --verbose
```

### 2.3 持續執行（Ctrl-C 手動停止）

省略 `--duration-sec` 即可無限執行直到 Ctrl-C：

```bash
go run ./cmd/iotforge telemetry sim \
  --api-key telemetry-sim-client-20260707 \
  --api-secret telemetry-sim-secret-20260707 \
  --schema-json '{"fields":[...]}' \
  --device-codes "SLM-001" \
  --interval-ms 5000
```

---

## 三、異常模式

### 3.1 low-voltage（觸發低壓規則）

```bash
go run ./cmd/iotforge telemetry sim \
  --api-key telemetry-sim-client-20260707 \
  --api-secret telemetry-sim-secret-20260707 \
  --schema-json '{"fields":[{"key":"voltage","type":"number","minimum":0,"maximum":300,"required":true},{"key":"brightness","type":"number","minimum":0,"maximum":100,"required":true}]}' \
  --device-codes "SLM-001" \
  --interval-ms 3000 \
  --duration-sec 30 \
  --mode anomaly \
  --rule-profile low-voltage \
  --verbose
```

### 3.2 invalid-schema（驗證 schema 拒絕）

```bash
go run ./cmd/iotforge telemetry sim \
  --api-key telemetry-sim-client-20260707 \
  --api-secret telemetry-sim-secret-20260707 \
  --schema-json '{"fields":[{"key":"voltage","type":"number","minimum":0,"maximum":300,"required":true}]}' \
  --device-codes "SLM-001" \
  --interval-ms 3000 \
  --duration-sec 15 \
  --mode anomaly \
  --rule-profile invalid-schema \
  --verbose
```

預期送出缺少 required 欄位的 payload，後端回傳 schema validation error。

### 3.3 poor-signal（訊號極弱情境）

```bash
go run ./cmd/iotforge telemetry sim \
  --api-key telemetry-sim-client-20260707 \
  --api-secret telemetry-sim-secret-20260707 \
  --schema-json '{"fields":[{"key":"rssi","type":"number","minimum":-150,"maximum":0,"required":true},{"key":"rsrp","type":"number","minimum":-150,"maximum":0,"required":true}]}' \
  --device-codes "SLM-001" \
  --interval-ms 3000 \
  --duration-sec 15 \
  --mode anomaly \
  --rule-profile poor-signal \
  --verbose
```

---

## 四、dry-run 模式

只印 payload 不送出，適合驗證 Schema JSON 格式或調試參數：

```bash
go run ./cmd/iotforge telemetry sim \
  --api-key telemetry-sim-client-20260707 \
  --api-secret telemetry-sim-secret-20260707 \
  --schema-json '{"fields":[...]}' \
  --device-codes "SLM-001" \
  --interval-ms 5000 \
  --duration-sec 10 \
  --dry-run \
  --verbose
```

---

## 五、常用參數說明

### 5.1 必要參數

| 參數 | 說明 |
|---|---|
| `--api-key` | M2M ingest API key（對應 DB `telemetry_ingest_client.api_key`） |
| `--api-secret` | M2M ingest API secret（明文，後端以 BCrypt 比對） |
| `--schema-json` | telemetry schema JSON 字串（`{"fields":[...]}` 格式） |
| `--device-codes` | 逗號分隔的設備代碼（如 `"SLM-001,SLM-002"`） |

### 5.2 選用參數

| 參數 | 預設值 | 說明 |
|---|---|---|
| `--interval-ms` | `5000` | 每輪送資料間隔（毫秒） |
| `--duration-sec` | `0` | 總執行時間（秒），`0` 表示手動停止 |
| `--batch-size` | `20` | 每批次最多送幾台設備 |
| `--mode` | `normal` | `normal` / `anomaly` |
| `--rule-profile` | `""` | 異常 profile（`low-voltage` / `low-power-factor` / `poor-signal` / `high-brightness` / `invalid-schema`） |
| `--seed` | `0` | 亂數種子，固定後可重現相同資料 |
| `--device-type` | `""` | 配合 `--schema-json` 標記設備類型名稱 |
| `--dry-run` | `false` | 只印 payload，不送 API |
| `--verbose` | `false` | 顯示每批次成功/失敗明細 |

---

## 六、驗收檢查

模擬執行完成後，確認資料已落地：

```bash
# 查 telemetry_data 是否有寫入
psql -U postgres -d iot_forgedb -c "
SELECT device_code, ts, values
FROM telemetry_data
WHERE tenant_id = 'T_7BA4E9A8DA6B'
ORDER BY ts DESC
LIMIT 10;
"
```

前端確認路徑：

1. 登入前端 → 遙測資料 → 即時資料
2. 選擇設備 → 檢查是否有最新值
3. 遙測資料 → 歷史資料查詢 → 選時間區間 → 確認曲線呈現

---

## 七、完整的操作流程（從零開始）

```bash
# Step 1: 確保後端已啟動
cd backend && mvn spring-boot:run

# Step 2: （首次）補 ingest 憑證
psql -U postgres -d iot_forgedb -c "
INSERT INTO telemetry_ingest_client (tenant_id, client_name, api_key, secret_hash, enabled, rate_limit_per_min)
VALUES ('T_7BA4E9A8DA6B', 'Telemetry Simulator', 'telemetry-sim-client-20260707', '\$2y\$10\$GkrKcXJWAskUuvaXTPL0TOkD0qhDD2UzD6mxxLRpQz2QyitZ9fBmG', true, 1200);
"

# Step 3: 執行模擬（30 秒，每 5 秒一筆）
cd 01-cli && go run ./cmd/iotforge telemetry sim \
  --api-key telemetry-sim-client-20260707 \
  --api-secret telemetry-sim-secret-20260707 \
  --schema-json '{"fields":[{"key":"voltage","type":"number","minimum":0,"maximum":300,"required":true},{"key":"brightness","type":"number","minimum":0,"maximum":100,"required":true},{"key":"current","type":"number","minimum":0,"required":true},{"key":"power","type":"number","minimum":0,"required":true},{"key":"powerFactor","type":"number","minimum":0,"maximum":1,"required":true},{"key":"rssi","type":"number","minimum":-150,"maximum":0,"required":false},{"key":"controllerSerial","type":"text","required":true},{"key":"reportTime","type":"date","required":true}]}' \
  --device-codes "SLM-001" \
  --interval-ms 5000 \
  --duration-sec 30 \
  --verbose

# Step 4: 確認資料落地
psql -U postgres -d iot_forgedb -c "
SELECT count(*) as total_records
FROM telemetry_data
WHERE tenant_id = 'T_7BA4E9A8DA6B';
"

# Step 5: 打開前端確認即時資料 / 歷史資料頁面
```

---

## 八、常見問題

### Q1: 回傳 `[60001] Device not found: xxx`

設備代碼不存在系統中。請確認：

```sql
SELECT device_code FROM devices WHERE tenant_id = 'T_7BA4E9A8DA6B';
```

### Q2: 回傳 `[10001] access token 無效`

使用 JWT 模式時 token 已過期。請重新登入：

```bash
iotforge login --endpoint http://localhost:8080
```

或改用 inline 模式（`--schema-json` + `--device-codes`，不需 JWT）。

### Q3: 回傳 `[88070] api key not found` 或 `[88071] invalid secret`

- API key / secret 與 DB 不一致
- secret 需用明文，後端以 BCrypt 比對
- 確保密碼未包含特殊字元被 shell 轉義

### Q4: 回傳 `[88072] client is disabled`

`telemetry_ingest_client.enabled = false`，設為 true：

```sql
UPDATE telemetry_ingest_client SET enabled = true WHERE api_key = 'telemetry-sim-client-20260707';
```

### Q5: 如何產生新的 BCrypt hash？

```bash
# 安裝 htpasswd（Debian/Ubuntu）
sudo apt install apache2-utils

# 產生 BCrypt hash（cost=10）
htpasswd -bnBC 10 '' 'my-new-secret' | tr -d ':\n' | sed 's/^\$2y/$2a/'
```
