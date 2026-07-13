根據您提供的程式碼與註解，`@RunInSystemTenantContext` 是一個**自定義的 Spring AOP 註解**，主要用於**多租戶（Multi-tenant）架構**中，用來臨時提升執行權限或切換上下文。

以下為您詳細拆解這個註解的作用、原理以及在 `AuditAsyncWriter` 中的關鍵應用：

### 1. 核心功能：一句話總結
這個註解的作用是：**將標註的方法「整段執行期間」的租戶上下文（Tenant Context）強制切換為 `SYSTEM`（系統級/超級管理員），方法執行結束後，再自動恢復原來的上下文。**

### 2. 為什麼在 `AuditAsyncWriter` 中需要它？（關鍵場景解析）
在 `AuditAsyncWriter.java` 中，`saveAsync` 方法同時使用了 `@Async` 和 `@RunInSystemTenantContext`，這是為了解決多租戶系統中兩個非常經典的痛點：

*   **痛點一：`@Async` 導致 ThreadLocal 上下文丟失**
    *   多租戶系統通常使用 `ThreadLocal` 來儲存當前請求的 `tenantId`。
    *   當使用 `@Async` 開啟新執行緒時，**新執行緒是不會繼承主執行緒的 `ThreadLocal` 的**。這會導致新執行緒在操作資料庫時，因為找不到 `tenantId` 而報錯，或寫入錯誤的資料。
    *   **解決方式**：透過此註解，AOP 會在新執行緒啟動後，主動為其初始化一個 `SYSTEM` 上下文，確保執行緒有合法的上下文環境。
*   **痛點二：審計日誌需要「跨租戶」操作資料**
    *   審計日誌是全局的，記錄的是「所有租戶」的使用者行為。
    *   在 `saveAsync` 內部，程式碼呼叫了 `userDisplayInfoProvider.findByUserId(userId)` 來查詢使用者的 displayName 和 email。如果當前上下文是某個特定的租戶 A，它可能**查不到**租戶 B 的使用者資料（被租戶隔離攔截器擋住了）。
    *   **解決方式**：切換到 `SYSTEM` 上下文，代表以「系統最高權限」執行。這樣就能**繞過租戶資料隔離限制**，成功查詢到任何租戶的使用者資訊，並將審計日誌寫入系統級的日誌表中。

### 3. 底層實現原理
根據 Javadoc 的說明，這個註解本身只是一個標記（Marker），真正的邏輯是由 Spring AOP 的切面（Aspect）來完成的：
*   **觸發切面**：由 `TenantSystemContextAspect` 攔截帶有此註解的方法。
*   **上下文切換**：在方法執行前，呼叫 `TenantContext.runInSystemContext(Supplier)`，將當前執行緒的上下文設定為 SYSTEM。
*   **自動清理（Cleanup）**：方法執行完畢（無論成功或拋出例外），AOP 會在 `finally` 區塊中自動恢復進入方法前的舊上下文，避免上下文污染到後續的其他請求。

### 4. 使用限制與注意事項（Spring AOP 通則）
因為是依賴 Spring AOP 實現，所以必須遵守 Spring 代理機制的限制，否則註解會**無效（失效）**：
1.  **必須是 Public 方法**：且該類別必須是被 Spring 容器管理的 Bean（有 `@Service`、`@Component` 等註解）。
2.  **禁止同類別內部自呼叫（Self-invocation）**：如果在同一個 class 內，用 `this.saveAsync()` 去呼叫這個方法，AOP 代理不會被觸發，註解就會失效。必須由其他類別（如 Controller 或其他 Service）注入並呼叫。
3.  **不適用於細粒度控制**：如果你只需要方法中的「某幾行程式碼」以 SYSTEM 權限執行，而不是「整個方法」，就不應該用這個註解。

### 5. 替代方案（程式碼級別控制）
正如 Javadoc 最後所提到的，如果不想用 AOP 註解，或者需要在 inline / lambda 中切換上下文，可以直接呼叫底層的工具方法：
```java
// 直接以程式碼方式切換，不需要依賴 AOP 代理
TenantContext.runInSystemContext(() -> {
    // 這裡面的程式碼會以 SYSTEM 上下文執行
    userEventLogRepository.save(entity);
});
```

### 總結
`@RunInSystemTenantContext` 是一個為了**多租戶架構**量身打造的便利註解。在 `AuditAsyncWriter` 中，它完美地解決了**異步執行緒上下文丟失**以及**跨租戶查詢/寫入資料**的問題，確保審計日誌功能能夠在背景穩定、正確地運作。