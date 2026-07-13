這個 `LoginAuditListener.java` 是審計日誌系統中另一個非常關鍵的組件。如果說 `BaseLoggerAspect` 是透過 **AOP（攔截器）** 來記錄一般 API 的呼叫，那麼 `LoginAuditListener` 則是透過 **Spring 事件機制（Event）** 來專門處理「登入/登出」這種特殊場景的審計記錄。

以下為您詳細拆解這個 Class 的設計理念與核心機制：

### 1. 核心職責：一句話總結
**監聽系統發布的登入/登出事件（`LoginAuditEvent`），並將這些事件非同步、安全地寫入審計日誌表中。**

### 2. 核心機制與設計亮點解析

#### ① 事件驅動與極致解耦 (`@EventListener`)
*   **機制**：使用 `@EventListener` 訂閱 `LoginAuditEvent`。
*   **為什麼不用 AOP？** 登入/登出流程通常涉及 Spring Security 的過濾器鏈（Filter Chain），可能在請求到達 Controller 之前就已經完成，或者不適合用標準的 `@AuditEvent` 註解來攔截。使用事件機制，**登入模組只需要在登入成功/失敗時「發布」一個事件，完全不需要知道審計模組的存在**，實現了完美的模組解耦。
*   **為什麼不用 `@TransactionalEventListener`？** 
    *   Javadoc 中特別說明了這點：`@TransactionalEventListener` 會等到「資料庫事務提交後」才觸發。
    *   但登入流程（尤其是驗證失敗、或某些特定的認證機制）**本身不一定包在一個資料庫事務（Transaction）中**。如果使用 `@TransactionalEventListener`，可能會導致事件永遠不觸發。因此，這裡果斷使用 `@EventListener`，確保「只要事件發布，就一定會監聽到」。

#### ② 非同步效能優化 (`@Async("auditExecutor")`)
*   **機制**：搭配 `AuditAsyncConfig` 中定義的 `auditExecutor` 執行緒池。
*   **目的**：寫入資料庫是 I/O 密集型操作。如果同步寫入，會讓使用者在點擊「登入」後，明顯感覺到卡頓（等待日誌寫完才返回 Token）。透過 `@Async`，事件監聽器會把寫入任務丟給背景執行緒池，**讓登入 API 能夠「秒回」，大幅提升使用者體驗**。

#### ③ 多租戶上下文的安全切換 (`TenantContext.runInSystemContext`)
*   **痛點**：審計日誌表（`user_event_log`）通常是系統級別的全局表。在多租戶架構下，如果當前執行緒的 `TenantContext` 是某個特定租戶（或者在登入瞬間，租戶上下文尚未完全建立/解析），直接執行 `save()` 可能會被底層的「租戶資料隔離過濾器（Tenant Filter）」擋住，導致寫入失敗或寫入錯誤的租戶。
*   **解法**：程式碼中使用了 `TenantContext.runInSystemContext(() -> userEventLogRepository.save(entity));`。這行程式碼會**臨時將當前執行緒的上下文切換為 `SYSTEM`（超級管理員/系統級）**，繞過租戶隔離限制，確保日誌能順利寫入全局表中，執行完畢後再自動恢復。
*   *(註：這與前面 `@RunInSystemTenantContext` 註解的目的完全一致，只是這裡因為是事件監聽方法，直接寫在 Lambda 中更加直觀且靈活。)*

#### ④ 最佳努力原則 (Best-Effort) 與容錯
```java
try {
    // ... 組裝 Entity 並 save ...
} catch (Exception ex) {
    log.error("LoginAuditListener: failed to write audit log...");
}
```
*   **設計原則**：審計日誌是輔助功能，**絕對不能因為日誌寫入失敗（例如資料庫暫時斷線），而導致使用者的「登入」動作失敗**。
*   **實作**：使用 `try-catch` 將整個寫入邏輯包住，捕獲所有例外並記錄 Error Log（後續可接 ELK 告警），**不向外拋出異常（do not rethrow）**。這保證了主業務（登入）的絕對穩定。

### 3. 資料組裝細節
在 `onLoginAudit` 方法中，它將 `LoginAuditEvent` 攜帶的資料（如 `tenantId`, `userId`, `email`, `displayName`, `ipAddress` 等）一一映射到 `UserEventLogEntity` 中。
*   值得注意的是 `entity.setExecutionTime(0L);`。因為登入事件通常是瞬間觸發的動作，不像一般 API 需要計算 `Controller -> Service -> DB` 的執行耗時，所以這裡直接設為 0。

### 4. 在 Spring Boot 知識圖譜中的位置

如果將這個 Class 放入我們之前討論的圖譜中：
1.  **觸發源**：Auth 模組（登入服務）在驗證完成後，呼叫 `ApplicationEventPublisher.publishEvent(new LoginAuditEvent(...))`。
2.  **核心引擎 (Event)**：Spring 的 `ApplicationEventMulticaster` 接收到事件，找到帶有 `@EventListener` 的 `LoginAuditListener`。
3.  **擴展引擎 (Async Proxy)**：因為有 `@Async`，Spring 的動態代理攔截了這個呼叫，將任務丟入 `auditExecutor` 執行緒池。
4.  **背景執行**：新執行緒啟動，透過 `TenantContext` 切換上下文，最終透過 `UserEventLogRepository` (Spring Data JPA) 將資料寫入 Database。

### 總結
`LoginAuditListener` 是一個結合了 **Spring Event（事件解耦）**、**@Async（非同步效能）** 以及 **多租戶上下文控制** 的優秀範例。它確保了系統中最關鍵的「登入/登出」行為能夠被精確記錄，同時完全不影響主流程的效能與穩定性。