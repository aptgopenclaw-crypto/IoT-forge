根據您提供的架構設計文件與 Java 實作程式碼，我為您萃取出 **`ingest` (資料接入) 功能模組**的完整設計藍圖。

這份設計展現了標準的 **Ports and Adapters (六邊形架構)** 精神，將 `ingest` 作為最外圍的適配器，完美隔離了外部協定與內部核心。

---

# 📦 Ingest 功能模組設計萃取

## 一、 模組定位與核心職責
`ingest` 模組是整個 IoT 系統的**最外圍接入邊界 (Adapter Layer)**。它的核心使命是「接收外部髒資料，轉換為內部標準格式」，讓內部的 `telemetry` 核心模組完全不需要感知外部協定或廠商格式。

**核心職責包含：**
1. **協定接收**：處理不同來源的接入（目前實作為 HTTP API，架構保留 MQTT/Kafka 擴充性）。
2. **M2M 機器認證**：處理第三方設備或網關的 API Key/Secret 認證。
3. **設備碼映射**：將外部的 `externalCode` 轉換為內部的 `deviceCode`。
4. **格式收斂**：將各種來源的 Payload 解碼並收斂為標準的 Canonical 模型 (`TelemetryIngestRequest`)。

## 二、 模組邊界與依賴關係
* **依賴方向**：**單向、無環**。
* **依賴誰**：`telemetry` (核心 Port)、`device` (設備解析 Port)、`common` (共用事件/錯誤碼)。
* **被誰依賴**：**無**（它是最外圈，沒有其他模組依賴它）。
* **關鍵交接**：`ingest` 完成所有前置處理後，只呼叫 `telemetry` 模組的 `TelemetryIngestionService.ingest()` 介面，將標準化資料交接給核心。

---

## 三、 核心元件與 Package 設計

根據程式碼實作，HTTP 接入部分的元件設計如下：

### 1. 接入控制層 (Source / HTTP Adapter)
* **`TelemetryIngestController`**
  * **職責**：暴露 REST API 給第三方呼叫。
  * **端點**：
    * `POST /v1/ingest/telemetry`：單筆遙測數據上報。
    * `POST /v1/ingest/telemetry/batch`：批次遙測數據上報。
  * **設計亮點**：Controller 極度輕量，僅負責接收 HTTP 請求、提取 `IngestClientPrincipal`，並將工作委派給 Application Service。

### 2. 安全認證層 (Security / M2M Auth)
為了不污染主系統的使用者 JWT 認證鏈，`ingest` 模組**自建獨立的 Security Filter Chain**。
* **`IngestSecurityConfig`**
  * **職責**：配置 `/v1/ingest/**` 專屬的 Spring Security 過濾鏈。
  * **設計亮點**：
    * 使用 `@Order(0)` 確保最高優先級，優先於 Swagger 和主系統認證鏈。
    * 設定為 `STATELESS` 並停用 CSRF（純 Header 認證）。
    * 自定義 `authenticationEntryPoint` 與 `accessDeniedHandler`，確保認證失敗時回傳統一的 `BaseResponse` JSON 格式。
* **`IngestApiKeyAuthFilter`**
  * **職責**：實作 M2M (Machine-to-Machine) 的 API Key/Secret 認證。
  * **流程**：
    1. 讀取 `X-API-Key` 與 `X-API-Secret` Header。
    2. 查詢 `TelemetryIngestClientRepository` 取得憑證。
    3. 使用 BCrypt 比對 Secret Hash。
    4. 檢查 Client 是否啟用 (`isEnabled`)。
    5. 建立 `IngestClientPrincipal` 並注入 `SecurityContext`，同時設定 `TenantContext` (多租戶隔離)，請求結束後清除 Context。
  * **安全亮點**：「憑證不存在」與「Secret 錯誤」統一回傳 `IOT_INGEST_CREDENTIALS_INVALID`，**防止帳號列舉攻擊 (Account Enumeration)**。

### 3. 應用服務層 (Application Service)
* **`TelemetryHttpIngestService`**
  * **職責**：HTTP 接入的業務邏輯核心，負責資料轉換與映射。
  * **核心邏輯**：
    1. **設備碼解析 (`resolveDeviceCode`)**：優先使用請求中的 `deviceCode`；若無，則使用 `externalCode` 去 `DeviceExternalRefRepository` 查詢對應的內部 `deviceCode`。
    2. **Canonical 轉換**：將 HTTP Request 轉換為核心模組認識的 `TelemetryIngestRequest` (標記 source 為 `HTTP_API`)。
    3. **委派核心**：呼叫 `TelemetryIngestionService.ingest()` 寫入數據。
  * **容錯亮點 (Partial Success)**：在批次處理 (`ingest` 方法) 時，採用**逐筆獨立處理**。如果某一筆的外部碼映射失敗，該筆會返回 `IOT_INGEST_DEVICE_NOT_MAPPED` 錯誤，但**不會中斷或影響批次中其他筆的處理**。

### 4. 客戶端與映射層 (Client & Mapping)
*(對應架構文件中的 `ingest/client` 與資料表)*
* **`TelemetryIngestClient` (Entity)**：儲存第三方接入者的憑證 (API Key, Secret Hash, Tenant, 啟用狀態)。
* **`DeviceExternalRef` (Entity)**：儲存外部設備碼 (`externalCode`) 到內部設備碼 (`deviceCode`) 的映射關係。

---

## 四、 關鍵設計決策總結

1. **絕對的解耦 (Isolation)**
   * **安全隔離**：`IngestSecurityConfig` 獨立於主系統的 JWT 認證，M2M 認證邏輯完全封裝在 `ingest` 模組內。
   * **依賴隔離**：`ingest` 不依賴任何具體的實作，只依賴 `telemetry` 的 Port (`TelemetryIngestionService`)。未來若要新增 MQTT 接入，只需在 `ingest` 內新增 MQTT Adapter，核心程式碼零修改。
2. **高容錯的批次處理 (Resilience)**
   * 批次上傳時，設備映射失敗或單筆驗證失敗，僅標記該筆結果為 Failure，確保網路不穩定或個別設備離線時，不會導致整個批次的數據丟失。
3. **多租戶情境管理 (Multi-tenancy)**
   * 在 `IngestApiKeyAuthFilter` 中，認證通過後立即設定 `TenantContext.setCurrentTenantId()`，並在 `finally` 區塊中清除，確保多租戶資料隔離的嚴謹性，避免 ThreadLocal 記憶體洩漏或資料串流。
4. **統一的錯誤回應 (Consistent Error Handling)**
   * 即使是在 Filter 層攔截到的認證失敗，也透過 `ObjectMapper` 序列化為系統統一的 `BaseResponse` 格式，保持 API 契約的一致性。

## 五、 歸屬資料表 (Database Schema)
根據架構設計，`ingest` 模組擁有以下資料表的所有權：
| 資料表名稱 | 歸屬模組 | 用途說明 |
| :--- | :--- | :--- |
| `telemetry_ingest_client` | `ingest.client` | 第三方 API 憑證（API key / client secret 雜湊、tenant、device scope、限流、啟用旗標） |
| `device_external_ref` | `ingest.client` | 外部設備碼 → 內部 `deviceCode` 映射表 |