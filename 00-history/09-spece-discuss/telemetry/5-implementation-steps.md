# Telemetry 實作步驟（工程落地清單）

> 日期：2026-06-30
> 延續：`4-module-architecture.md`（模組切分定案）、`2-telemetry-design.md`（JSON/Schema）、`3.md`（多來源接入）
> 本文把「落地順序」展開成可逐項勾選的工程任務，含檔案、類別、migration、測試與驗收標準。
> 規範：後端用 `mvn`（非 `./mvnw`）；改 Java 後跑 `mvn spring-javaformat:apply -q`（TAB 縮排）；新 migration 從 `V3+` 起、保持 schema-agnostic。

---

## 進度總覽

| Step | 模組 | 狀態 | 產出 |
|---|---|---|---|
| 1 | 契約層（common） | ✅ 已完成（commit `78b3bab`） | 2 events + 2 ports + DeviceRef |
| 2 | schema 改造 | ⬜ 未開始 | template 拆 attributes/telemetry + `SchemaProviderPort` adapter |
| 3 | telemetry 核心 | ⬜ 未開始 | storage + validation + `TelemetryIngestionService` |
| 4 | ingest 模組 | ⬜ 未開始 | mqtt adapter → http adapter + client 憑證/映射 |
| 5 | telemetry query/api | ⬜ 未開始 | 歷史 / 即時 / 統計 REST |
| 6 | event-rule | ⬜ 未開始 | 監聽事件、規則比對、發 `RuleTriggeredEvent`（見 `event-rule/h2-module-design.md`） |
| 7 | 前端 | ⬜ 未開始 | live / history 視圖 |

---

## Step 1 — 契約層（common）✅ 已完成

定義於 `com.taipei.iot.common`，釘死跨域邊界，後續所有模組只 import `common`，彼此無編譯期相依。

- [x] `common/event/TelemetryReceivedEvent`（telemetry 發 → event-rule 收）
- [x] `common/event/RuleTriggeredEvent` + 巢狀 `RecipientSpec`（event-rule 發 → notification 收）
- [x] `common/schema/port/SchemaProviderPort`（schema 模組實作，提供 telemetry JSON schema）
- [x] `common/device/port/DeviceLookupPort` + `DeviceRef`（device 模組實作，deviceCode 解析）

**驗收**：`mvn clean verify` 全綠（已驗證；1293 單元 + 3 整合 + 架構測試）。

---

## Step 2 — schema 改造（device_templates 拆 attributes / telemetry）

**目標**：把既有 `device_templates.schema` 的單一 JSON 拆成 `attributes`（靜態屬性）與 `telemetry`（時序量測）兩段，並提供 `SchemaProviderPort` 的 adapter，讓 telemetry 核心能取得「該 deviceType 的 telemetry JSON Schema」。

### 2.1 資料遷移
- [ ] `V3__split_device_template_schema.sql`
  - 新增欄位 `attributes_schema JSONB`、`telemetry_schema JSONB`（或保留 `schema` 並新增 `telemetry_schema`，視既有結構而定 — 先確認現況再定）。
  - 回填：把既有 `schema` 內容依約定鍵（如 `attributes` / `telemetry`）拆入新欄位；無法判定者整段放 `attributes_schema` 並記 TODO。
  - 保持 schema-agnostic（無 schema 前綴、無 search_path）。

### 2.2 程式調整
- [ ] `schema` 模組 entity 對應新欄位；CRUD / DTO 同步（提供 attributes 與 telemetry 兩段編輯）。
- [ ] 新增 adapter：`schema/.../SchemaProviderAdapter implements SchemaProviderPort`
  - `getTelemetrySchema(deviceType)` → `Optional<JsonNode>`，回傳該 type 的 `telemetry_schema`。
  - 加上快取（可用既有 cache 機制；schema 變動低頻）。

### 2.3 測試
- [ ] migration 套用後資料正確拆分（整合測試 or 手動 psql 驗證）。
- [ ] `SchemaProviderAdapter` 單元測試：存在/不存在 deviceType 的回傳。

**驗收**：`SchemaProviderPort.getTelemetrySchema("<type>")` 能取得正確 telemetry schema；既有 device template 功能不回歸。

---

## Step 3 — telemetry 核心（storage + validation + ingestion）

> 純後端核心，**不接任何來源**，全部以單元測試驗證。Package：`com.taipei.iot.telemetry`。

### 3.1 storage（原生 PG 月分區，定案見 `2-telemetry-design.md` 第五節）
- [ ] `V4__create_telemetry_data.sql`
  - `telemetry_data` `PARTITION BY RANGE (ts)`；複合 PK `(ts, id)`；`id BIGINT GENERATED ALWAYS AS IDENTITY`。
  - 欄位：`tenant_id`、`device_id`、`device_type`、`ts`、`values JSONB`、`source VARCHAR(20) DEFAULT 'MQTT'`、`source_client_id VARCHAR(50)`、`received_at`。
  - index：`(device_id, ts)`、`(tenant_id, ts)`、`(device_type, ts)` + `BRIN (ts)`。
  - 先建近月分區（含 default 分區避免漏寫）。
- [ ] `storage/TelemetryData`（entity）、`TelemetryDataRepository`。
- [ ] storage 抽象層：`TelemetryStore` 介面 + `JdbcTelemetryStore`（或 JPA）實作，封裝寫入/查詢 — 未來可換 hypertable。
- [ ] 分區維護 `@Scheduled`：預建下月分區 + DROP/DETACH 超過保留期（~90d）的舊分區。

