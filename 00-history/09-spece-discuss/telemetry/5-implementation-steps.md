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
| 2 | schema 改造 | ✅ 已完成 | `schema` 拆 attributes/telemetry（V3 migration）+ `SchemaProviderAdapter` + 兩段編輯 API |
| 3 | telemetry 核心 | ✅ 已完成 | storage（V4 月分區）+ validation（json-schema 1.5.6）+ `TelemetryIngestionService` |
| 4 | ingest 模組 | ✅ 已完成 | mqtt adapter（gated）+ http adapter + API key 認證 + 外部碼映射（V5/V6） |
| 5 | telemetry query/api | ✅ 已完成 | 歷史 / 最新值 / 統計 REST（/v1/auth/telemetry，DEVICE_VIEW，租戶隔離） |
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

## Step 2 — schema 改造（device_templates 拆 attributes / telemetry）✅ 已完成

**目標**：把既有 `device_templates.schema` 的單一 JSON 拆成 `attributes`（靜態屬性）與 `telemetry`（時序量測）兩段，並提供 `SchemaProviderPort` 的 adapter，讓 telemetry 核心能取得「該 deviceType 的 telemetry JSON Schema」。

> 定案：採「單一 `schema` JSONB 內巢狀 `attributes` / `telemetry` 兩鍵」（與 `2-telemetry-design.md` 第三節及 `SchemaProviderPort` javadoc 一致），**不新增欄位**。

### 2.1 資料遷移
- [x] `V3__split_device_template_schema.sql`
  - 把既有 `schema` 正規化為 `{ "attributes": {...}, "telemetry": {...} }`：`{}` → 兩段空物件；舊版扁平 schema（無 attributes/telemetry 鍵）整段歸入 `attributes`、`telemetry` 補空物件；已拆分者由 `WHERE` 略過（冪等）。
  - schema-agnostic（無 schema 前綴、無 search_path）。

### 2.2 程式調整
- [x] `DeviceTemplateService` 新增 `getAttributesSchema` / `updateAttributesSchema` / `getTelemetrySchema` / `updateTelemetrySchema`（單段讀寫，保留另一段、version 遞增、type 不存在則自動建立）。
- [x] `DeviceTemplateController` 新增 `GET|PUT /{deviceType}/schema/attributes`、`GET|PUT /{deviceType}/schema/telemetry`（沿用既有權限）。
- [x] `schema/service/SchemaProviderAdapter implements SchemaProviderPort`
  - `getTelemetrySchema(deviceType)` → `Optional<JsonNode>`，取 `schema.telemetry`（空物件/缺鍵視為 empty）。
  - 註：專案目前無快取基礎設施（無 `@EnableCaching`），schema 讀取為單純 DB 查詢，暫不加快取。

### 2.3 測試
- [x] `SchemaProviderAdapterTest`（4 案）：有/空 telemetry、缺 telemetry 鍵、type 不存在。
- [x] `DeviceTemplateServiceTest`（3 案）：單段更新保留另一段且 version+1、未知 type 自動建立、讀取 telemetry 段。
- [x] migration 拆分邏輯以 `WHERE` 冪等保護（套用後可手動 psql 驗證）。

**驗收**：`SchemaProviderPort.getTelemetrySchema("<type>")` 能取得正確 telemetry schema；既有 device template 功能不回歸。（`mvn test` 10 案全綠）

---

## Step 3 — telemetry 核心（storage + validation + ingestion）✅ 已完成

> 純後端核心，**不接任何來源**，全部以單元測試驗證。Package：`com.taipei.iot.telemetry`。

### 3.1 storage（原生 PG 月分區，定案見 `2-telemetry-design.md` 第五節）
- [x] `V4__create_telemetry_data.sql`
  - `telemetry_data` `PARTITION BY RANGE (ts)`；複合 PK `(ts, id)`；`id BIGINT GENERATED ALWAYS AS IDENTITY`。
  - 欄位：`tenant_id`、`device_id`、`device_type`、`ts`、`values JSONB`、`source VARCHAR(20) DEFAULT 'MQTT'`、`source_client_id VARCHAR(50)`、`received_at`。
  - index：`(device_id, ts)`、`(tenant_id, ts)`、`(device_type, ts)` + `BRIN (ts)`。
  - 先建近月分區（含 default 分區避免漏寫）。
- [x] `storage/TelemetryData`（entity）、`TelemetryDataRepository`。
- [x] storage 抽象層：`TelemetryStore` 介面 + `JpaTelemetryStore` 實作（含 `TelemetryReading` 輸入契約），封裝寫入 — 未來可換 hypertable。
- [x] 分區維護 `@Scheduled`：`TelemetryPartitionMaintenanceJob` 預建近月分區 + DROP 超過保留期（90d）的舊分區。

