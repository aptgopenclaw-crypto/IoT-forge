# Telemetry 模擬程式實作計畫

> 日期：2026-07-07
> 目的：建立一套可在開發 / 測試環境使用的 telemetry 模擬程式，隨機產生遙測資料並送入既有 ingest 流程。
> 限制：本文僅規劃，不實作程式碼。
> 依據：
> - `2-telemetry-design.md`：payload 與 schema 驗證格式
> - `5-implementation-steps.md`：telemetry / ingest / event-rule 模組已完成範圍
> - 現有 HTTP ingest 入口：`POST /v1/ingest/telemetry`、`POST /v1/ingest/telemetry/batch`

---

## 一、目標

建立一個「可控制、可重現、可觀察」的 telemetry 模擬工具，用於：

1. 驗證 telemetry ingest 流程是否正常
2. 驗證 schema 驗證是否正確攔截非法資料
3. 驗證 telemetry live / history 前端畫面是否有資料可看
4. 驗證 event-rule 是否會在特定條件下被觸發
5. 支援不同場域、不同 deviceType、不同裝置數量的壓測或展示情境

---

## 二、接入策略

### 2.1 採用既有 HTTP ingest API

**首版模擬程式建議直接走 HTTP ingest，而非 MQTT。**

理由：

- HTTP ingest 已有穩定入口：`/v1/ingest/telemetry`、`/v1/ingest/telemetry/batch`
- 可直接覆蓋 `X-API-Key` / `X-API-Secret` 的 M2M 認證路徑
- 不依賴 broker，開發機與 CI 更容易啟動
- 便於控制請求節奏、批次大小、錯誤注入
- 可直接驗證 canonical ingest path，而不需額外排查 MQTT topic / broker 設定

### 2.2 未來可擴充雙模式

首版先做：

- `HTTP mode`：主模式，實際送入系統

後續可再做：

- `MQTT mode`：模擬設備 topic 上送
- `dry-run mode`：只生成 payload、不送出
- `replay mode`：重放預先錄製的樣本序列

---

## 三、建議放置位置

### 3.1 建議獨立於 backend / frontend

建議新增一個獨立工具目錄，例如：

```text
telemetry-simulator/
```

或置於現有 CLI 體系下，例如：

```text
01-cli/cmd/iotforge telemetry-sim ...
```

### 3.2 推薦優先順序

**優先建議：放在 `01-cli/` 下，做成 CLI 子命令。**

理由：

- 專案已有 CLI 結構與 Go module
- 模擬程式本質上屬於工具，不應放進 backend runtime
- CLI 比單獨 script 更利於參數化與後續維護
- 後續可直接支援：`seed`、`run`、`stop`、`profile validate` 等子命令

---

## 四、模擬資料來源策略

### 4.1 Device 來源

模擬程式不應自行幻想 deviceType / telemetry 欄位，應盡量從系統真實資料衍生。

建議流程：

1. 查詢目標 tenant 的設備清單
2. 依 `deviceType` 分組
3. 查詢每個 `deviceType` 的 telemetry schema
4. 依 schema 生成對應欄位資料
5. 以 `deviceCode` 作為上送主鍵

### 4.2 Telemetry 欄位生成原則

依欄位型別自動生成：

| schema type | 生成策略 |
|---|---|
| `number` | 在 `minimum ~ maximum` 間隨機；若無上下界，採預設範圍 |
| `integer` | 與 `number` 類似，但取整數 |
| `string + enum` | 從 enum 隨機抽樣 |
| `boolean` | true / false 隨機 |
| `required` 欄位 | 必定生成 |
| 非 required 欄位 | 可配置為固定生成或機率生成 |

### 4.3 建議內建欄位 profile

若 schema 不完整或缺少 minimum / maximum，可按常見欄位名稱提供預設 profile：

| 欄位名 | 預設範圍 |
|---|---|
| `temperature` | -10 ~ 60 |
| `humidity` | 0 ~ 100 |
| `voltage` | 180 ~ 260 |
| `power` | 0 ~ 1000 |
| `current` | 0 ~ 100 |
| `switch` | `on/off/fault` |
| `signal` | -120 ~ -40 |

---

## 五、模擬模式設計

### 5.1 正常模式

持續生成符合 schema 的資料，模擬設備正常上送。

用途：

- Live view 驗證
- History view 驗證
- 前端 demo

### 5.2 異常模式

故意產生會觸發 rule 或 schema 驗證失敗的資料。

分兩類：

1. **規則觸發型**
   - 例如 `temperature > 80`
   - 例如 `voltage < 180`
   - 例如 `switch = fault`

2. **資料不合法型**
   - 少 required 欄位
   - enum 值不合法
   - number 超範圍
   - type 錯誤（字串代替數字）

### 5.3 批次模式

一次送多筆資料到 `/v1/ingest/telemetry/batch`。

用途：

- 驗證批次部分失敗
- 驗證高吞吐場景
- 驗證多設備同時上送

### 5.4 回放模式

讀取預先定義的 JSON/CSV 時序腳本，依時間或加速比例送出。

用途：

- 重現特定問題
- 驗證複雜規則
- 展示固定案例

---

## 六、執行參數設計

模擬程式建議支援以下參數：

