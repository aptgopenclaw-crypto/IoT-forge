根據您提供的核心實作程式碼與架構文件，我為您萃取出 **`telemetry` (遙測核心) 功能模組**的完整設計藍圖。

如果說 `ingest` 是系統的「邊界守門員」，那麼 `telemetry` 就是整個 IoT 系統的**「核心心臟」**。它完全屏蔽了外部世界的複雜性，只專注於資料的驗證、持久化與事件觸發。

---

# 📦 Telemetry 功能模組設計萃取

## 一、 模組定位與核心職責
`telemetry` 模組是系統的**核心域 (Core Domain)**。它的核心使命是「接收標準化的遙測資料，確保其合法性，持久化儲存，並觸發下游業務事件」。

**核心職責包含：**
1. **設備合法性校驗**：確認資料來源的設備是否存在且有效。
2. **Schema 嚴格驗證**：根據設備類型的 JSON Schema 驗證遙測數值（Key-Value）的格式與範圍。
3. **時序資料持久化**：將合法的遙測數據寫入底層儲存（如 PostgreSQL 分區表）。
4. **領域事件發布**：資料寫入成功後，發布 `TelemetryReceivedEvent`，驅動下游的 `event-rule` (事件規則) 模組。

## 二、 模組邊界與依賴關係
* **依賴方向**：**單向、無環**。
* **依賴誰**：
  * `device` (透過 `DeviceLookupPort` 解析設備)
  * `schema` (透過 `TelemetryValidationService` 讀取 Schema 進行驗證)
  * `common` (使用 `TenantContext` 處理多租戶，發布 `TelemetryReceivedEvent`)
* **被誰依賴**：`ingest` (最外圈適配器呼叫核心)。
* **關鍵交接**：`telemetry` **完全不認識** MQTT、HTTP 或任何外部協定。它只接收標準化的 `TelemetryIngestRequest`，並回傳 `TelemetryIngestResult`。

---

## 三、 核心元件與模型設計

### 1. 核心入口 (Application Service)
* **`TelemetryIngestionService` (介面) / `TelemetryIngestionServiceImpl` (實作)**
  * **職責**：遙測接入的業務邏輯核心，定義了標準的處理 Pipeline。
  * **方法**：
    * `ingest(TelemetryIngestRequest)`：處理單筆接入。
    * `ingestBatch(List<TelemetryIngestRequest>)`：處理批次接入（底層透過 Stream 逐筆呼叫 `ingest`，確保部分失敗不影響整體）。

### 2. 標準化契約模型 (Canonical Models)
為了實現「協定無關」，模組定義了嚴格的輸入輸出契約：
* **`TelemetryIngestRequest` (輸入)**
  * **職責**：進入核心的標準化請求。
  * **欄位**：`deviceCode` (設備碼), `tenantId` (租戶), `ts` (時間戳), `values` (遙測數值), `source` (來源通道), `sourceClientId`, `rawPayload`。
  * **設計亮點**：將所有外部來源的差異抹平，核心邏輯無需撰寫任何 `if-else` 來判斷來源。
* **`TelemetryIngestResult` (輸出)**
  * **職責**：單筆接入的結果封裝。
  * **欄位**：`success`, `deviceCode`, `deviceId`, `errorCode`, `message`, `validationErrors`。
  * **設計亮點**：提供靜態工廠方法 (`success`, `failure`, `validationFailure`)。特別保留了 `validationErrors` 欄位，能將 Schema 驗證失敗的具體原因結構化地回傳給上層。
* **`TelemetrySource` (列舉)**
  * **職責**：標記資料來源 (`MQTT`, `HTTP_API`, `BATCH_IMPORT`, `KAFKA`)，寫入 DB 時作為審計與追蹤依據。

---

## 四、 核心處理流程 (The Ingestion Pipeline)

在 `TelemetryIngestionServiceImpl.doIngest()` 中，實作了嚴謹的 4 步驟 Pipeline：

1. **🛡️ 多租戶情境設定 (Tenant Context Setup)**
   * 提取 `request.tenantId()` 設定至 `TenantContext` (ThreadLocal)。
   * **關鍵設計**：使用 `try-finally` 區塊，確保無論後續邏輯是否拋出異常，都能還原或清除 `TenantContext`，**徹底杜绝多租戶資料串流與記憶體洩漏**。
2. **🔍 設備解析與校驗 (Device Resolution)**
   * 呼叫 `DeviceLookupPort.resolve(deviceCode, tenantId)`。
   * 若設備不存在，直接返回 `failure` (錯誤碼: `DEVICE_NOT_FOUND`)，**不進入後續昂貴的驗證與儲存流程**。
3. **📏 Schema 嚴格驗證 (Schema Validation)**
   * 呼叫 `TelemetryValidationService.validate(deviceType, values)`。
   * 若驗證失敗，記錄 Warn 日誌，並返回 `validationFailure`，**將具體的 Schema 錯誤清單 (`validationErrors`) 回傳**。
4. **💾 持久化與事件觸發 (Persist & Publish)**
   * 補全預設值：若 `ts` 為 null 則使用 `Instant.now()`；若 `source` 為 null 則預設為 `MQTT`。
   * 呼叫 `TelemetryStore.save()` 寫入 `TelemetryReading`。
   * 呼叫 `ApplicationEventPublisher.publishEvent()` 發出 `TelemetryReceivedEvent`，**無縫接軌下游的事件規則引擎**。
   * 返回 `success`。

---

## 五、 關鍵設計決策總結

1. **極致的協定無關 (Protocol Agnostic)**
   * 核心模組沒有 import 任何 Spring Web、MQTT 或 HTTP 相關的套件。這意味著 `telemetry` 模組可以輕易地被抽取出來，作為獨立的微服務或進行純單元測試（只需 Mock `DeviceLookupPort` 和 `TelemetryStore`）。
2. **Fail-Fast 與 結構化錯誤 (Fail-Fast & Structured Errors)**
   * 在 Pipeline 中，設備找不到或 Schema 驗證失敗時，會**立即短路 (Short-circuit)** 並返回帶有詳細錯誤碼的 `TelemetryIngestResult`。這避免了無效資料進入儲存層，節省系統資源。
3. **高容錯的批次語意 (Resilient Batch Processing)**
   * `ingestBatch` 採用 `stream().map(this::ingest).toList()`。這確保了批次處理時，**每一筆資料都是獨立的事務與驗證**。即使第 50 筆資料驗證失敗，前 49 筆依然會成功寫入並觸發事件，完美支援 IoT 場景下網路不穩定導致的部分資料異常。
4. **事件驅動架構 (Event-Driven)**
   * 透過 Spring 的 `ApplicationEventPublisher` 發布 `TelemetryReceivedEvent`。`telemetry` 模組**不需要知道**是誰在監聽這個事件（可能是 `event-rule`，也可能是未來的報表模組），達成了模組間的**編譯期解耦**。

## 六、 歸屬資料表 (Database Schema)
根據架構設計，`telemetry` 模組擁有以下資料表的所有權：
| 資料表名稱 | 歸屬模組 | 用途說明 |
| :--- | :--- | :--- |
| `telemetry_data` | `telemetry.storage` | 主時序表。採用原生 PostgreSQL range partition (按 `ts` 月分區) + BRIN index，不依賴 TimescaleDB，保持 Schema-agnostic。 |