### 3.2 validation
- [x] pom 加 `com.networknt:json-schema-validator:1.5.6`。
- [x] `validation/TelemetryValidationService`：以 `SchemaProviderPort` 取 schema，對 `values` 做 JSON Schema 驗證，回傳結構化錯誤（`TelemetryValidationResult`）。

### 3.3 ingestion
- [x] `ingest/TelemetryIngestRequest`（canonical 模型：deviceCode, ts, values, tenantId, source, sourceClientId, rawPayload）。
- [x] `ingest/TelemetrySource` enum（MQTT / HTTP_API / BATCH_IMPORT / KAFKA）。
- [x] `ingest/TelemetryIngestionService`（介面）+ `...ServiceImpl`：
  1. `DeviceLookupPort.resolve(deviceCode, tenantId)` → `DeviceRef`（找不到→拒絕，`DeviceLookupAdapter` 新增於 device 模組）。
  2. `SchemaProviderPort.getTelemetrySchema(deviceType)` → 驗證。
  3. 通過 → `TelemetryStore` 寫入（標記 source）。
  4. `ApplicationEventPublisher` 發 `TelemetryReceivedEvent`。
  - 提供 `ingestBatch(List<>)`（API 常一次多筆）。

### 3.4 測試
- [x] ingestion 單元測試（mock 兩個 port + store + publisher）：成功、device 不存在、schema 驗證失敗、批次部分失敗（`TelemetryIngestionServiceImplTest` 5 案）。
- [x] validation 單元測試（`TelemetryValidationServiceTest` 4 案）。
- [x] storage 寫入測試打真 PG 分區（`TelemetryStoreIntegrationTest`，驗證落入 `telemetry_data_2026_06`）。

**驗收**：給定合法 `TelemetryIngestRequest` → 寫入 `telemetry_data` 並發出事件；非法輸入被正確拒絕；核心不認得任何協定。

---

## Step 4 — ingest 模組（外圈 adapter）✅ 已完成

> Package：`com.taipei.iot.ingest`。把多來源收斂成 canonical，呼叫 `TelemetryIngestionService.ingest()`。核心零修改。
> ingest 僅依賴 telemetry + common（deviceCode 透傳，設備解析在 telemetry 核心內以 `DeviceLookupPort` 完成），維持單向無環。

### 4.1 MQTT adapter（先做，接 EMQX）
- [x] `source/mqtt/TelemetryMqttHandler`：topic `device/{deviceCode}/telemetry` 取設備碼 → decode → `TelemetryIngestRequest(source=MQTT)` → 呼叫核心（與 broker 解耦、可單測）。`MqttIngestConfig` 以 `@ConditionalOnProperty(mqtt.enabled)` gated，預設停用（本機/CI 無 broker 不影響啟動）。
- [x] `decoder/TelemetryPayloadDecoder`（介面）+ `TelemetryDecoderRegistry` + `CanonicalTelemetryDecoder`（預設 `{ts,values}`，支援單筆/陣列/寬鬆物件）。

### 4.2 HTTP adapter + client 憑證/映射
- [x] `source/http/TelemetryIngestController`：`POST /v1/ingest/telemetry`（單筆）+ `/v1/ingest/telemetry/batch`（批次）。
- [x] `source/http/IngestApiKeyAuthFilter`：`X-API-Key`/`X-API-Secret` 機器對機器認證（BCrypt）→ 解析 tenant/client；`IngestSecurityConfig` 為 `/v1/ingest/**` 獨立 `@Order(0)` SecurityFilterChain（不動 auth 主鏈）。
- [x] `client/TelemetryIngestClient`（entity，API key + secret 雜湊、tenant、device scope、限流、啟用旗標；全域非租戶過濾）+ Repository。
- [x] `client/DeviceExternalRef`（entity，外部碼 → 內部 deviceCode）+ Repository。
- [x] migration `V5__create_telemetry_ingest_client.sql`、`V6__create_device_external_ref.sql`。ErrorCode 加 88070/88071/88072。
- [ ] （延後）client / external-ref 管理 CRUD API（後台佈建用，待 Step 7 後台一併處理）。

### 4.3 測試
- [x] decoder registry 單元測試（`TelemetryDecoderRegistryTest` 4 案 + `CanonicalTelemetryDecoderTest` 7 案 + `TelemetryMqttHandlerTest` 4 案）。
- [x] HTTP ingest 端對端（`TelemetryIngestControllerIntegrationTest`、6 案：認證、外部碼映射、批次、錯誤 secret 401、停用憑證 403、無憑證 401）。

**驗收**：MQTT/HTTP 兩來源都能落到同一 `telemetry_data`；新增來源只動 ingest。

---

## Step 5 — telemetry query / REST API

> Package：`com.taipei.iot.telemetry.query`。對前端開放，走既有 `/v1/auth/...` + scope/權限。