| 參數 | 說明 |
|---|---|
| `--base-url` | ingest API base URL |
| `--api-key` | `X-API-Key` |
| `--api-secret` | `X-API-Secret` |
| `--tenant-id` | 目標 tenant |
| `--device-type` | 只模擬指定 deviceType |
| `--device-code` | 只模擬指定設備 |
| `--device-limit` | 最多取幾台設備 |
| `--interval-ms` | 上送間隔 |
| `--batch-size` | 批次上送筆數 |
| `--duration-sec` | 總執行時間 |
| `--mode` | normal / anomaly / batch / replay |
| `--rule-profile` | 指定觸發哪一類規則 |
| `--seed` | 固定亂數種子，便於重現 |
| `--dry-run` | 只印 payload，不送出 |
| `--verbose` | 顯示請求/回應摘要 |

---

## 七、資料流設計

### 7.1 啟動流程

```text
載入設定
  ↓
認證資訊檢查
  ↓
查詢目標設備 / schema
  ↓
建立模擬任務清單
  ↓
進入事件循環
  ↓
定期生成 telemetry payload
  ↓
呼叫 HTTP ingest API
  ↓
紀錄成功/失敗/延遲
```

### 7.2 單筆 payload 格式

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

### 7.3 批次 payload 格式

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

---

## 八、觀察與輸出

模擬程式至少輸出以下統計：

| 指標 | 說明 |
|---|---|
| `sent` | 已送出筆數 |
| `success` | ingest 成功筆數 |
| `failed` | ingest 失敗筆數 |
| `validationFailed` | schema 驗證失敗數 |
| `ruleTriggeredEstimate` | 預期規則觸發次數（本地估算） |
| `avgLatencyMs` | 平均 API 延遲 |
| `lastError` | 最近一次錯誤摘要 |

建議輸出模式：

1. 即時 console summary
2. 可選 JSON log
3. 結束時印 summary table

---

## 九、與 event-rule 的配合方式

為了驗證資料驗證規則 / 事件規則，模擬程式應支援「規則導向 profile」。

### 9.1 規則導向 profile 範例

| profile | 目標 |
|---|---|
| `high-temperature` | 連續送出高溫數據，觸發過溫規則 |
| `low-voltage` | 送出低電壓數據，觸發低壓規則 |
| `fault-switch` | 送出 `switch=fault` 狀態 |
| `flapping` | 在臨界值上下來回，驗證 cooldown / duration |

### 9.2 驗證重點

- ON_MATCH 是否立即觸發
- FOR_DURATION 是否需連續滿足條件
- cooldown 是否生效
- trigger_log 是否寫入
- notification 是否被送出

---

## 十、推薦分期實作

### Phase 1 — 最小可用版

目標：先讓系統有穩定遙測資料可看。

範圍：

- HTTP 單筆 / 批次送入
- 依設備清單與 schema 自動生成合法資料
- `normal` 模式
- console summary

### Phase 2 — 規則驗證版

範圍：

- anomaly / rule-profile
- 可控的超標資料
- 支援固定亂數種子
- 可輸出 JSON log

### Phase 3 — 展示 / 壓測版

範圍：

- replay mode
- 多 goroutine / worker 並發
- 多 deviceType profile
- 指標匯總

---

## 十一、驗收標準

### 11.1 Phase 1 驗收

- 能成功對 `/v1/ingest/telemetry`、`/v1/ingest/telemetry/batch` 送資料
- `telemetry_data` 可看到資料落庫
- Telemetry Live / History 前端頁面可看到即時與歷史資料
- schema 驗證失敗能在模擬程式側看到錯誤摘要

### 11.2 Phase 2 驗收

- 能穩定觸發至少 1 個 event-rule
- `event_rule_trigger_log` 可查到資料
- notification / 後續事件鏈可觀察到觸發

### 11.3 Phase 3 驗收

- 能持續 10 分鐘以上穩定上送
- 支援 100+ device 模擬
- 可控制速率與批次大小

---

## 十二、風險與注意事項

### 12.1 不應繞過 ingest 核心

模擬程式必須走 ingest API，不應直接寫 DB，否則無法驗證：

- device lookup
- schema validation
- event publish
- event-rule listener
- tenant/client 驗證

### 12.2 schema 可能不完整

若某些 deviceType 的 telemetry schema 不完整，模擬器需有 fallback：

- 欄位名稱 heuristic
- 預設 number/string/enum profile
- 或跳過無法解析的 deviceType

### 12.3 tenant 專屬資料

模擬程式應明確綁定 tenant，避免把 A 場域資料打到 B 場域。

### 12.4 亂數資料要可重現

需支援 `seed`，否則規則問題難以重現。

---

## 十三、建議後續產出

後續真正實作時，建議補以下文件 / 產物：

1. `telemetry-simulator/README.md`
2. CLI usage 說明（參數列表）
3. 2~3 組內建 rule profiles
4. 1 份 demo dataset / replay file
5. 與 `event-rule` 聯調的驗證手冊

---

## 十四、建議結論

### 最佳首版方案

**以 CLI 工具 + HTTP ingest mode + schema-driven random generation** 作為第一版。

這個方案：

- 最符合現有架構
- 落地成本最低
- 最能覆蓋 telemetry / event-rule / frontend 的整條鏈路
- 不需額外依賴 MQTT broker

### 首版範圍建議

先做：

- 指定 tenant
- 拉設備與 schema
- 隨機生成合法 telemetry
- 送 `/v1/ingest/telemetry/batch`
- 提供 `normal` / `anomaly` 兩種模式

等第一版跑順，再補：

- replay
- 並發 worker
- MQTT mode
- 壓測模式
