# 📦 Telemetry 模擬器 CLI 設計文件

## 一、模組定位與核心目標

### 1.1 模組定位
Telemetry 模擬器是一個**獨立的 CLI 工具**，用於在開發/測試環境中產生可控、可重現、可觀察的遙測資料，並透過既有的 HTTP ingest API 送入系統。

**它不是系統的一部分，而是系統的「測試與展示工具」。**

### 1.2 核心目標
| 目標 | 說明 |
| :--- | :--- |
| **驗證 ingest 流程** | 確認 HTTP ingest API 與 M2M 認證鏈路正常運作 |
| **驗證 schema 驗證** | 確認非法資料能被 `TelemetryValidationService` 正確攔截 |
| **驅動前端展示** | 讓 Telemetry Live / History 頁面有真實資料可看 |
| **觸發事件規則** | 驗證 `event-rule` 模組能在特定條件下被觸發 |
| **支援場域 Demo** | 模擬不同 deviceType、不同裝置數量的展示情境 |

### 1.3 設計原則
1. **不繞過 ingest 核心**：必須走 `/v1/ingest/telemetry/batch`，不能直接寫 DB，否則無法驗證完整鏈路。
2. **Schema-Driven**：不自行幻想欄位，從系統真實的 `device_templates.schema.telemetry` 衍生。
3. **可重現**：支援 `--seed` 固定亂數種子，讓問題可重現。
4. **最小依賴**：不依賴 MQTT broker，開發機與 CI 更容易啟動。

---

## 二、技術選型與接入策略

### 2.1 為什麼用 Go + CLI？
| 決策 | 理由 |
| :--- | :--- |
| **語言：Go** | 專案已有 `01-cli/` 體系與 Go module，可複用既有 `pkg/client` 與認證邏輯 |
| **形式：CLI 子命令** | 比獨立 script 更利於參數化、版本控制與後續維護 |
| **位置：`01-cli/cmd/iotforge telemetry sim`** | 與既有命令體系整合，使用者無需額外安裝工具 |

### 2.2 為什麼走 HTTP ingest 而非 MQTT？
| 考量 | HTTP ingest 優勢 |
| :--- | :--- |
| **穩定性** | 已有 `/v1/ingest/telemetry` 與 `/v1/ingest/telemetry/batch` 穩定入口 |
| **認證簡單** | 直接覆蓋 `X-API-Key` / `X-API-Secret`，不需處理 MQTT broker 連線 |
| **可控性** | 易於控制請求節奏、批次大小、錯誤注入 |
| **可驗證性** | 直接驗證 canonical ingest path，不需排查 MQTT topic / broker 設定 |

> 💡 **未來擴充**：首版只做 HTTP mode，後續可擴充 MQTT mode、replay mode。

---

## 三、目錄結構與檔案規劃

```
01-cli/
├── cmd/iotforge/
│   └── telemetry/
│       └── sim.go                    # Cobra 子命令註冊
└── pkg/
    ├── client/
    │   ├── devices.go                # 既有：設備清單 API
    │   └── telemetry.go              # 新增：telemetry schema + ingest batch
    ├── dto/
    │   └── telemetry.go              # 新增：請求/回應 DTO
    └── telemetrysim/                 # 新增：模擬器核心
        ├── config.go                 # 參數與預設值
        ├── types.go                  # 內部模型（SimDevice, FieldSchema）
        ├── schema_loader.go          # 查詢設備與 schema
        ├── generator.go              # 隨機值生成
        ├── profiles.go               # 預設欄位 profile / anomaly profile
        ├── sender.go                 # HTTP ingest 封裝
        ├── summary.go                # 執行統計
        └── runner.go                 # 主執行流程（事件循環）
```

---

## 四、核心資料模型