- [x] `query/TelemetryDataService`：歷史查詢（device + 時間窗 [from,to) + 分頁）、最新值、各數值欄位 min/max/avg 統計。`telemetry_data` 無 tenantFilter，故所有查詢<strong>明確</strong>帶 `tenantId` 條件保證租戶隔離；統計以 `JdbcTemplate` 對 jsonb 欄位先正規表示式過濾數值樣本再 cast（避免非數值整批失敗）。
- [x] `query/TelemetryController`：`GET /v1/auth/telemetry/devices/{deviceId}/{history|latest|stats}`，沿用 `DEVICE_VIEW` 權限，回 `BaseResponse<T>`（history 走 `PageResponse`）。
- [x] DTO（`TelemetryPointResponse` / `TelemetryLatestResponse` / `TelemetryFieldStats`）+ 時間窗校驗（預設近 24h、`from<to` 否則 88080、分頁上限 1000）。
- [x] 單元（`TelemetryControllerTest` MVC slice 4 案：契約 / `DEVICE_VIEW` 403 / ISO instant 綁定）+ 整合（`TelemetryDataServiceIntegrationTest` 真 PG 6 案：歷史遞減、最新值、統計、自動推導欄位、跨租戶隔離、非法區間）。

**驗收**：前端能查單一設備歷史曲線與最新值，且租戶隔離正確。

---

## Step 6 — event-rule（獨立域）✅

詳見 `event-rule/h2-module-design.md`（已鎖定）。摘要落地順序：

1. 契約（`RuleTriggeredEvent` ✅）
2. model + entity + repository（`event_rule` 等表，migration `V7+` ✅）
3. 求值核心：`ConditionEvaluator`（巢狀 AND/OR/NOT + GT/LT/EQ ✅）+ `RuleEvaluator` ✅
4. Redis 狀態層：`RuleStateStore`（cooldown / duration 收斂 / last-value ✅）
5. 事件接入：`TelemetryRuleListener`（`@Async @EventListener`，顯式還原 `TenantContext` ✅）
6. 動作：`RuleActionHandler` + `NotifyActionHandler`；notification 端 `RuleTriggeredEventListener` 組 `NotificationPayload` ✅
7. CRUD API ✅
8. trigger_log（原生 PG 策略，稀疏寫入 ✅）
9. 前端規則編輯器（Step 7 後端完成，前端 TBD）

**驗收**：遙測進來 → 規則命中 → 收斂 → 發 `RuleTriggeredEvent` → 通知送達。  
**測試**：34 unit（含 ArchUnit）+ 22 integration all green ✅  
**注意**：`ConditionNode.isLeaf()` 需 `@JsonIgnore`，否則 Hibernate JSONB deepCopy 失敗。

---

## Step 7 — 前端 ✅

- [x] telemetry live 視圖（`TelemetryLiveView.vue`，15 秒輪詢最新值，設備選擇器）。
- [x] telemetry history 視圖（`TelemetryHistoryView.vue`，時間範圍篩選 + 統計摘要 + 分頁歷史表格）。
- [x] event-rule 列表視圖（`EventRuleListView.vue`，CRUD + 啟用/停用 + 規則表單）。
- [x] event-rule 觸發記錄視圖（`EventRuleLogsView.vue`，時間範圍 + severity 篩選）。
- [x] API 層：`src/api/telemetry/index.ts`、`src/api/eventrule/index.ts`。
- [x] 型別：`src/types/telemetry.ts`（TelemetryPointResponse / TelemetryLatestResponse / TelemetryFieldStats / EventRuleRequest 等）。
- [x] 路由：`/telemetry/live`、`/telemetry/history`、`/event-rules/list`、`/event-rules/logs` 靜態路由加入 `router/index.ts`（backend menus 140-142 已對應 route_name）。
- [x] i18n：zh-TW / zh-CN / en 均補齊 `telemetry.*` + `eventRule.*` + `common.{all,to,startTime,...}` 鍵值；修補 pre-existing `device.debuggerBtn/decommissionBtn` 不對齊。
- [x] 驗收：`npm run type-check` 零錯誤（新檔案），i18n lint 29/29 通過，Vite build 成功。

---

## 通用驗收 / 收尾 ✅

- [x] `mvn clean verify` 全綠：**1354 unit + 22 integration = 1376 tests，0 failures**。
- [x] ArchUnit 收緊 `no_cyclic_dependencies`：已無 freeze，任何新循環即打斷 build。新增 `iot_dependency_directions` 規則，明確編碼 `ingest→telemetry→{schema,device}`、`eventrule→schema`、`notification→common events（不直接依賴 eventrule）` 的單向約束。
- [x] 依賴方向驗證：4 arch tests (LayeredArchitectureTest) 全通過，方向圖正確且無循環。