### 3.2 validation
- [ ] pom 加 `com.networknt:json-schema-validator:1.5.6`。
- [ ] `validation/TelemetryValidationService`：以 `SchemaProviderPort` 取 schema，對 `values` 做 JSON Schema 驗證，回傳結構化錯誤。

### 3.3 ingestion
- [ ] `ingest/TelemetryIngestRequest`（canonical 模型：deviceCode, ts, values, tenantId, source, sourceClientId, rawPayload）。
- [ ] `ingest/TelemetrySource` enum（MQTT / HTTP_API / BATCH_IMPORT / KAFKA）。
- [ ] `ingest/TelemetryIngestionService`（介面）+ `...ServiceImpl`：
  1. `DeviceLookupPort.resolve(deviceCode, tenantId)` → `DeviceRef`（找不到→拒絕）。
  2. `SchemaProviderPort.getTelemetrySchema(deviceType)` → 驗證。
  3. 通過 → `TelemetryStore` 寫入（標記 source）。
  4. `ApplicationEventPublisher` 發 `TelemetryReceivedEvent`。
  - 提供 `ingestBatch(List<>)`（API 常一次多筆）。

### 3.4 測試
- [ ] ingestion 單元測試（mock 兩個 port + store + publisher）：成功、device 不存在、schema 驗證失敗、批次部分失敗。
- [ ] storage 寫入/查詢測試（可用整合測試打真 PG 分區）。

**驗收**：給定合法 `TelemetryIngestRequest` → 寫入 `telemetry_data` 並發出事件；非法輸入被正確拒絕；核心不認得任何協定。

---

## Step 4 — ingest 模組（外圈 adapter）

> Package：`com.taipei.iot.ingest`。把多來源收斂成 canonical，呼叫 `TelemetryIngestionService.ingest()`。核心零修改。

### 4.1 MQTT adapter（先做，接 EMQX）
- [ ] `source/mqtt/TelemetryMqttHandler`：訂閱 EMQX topic → decode → `TelemetryIngestRequest(source=MQTT)` → 呼叫核心。
- [ ] `decoder/TelemetryPayloadDecoder`（介面）+ `TelemetryDecoderRegistry` + `CanonicalTelemetryDecoder`（預設 `{ts,values}`）。

### 4.2 HTTP adapter + client 憑證/映射
- [ ] `source/http/TelemetryIngestController`：`POST /v1/ingest/telemetry`（單筆/批次）。
- [ ] `source/http/IngestApiKeyAuthFilter`：機器對機器 API key 認證 → 解析 tenant/client。
- [ ] `client/TelemetryIngestClient`（entity，API key/secret 雜湊、tenant、device scope、限流、啟用旗標）。
- [ ] `client/DeviceExternalRef`（entity，外部碼 → 內部 deviceCode）+ Repository + 管理 API。
- [ ] migration `V5__create_telemetry_ingest_client.sql`、`V6__create_device_external_ref.sql`。

### 4.3 測試
- [ ] decoder registry 單元測試。
- [ ] HTTP ingest 端對端（MockMvc）：認證、映射、批次回應。

**驗收**：MQTT/HTTP 兩來源都能落到同一 `telemetry_data`；新增來源只動 ingest。

---

## Step 5 — telemetry query / REST API

> Package：`com.taipei.iot.telemetry.query`。對前端開放，走既有 `/v1/auth/...` + scope/權限。

- [ ] `query/TelemetryDataService`：歷史查詢（device + 時間範圍 + 量測欄位）、最新值、簡單統計（min/max/avg/降採樣）。
- [ ] `query/TelemetryController`：`GET /v1/auth/telemetry/...`（history / latest / stats），回 `BaseResponse<T>`。
- [ ] DTO + 分頁/時間窗參數校驗。
- [ ] 單元 + MockMvc 測試（含 tenant scope 隔離）。

**驗收**：前端能查單一設備歷史曲線與最新值，且租戶隔離正確。

---

## Step 6 — event-rule（獨立域）

詳見 `event-rule/h2-module-design.md`（已鎖定）。摘要落地順序：

1. 契約（`RuleTriggeredEvent` ✅）
2. model + entity + repository（`event_rule` 等表，migration `V7+`）
3. 求值核心：`ConditionEvaluator`（巢狀 AND/OR/NOT + GT/LT/EQ）+ `RuleEvaluator`
4. Redis 狀態層：`RuleStateStore`（cooldown / duration 收斂 / last-value）
5. 事件接入：`TelemetryRuleListener`（`@Async @EventListener`，顯式還原 `TenantContext`）
6. 動作：`RuleActionHandler` + `NotifyActionHandler`；notification 端 `RuleTriggeredEventListener` 組 `NotificationPayload`
7. CRUD API
8. trigger_log（原生 PG 策略，稀疏寫入）
9. 前端規則編輯器

**驗收**：遙測進來 → 規則命中 → 收斂 → 發 `RuleTriggeredEvent` → 通知送達。

---

## Step 7 — 前端

- [ ] telemetry live 視圖（最新值，WebSocket/輪詢）。
- [ ] telemetry history 視圖（曲線、時間範圍、降採樣）。
- [ ] 選單：`/telemetry`（獨立頂層，見 `event-rule/h1.md`）。
- [ ] i18n（zh-TW / en）+ a11y。

---

## 通用驗收 / 收尾

- [ ] 每個 Step 後 `mvn clean verify` 全綠。
- [ ] 全部就緒後，ArchUnit 收緊 `no_cyclic_dependencies`（見 `4-module-architecture.md` 待辦）。
- [ ] 依賴方向維持單向：`ingest → telemetry → {schema, device}`、`event-rule → schema`，跨域只走 `common`。