### 4.1 `SimulatorConfig` — 模擬器設定
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
    Mode          string        // "normal" | "anomaly"
    RuleProfile   string        // "low-voltage" | "poor-signal" | "invalid-schema" ...
    Seed          int64
    DryRun        bool
    Verbose       bool
}
```

### 4.2 `SimDevice` — 模擬設備
```go
type SimDevice struct {
    DeviceID    int64
    DeviceCode  string
    DeviceType  string
    TenantID    string
}
```

### 4.3 `FieldSchema` — 欄位定義（關鍵！）
> ⚠️ **重要**：系統回傳的 telemetry schema **不是標準 JSON Schema**，而是前端編輯器儲存的 `fields[]` 格式。

```go
type FieldSchema struct {
    Name     string    // field.key
    Title    string    // field.title（顯示用）
    Type     string    // "number" | "text" | "date" | "boolean"
    Required bool      // field.required
    Enum     []string  // field.enum（若有）
    Minimum  *float64  // field.minimum
    Maximum  *float64  // field.maximum
}
```

**Schema 解析範例：**
```json
{
  "fields": [
    { "key": "voltage", "type": "number", "minimum": 0, "maximum": 300, "required": true },
    { "key": "brightness", "type": "number", "minimum": 0, "maximum": 100, "required": true },
    { "key": "controllerSerial", "type": "text", "required": true },
    { "key": "reportTime", "type": "date", "required": true }
  ]
}
```

---

## 五、核心元件設計

### 5.1 `schema_loader.go` — 載入設備與 Schema
**職責**：從系統拉出要模擬的設備集合，並查詢每種 deviceType 的 telemetry schema。

**流程**：
1. 若指定 `deviceCode` → 只取該設備
2. 若指定 `deviceType` → 拉該 type 的設備
3. 否則 → 拉該 tenant 全部設備，受 `deviceLimit` 限制
4. 對設備依 `deviceType` 去重
5. 逐一查詢 `/v1/auth/device-templates/{deviceType}/schema/telemetry`
6. 解析 `fields[]` 陣列為 `[]FieldSchema`

**錯誤策略**：
- 查不到設備 → **fail-fast**
- 某個 deviceType 無 telemetry schema → 跳過該類設備，在 summary 中記錄

### 5.2 `generator.go` — 合法資料生成器
**職責**：根據 `FieldSchema` 自動產生合法（或異常）的 `values`。

**介面**：
```go
func GenerateValues(fields []FieldSchema, mode string, profile string, rnd *rand.Rand) map[string]any
```

#### Normal 模式生成規則
| Schema Type | 生成策略 |
| :--- | :--- |
| `number` | 有 `min/max` → 區間內隨機浮點數（精度 2 位）；只有 `min` → `min ~ min*3`；都無 → 依欄位名 heuristic |
| `text` | 有 `enum` → 隨機抽樣；無 enum → `"SIM-{fieldName}-{rand4}"` |
| `date` / `datetime` | 當下 UTC 時間 ISO8601 字串 |
| `boolean` | 隨機 `true` / `false` |
| `required: true` | 必定生成 |
| `required: false` | 70% 機率生成 |

#### 已知欄位名 Heuristic（內建於 `profiles.go`）
| 欄位名 | 建議 Normal 範圍 |
| :--- | :--- |
| `brightness` | 30 ~ 100 |
| `voltage` | 200 ~ 240 |
| `current` | 0.1 ~ 5.0 |
| `power` | 0.05 ~ 1.5 |
| `powerFactor` | 0.85 ~ 0.99 |
| `rssi` | -90 ~ -50 |
| `rsrp` | -110 ~ -80 |
| `temperature` | 15 ~ 40 |
| `humidity` | 30 ~ 80 |

#### Anomaly 模式（Rule Profile）
| Profile | 行為 |
| :--- | :--- |
| `high-brightness` | `brightness` = 100（過亮警告） |
| `low-voltage` | `voltage` < 150 |
| `low-power-factor` | `powerFactor` < 0.7 |
| `poor-signal` | `rssi` < -120、`rsrp` < -130 |
| `invalid-schema` | 故意缺 required 欄位、或給錯 type |

### 5.3 `sender.go` — 批次發送器
**職責**：將多筆 `TelemetryIngestRequest` 用 batch API 送出。

**介面**：
```go
func SendBatch(ctx context.Context, client *client.Client, batch []dto.TelemetryIngestRequest, cfg SimulatorConfig) ([]dto.TelemetryIngestResult, error)
```

**請求格式**：
```json
{
  "deviceCode": "SLM-001",
  "ts": "2026-07-07T16:00:00Z",
  "values": { "temperature": 25.4, "switch": "on" }
}
```

**回應處理**：
- 累加 `sent` / `success` / `failed`
- 記錄單筆錯誤摘要
- `verbose` 模式下列出失敗 deviceCode 與 message

### 5.4 `runner.go` — 主事件循環
**職責**：把「取設備 → 生成資料 → 湊 batch → 上送 → 睡眠」串成可持續跑的主流程。

**流程**：
```
Load config
   ↓
Init rand(seed)
   ↓
Load devices and schemas
   ↓
Start ticker(interval)
   ↓
For each tick:
  - pick N devices (受 batchSize 限制)
  - generate values
  - build batch
  - send batch (或 dry-run 只印 JSON)
  - update summary
   ↓
