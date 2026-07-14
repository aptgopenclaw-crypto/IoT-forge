這個 `TenantFilterAspect` 類別是一個基於 **Spring AOP（面向切面程式設計）** 的安全守門員，專門用於**多租戶（Multi-tenant）架構**中的資料隔離。

它的核心職責是：**在每次查詢資料庫前，自動且安全地為 Hibernate 啟用租戶過濾器（Tenant Filter），確保 A 租戶絕對看不到 B 租戶的資料。**

以下為該類別的詳細功能與設計亮點解析：

### 1. 核心運作流程
當系統中任何 `Repository` 的方法被呼叫時，這個 Aspect 會在方法執行**前（`@Before`）** 攔截並執行以下邏輯：

*   **步驟一：精準過濾目標 (區分全域與租戶資料)**
    ```java
    if (!(jp.getThis() instanceof TenantScopedRepository)) { return; }
    ```
    它不會盲目地攔截所有 Repository。只有實作了 `TenantScopedRepository` 介面的 Repository（即操作需要租戶隔離的實體）才會被處理。這確保了像 `UserEntity` 或 `TenantEntity` 這種**全域實體**的查詢不會被錯誤地加上租戶條件。
*   **步驟二：處理系統層級上下文 (SystemContext) 的安全檢查**
    ```java
    if (TenantContext.isSystemContext()) { ... }
    ```
    有時系統需要跨租戶查詢資料（例如：後台超管看所有資料、排程任務、登入流程）。此時會進入 `SystemContext`（不套用租戶過濾）。但為了防止惡意繞過，這裡進行了**安全交叉檢查**：
    *   如果是程式碼內部標記為 `trusted` 的呼叫（如 `@RunInSystemTenantContext`），直接放行。
    *   如果是外部請求觸發的，**必須是 `ROLE_SUPER_ADMIN`（超級管理員）**，否則直接拋出例外，防止一般使用者偽造系統上下文來竊取跨租戶資料。
*   **步驟三：Fail-closed 防呆機制 (防止資料外洩)**
    ```java
    if (tenantId == null) { throw new IllegalStateException(...); }
    ```
    如果當前既不是系統上下文，又沒有設定 `tenantId`，系統會**直接報錯並拒絕查詢**。這種「Fail-closed（預設拒絕）」的設計是資訊安全的最佳實踐，寧可讓系統報錯，也絕不允許在沒有租戶身分的情況下撈出資料。
*   **步驟四：動態啟用 Hibernate Filter**
    ```java
    Session session = entityManager.unwrap(Session.class);
    session.enableFilter("tenantFilter").setParameter("tenantId", tenantId);
    ```
    確認安全無虞後，它會取得底層的 Hibernate Session，動態啟用名為 `tenantFilter` 的過濾器，並將當前的 `tenantId` 注入。這代表底層的 Entity 應該有使用 Hibernate 的 `@Filter(name="tenantFilter")` 來定義 SQL 層級的隔離條件（例如 `WHERE tenant_id = :tenantId`）。

---

### 2. 設計亮點與優勢
1.  **無侵入性 (Non-intrusive)**：
    開發者在撰寫 Service 或 Repository 時，完全不需要手動去寫 `WHERE tenant_id = ?` 或是手動去 `enableFilter`。AOP 會在底層自動完成，避免了人為疏漏導致的資料外洩（Data Leakage）。
2.  **效能優異**：
    使用 Hibernate 原生的 `@Filter` 機制，過濾條件會直接組裝進 SQL 語句中由資料庫引擎處理，比在 Java 記憶體中過濾資料更有效率。
3.  **嚴格的權限防線**：
    特別針對 `SystemContext` 加入了 `ROLE_SUPER_ADMIN` 的驗證，堵死了「一般使用者透過 API 觸發特定邏輯，意外進入 SystemContext 而讀取全庫資料」的安全漏洞。

### 總結
`TenantFilterAspect` 是多租戶系統中的**資料安全核心**。它結合了 AOP 的便利性與 Hibernate Filter 的高效性，並透過嚴格的上下文（Context）驗證，確保了多租戶環境下資料隔離的絕對安全性。