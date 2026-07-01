這個 `AuditAsyncWriter` 類別是一個**非同步審計日誌寫入器（Async Audit Log Writer）**。

它的**核心目的**是：接收由 AOP 切面（`BaseLoggerAspect`）收集到的 API 呼叫上下文，在**背景非同步執行緒**中將這些審計數據持久化到資料庫，同時確保這個寫入過程**不會阻塞主業務線程**，也**不會因為寫入失敗而影響核心業務**。

以下是該類別的詳細功能與設計亮點解析：

### 1. 核心功能
*   **非同步資料庫寫入**：透過 `@Async("auditExecutor")` 註解，將耗時的資料庫寫入操作 (`userEventLogRepository.save`) 丟入專屬的背景執行緒池 (`auditExecutor`) 執行。
*   **使用者資訊動態補充**：在寫入前，透過注入的 `UserDisplayInfoProvider` 根據 `userId` 查詢使用者的 `displayName` (顯示名稱) 和 `email`，讓最終存入資料庫的日誌包含更豐富、對人類可讀的使用者資訊。
*   **日誌實體組裝與持久化**：將傳入的各種上下文參數（如租戶、IP、Payload、錯誤碼等）與查詢到的使用者資訊組裝成 `UserEventLogEntity`，並設定建立時間後存入資料庫。

### 2. 設計目的與企業級架構亮點
這段程式碼在設計上展現了幾個非常成熟的企業級架構考量：

*   **極致的效能保護（非同步與隔離）**：
    *   審計日誌的寫入通常涉及資料庫 I/O，如果同步執行會嚴重拖慢 API 響應時間。使用 `@Async` 確保主線程能立即返回響應給客戶端。
    *   指定專屬的 `auditExecutor` 執行緒池，避免日誌寫入任務耗盡 Tomcat 或全域預設的執行緒池資源。
*   **多租戶架構的上下文切換**：
    *   *程式碼細節*：`@RunInSystemTenantContext`
    *   *目的*：在多租戶 (Multi-tenant) 系統中，審計日誌通常需要寫入系統層級的資料表，或者需要跨租戶寫入權限。這個自定義註解確保在執行寫入時，系統上下文會自動切換到 "SYSTEM" 租戶，避免因為當前請求是普通租戶而導致資料庫寫入權限不足或資料隔離錯誤。
*   **模組解耦（依賴反轉原則 DIP）**：
    *   *程式碼細節*：依賴 `UserDisplayInfoProvider` (一個 Port/Interface) 而不是直接依賴 `UserService` 或 `UserRepository`。
    *   *目的*：審計模組 (`audit`) 需要使用者資訊，但為了避免 `audit` 模組直接耦合到 `auth` 或 `user` 模組，這裡定義了一個 `Port` (埠) 介面。這樣審計模組只依賴抽象，具體的實作由外部模組提供，降低了模組間的循環依賴風險。
*   **高可用與容錯設計（Best-Effort 策略）**：
    *   *程式碼細節*：整個寫入邏輯被 `try-catch` 包圍，且 `catch` 區塊中**只記錄 Error Log，不重新拋出例外** (`do not rethrow, do not affect business logic`)。
    *   *目的*：審計日誌是輔助功能，**絕對不能因為日誌系統掛掉或資料庫鎖死而導致核心業務（如設備控制、訂單創建）失敗**。這種 "盡力而為" (Best-effort) 的設計保證了主業務的高可用性。
    *   *運維考量*：註解中提到透過 ELK 監控 `"Audit async write failed"` 關鍵字來觸發告警，確保日誌系統出問題時運維人員能及時介入。
*   **解決 ThreadLocal 上下文丟失問題**：
    *   *背景*：因為 `@Async` 會開啟**新的執行緒**，原本在主執行緒的 `ThreadLocal` (如 `TenantContext`, `SecurityContext`) 在新執行緒中會失效。
    *   *解法*：這就是為什麼 `saveAsync` 方法需要接收 `tenantId`, `userId` 等十幾個參數的原因。這些值是由前端的 `BaseLoggerAspect` 在**主執行緒**提前抓取好，再透過方法參數「顯式傳遞」給非同步執行緒的。

### 總結：與 `BaseLoggerAspect` 的完美協同
如果把審計系統看作一個工廠：
1.  **`BaseLoggerAspect` (切面)** 是**採集員**：負責在 API 執行的第一線，精準、安全地收集所有環境與業務數據（包含處理 IP 防偽造、資料脫敏）。
2.  **`AuditAsyncWriter` (寫入器)** 是**倉儲員**：負責在後台安靜、穩定地將這些數據整理（補充使用者資訊）並存入倉庫（資料庫），即使倉庫暫時擁堵，也不會讓採集員的工作卡住。

兩者結合，構成了一套**高效能、高可用、安全且解耦**的企業級 API 審計追蹤機制。