Duration reached / context cancelled
   ↓
Print final summary
```

**批次策略**：每個 tick 送一個 batch，batch 內挑 `batchSize` 台設備；若設備數少於 batchSize，就全送。

### 5.5 `summary.go` — 執行統計
**輸出指標**：
| 指標 | 說明 |
| :--- | :--- |
| `sent` | 已送出筆數 |
| `success` | ingest 成功筆數 |
| `failed` | ingest 失敗筆數 |
| `validationFailed` | schema 驗證失敗數 |
| `avgLatencyMs` | 平均 API 延遲 |
| `lastError` | 最近一次錯誤摘要 |

---

## 六、CLI 命令設計

### 6.1 命令形式
```bash
iotforge telemetry sim [flags]
```

### 6.2 必要參數
| 參數 | 說明 |
| :--- | :--- |
| `--api-key` | M2M ingest API key（對應 DB `telemetry_ingest_client.api_key`） |
| `--api-secret` | M2M ingest API secret（明文，後端以 BCrypt 比對） |
| `--schema-json` | telemetry schema JSON 字串（`{"fields":[...]}` 格式） |
| `--device-codes` | 逗號分隔的設備代碼（如 `"SLM-001,SLM-002"`） |

### 6.3 選用參數
| 參數 | 預設值 | 說明 |
| :--- | :--- | :--- |
| `--base-url` | `http://localhost:8080` | ingest API base URL |
| `--tenant-id` | - | 目標 tenant |
| `--interval-ms` | `5000` | 每輪送資料間隔（毫秒） |
| `--duration-sec` | `0` | 總執行時間（秒），`0` 表示手動 Ctrl-C 停止 |
| `--batch-size` | `20` | 每批次最多送幾台設備 |
| `--mode` | `normal` | `normal` / `anomaly` |
| `--rule-profile` | `""` | 異常 profile（`low-voltage` / `poor-signal` / `invalid-schema` ...） |
| `--seed` | `0` | 亂數種子，固定後可重現相同資料 |
| `--device-type` | `""` | 配合 `--schema-json` 標記設備類型名稱 |
| `--dry-run` | `false` | 只印 payload，不送 API |
| `--verbose` | `false` | 顯示每批次成功/失敗明細 |

---

## 七、資料流與執行流程

### 7.1 單筆 Payload 格式
```json
{
  "deviceCode": "SL-001",
  "ts": "2026-07-07T15:30:00Z",
  "values": {
    "temperature": 27.6,
    "humidity": 61.2,
    "switch": "on"
  }
}
```

### 7.2 批次 Payload 格式
```json
[
  {
    "deviceCode": "SL-001",
    "ts": "2026-07-07T15:30:00Z",
    "values": { "temperature": 27.6, "switch": "on" }
  },
  {
    "deviceCode": "SL-002",
    "ts": "2026-07-07T15:30:01Z",
    "values": { "temperature": 28.1, "switch": "on" }
  }
]
```

### 7.3 完整啟動流程
```
1. 解析 CLI 參數 → 組出 SimulatorConfig
2. 初始化 rand(seed)
3. 查詢目標設備清單（/v1/auth/devices）
4. 查詢每種 deviceType 的 telemetry schema
5. 解析 fields[] 為 []FieldSchema
6. 進入 ticker 事件循環
7. 每個 tick：
   a. 挑選 batchSize 台設備
   b. 依 mode/profile 生成 values
   c. 組出 TelemetryIngestRequest batch
   d. 呼叫 POST /v1/ingest/telemetry/batch
   e. 解析 TelemetryIngestResult，更新 summary
8. 達到 duration 或收到 SIGINT → 印出 final summary
```

---

## 八、操作範例

### 8.1 單設備單次模擬（Normal 模式）
```bash
go run ./cmd/iotforge telemetry sim \
  --api-key telemetry-sim-client-20260707 \
  --api-secret telemetry-sim-secret-20260707 \
  --schema-json '{"fields":[{"key":"voltage","type":"number","minimum":0,"maximum":300,"required":true},{"key":"brightness","type":"number","minimum":0,"maximum":100,"required":true}]}' \
  --device-codes "SLM-001" \
  --interval-ms 5000 \
  --duration-sec 30 \
  --verbose
```

### 8.2 多設備批次模擬
```bash
go run ./cmd/iotforge telemetry sim \
  --api-key telemetry-sim-client-20260707 \
  --api-secret telemetry-sim-secret-20260707 \
  --schema-json '{"fields":[...]}' \
  --device-codes "SLM-001,SLM-002,SLM-003" \
  --interval-ms 3000 \
  --duration-sec 60 \
  --verbose
```

