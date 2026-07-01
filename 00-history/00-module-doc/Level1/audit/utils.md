這個 `PayloadSanitizer` 類別是一個 **API 請求入參脫敏與清洗工具（Payload Sanitizer / Masking Utility）**。

它的**核心目的**是：在將 API 的請求參數（Payload）寫入審計日誌之前，**自動過濾掉敏感資訊（如密碼、Token）並限制資料長度**，以防止機敏資料洩漏到日誌系統中，同時避免巨大的 Payload 撐爆資料庫。

以下是該類別的詳細功能與企業級設計亮點解析：

### 1. 核心功能
*   **JSON 序列化與樹狀解析**：使用 Jackson 的 `ObjectMapper` 將 API 的入參（通常是 `Object[] args`）轉換為 JSON 字串，並解析為 `JsonNode` 樹狀結構，以便進行深度的欄位遍歷。
*   **敏感欄位自動脫敏（Masking）**：
    *   遞迴遍歷整個 JSON 結構（支援巢狀物件與陣列）。
    *   透過 `isSensitive()` 方法判斷欄位名稱，若包含 `password`、`secret`、`token`，或完全等於 `authorization`，則將其值替換為 `***`。
*   **長度截斷保護（Truncation）**：
    *   設定最大長度 `MAX_LENGTH = 2000`。
    *   如果脫敏後的 JSON 字串超過 2000 字元，會強制截斷，只保留前 2000 個字元。
*   **容錯降級（Fail-Safe）**：
    *   整個處理過程被 `try-catch` 包圍。如果 JSON 序列化失敗（例如遇到無法序列化的特殊物件），不會拋出例外，而是直接回傳 `"[sanitize-error]"`。

### 2. 設計目的與安全/穩定性亮點
這個工具類雖然程式碼不多，但解決了企業級日誌系統中兩個非常致命的痛點：

*   **防止日誌洩密（資安合規）**：
    *   *背景*：開發人員在測試時，可能會習慣性地記錄完整的 Request Body。但如果 API 是「修改密碼」或「登入」，Payload 中會包含明文密碼或 Token。
    *   *目的*：透過黑名單關鍵字匹配（`password`, `token` 等），確保即使業務程式碼沒有手動過濾，審計日誌中也絕對不會出現明文密碼。這對於通過 ISO27001 或個資法合規審查至關重要。
*   **防止資料庫與儲存被撐爆（效能保護）**：
    *   *背景*：某些 API 可能允許上傳大檔案（如 Base64 編碼的圖片）或包含超長文字的 JSON。如果將這些幾 MB 甚至幾十 MB 的 Payload 原封不動寫入 `user_event_log` 表，會瞬間耗盡資料庫的儲存空間，並嚴重拖慢查詢效能。
    *   *目的*：`MAX_LENGTH = 2000` 的硬限制，確保每筆審計日誌的 Payload 欄位大小可控，保護了資料庫的穩定性。
*   **絕對不影響主業務（高可用性）**：
    *   *程式碼細節*：`catch (Exception e) { return "[sanitize-error]"; }`
    *   *目的*：日誌脫敏只是輔助功能。如果某個特殊的入參物件導致 Jackson 序列化拋出例外，系統寧可記錄一個錯誤標記 `"[sanitize-error]"`，也**絕對不能讓例外向上拋出而導致原本的 API 請求失敗**。

### 3. 在整體架構中的位置
結合您之前上傳的 `BaseLoggerAspect.java`，我們可以清楚看到它的運作位置：

```java
// 在 BaseLoggerAspect.java 的 finally 區塊中
String payload = PayloadSanitizer.sanitize(pjp.getArgs());

auditAsyncWriter.saveAsync(..., payload, ...);
```
1. AOP 切面攔截到 API 呼叫。
2. 呼叫 `PayloadSanitizer.sanitize()` 將入參「洗乾淨」並「縮小」。
3. 將乾淨且安全的 Payload 交給 `AuditAsyncWriter` 非同步寫入資料庫。

### 總結
`PayloadSanitizer` 是審計系統中的 **「資安過濾器」與「體積壓縮機」**。它以最低調的方式（一個靜態工具類），默默守護著系統的資料安全與資料庫的儲存健康，是企業級日誌系統中不可或缺的防線。