### 8.3 異常模式（觸發低壓規則）
```bash
go run ./cmd/iotforge telemetry sim \
  --api-key telemetry-sim-client-20260707 \
  --api-secret telemetry-sim-secret-20260707 \
  --schema-json '{"fields":[{"key":"voltage","type":"number","minimum":0,"maximum":300,"required":true}]}' \
  --device-codes "SLM-001" \
  --interval-ms 3000 \
  --duration-sec 30 \
  --mode anomaly \
  --rule-profile low-voltage \
  --verbose
```

### 8.4 Dry-Run 模式（只印 payload 不送出）
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

## 九、前置準備（DB 資料）

模擬器走 M2M HTTP ingest API，需要先在 DB 建立一組 API key / secret：

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

| 欄位 | 值 |
| :--- | :--- |
| `X-API-Key` | `telemetry-sim-client-20260707` |
| `X-API-Secret` | `telemetry-sim-secret-20260707` |

> 💡 產生新的 BCrypt hash：`htpasswd -bnBC 10 '' 'my-new-secret' | tr -d ':\n' | sed 's/^\$2y/$2a/'`

---

## 十、實作分期與驗收標準

### Phase 1 — 最小可用版（Day 1）
| 項目 | 內容 |
| :--- | :--- |
| **範圍** | HTTP 單筆/批次送入、依 schema 自動生成合法資料、`normal` 模式、console summary |
| **驗收** | 能成功對 `/v1/ingest/telemetry/batch` 送資料；`telemetry_data` 可看到資料落庫；前端 Live/History 頁面有資料 |

### Phase 2 — 規則驗證版（Day 2）
| 項目 | 內容 |
| :--- | :--- |
| **範圍** | `anomaly` / `rule-profile`、可控的超標資料、固定亂數種子、JSON log 輸出 |
| **驗收** | 能穩定觸發至少 1 個 event-rule；`event_rule_trigger_log` 可查到資料 |

### Phase 3 — 展示/壓測版（後續）
| 項目 | 內容 |
| :--- | :--- |
| **範圍** | `replay` mode、多 goroutine worker 並發、多 deviceType profile、Prometheus 指標輸出 |
| **驗收** | 能持續 10 分鐘以上穩定上送；支援 100+ device 模擬 |

---

## 十一、風險與注意事項

| 風險 | 應對策略 |
| :--- | :--- |
| **Schema 可能不完整** | 若某些 deviceType 無 telemetry schema，模擬器需有 fallback：欄位名稱 heuristic、預設 profile、或跳過該 type |
| **Tenant 專屬資料** | 模擬器應明確綁定 tenant，避免把 A 場域資料打到 B 場域 |
| **亂數資料要可重現** | 必須支援 `--seed`，否則規則問題難以重現 |
| **不應繞過 ingest 核心** | 必須走 ingest API，不能直接寫 DB，否則無法驗證 device lookup / schema validation / event publish 完整鏈路 |
| **Shell 特殊字元** | API secret 若包含 `$`、`!` 等字元，需注意 shell 轉義問題 |

---

## 十二、建議實作順序（一天版）

```
1. 補 CLI client：device list / telemetry schema / ingest batch
2. 建 SimulatorConfig / FieldSchema 資料模型
3. 做 schema_loader（解析 fields[] 格式）
4. 做 normal generator（含 heuristic profile）
5. 做 batch sender（含錯誤統計）
6. 做 runner（ticker 事件循環）
7. 接 CLI command（Cobra 子命令）
8. 手動驗證：啟動 backend → 執行模擬 → 查 telemetry_data → 看前端頁面
```

> 💡 **最務實的開工順序**：先補 CLI client → 再做 schema-driven generator → 再接 batch sender → 最後接 CLI 命令。這樣能最快形成一條真正可跑通的 telemetry 資料鏈。

---

## 十三、交付物清單

首版完成時，應至少有：
- [ ] CLI command 可執行（`iotforge telemetry sim`）
- [ ] 可送合法 telemetry batch 到 `/v1/ingest/telemetry/batch`
- [ ] 可看到 Live/History 前端頁面有資料
- [ ] Console summary 輸出（sent / success / failed / avgLatency）
- [ ] README / 使用說明（含操作範例）
- [ ] 基本單元測試（generator / schema_loader / summary）
- [ ] 至少 1 條 happy path 整合測試