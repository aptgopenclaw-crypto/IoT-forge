這兩個 Class 共同構成了一套基於 **Spring AOP** 與 **Hibernate Filter** 的 **多租戶（Multi-Tenancy）資料隔離與安全管控機制**。

它們的主要目的是確保在 SaaS 或多租戶架構下，**每個租戶的資料嚴格隔離**，同時提供安全的 **「系統級別（跨租戶）」** 特權操作通道。

以下為這兩個 Class 的詳細功能與目的解析：

---

### 1. `TenantSystemContextAspect.java`
**核心定位：特權上下文的「安全切換器」與「生命週期管理者」**

*   **主要功能**：
    攔截所有標註了 `@RunInSystemTenantContext` 的方法，並在執行該方法期間，將當前的執行緒上下文切換為 **「系統級別（System Context）」**，允許進行跨租戶（Cross-tenant）的操作。
*   **設計目的與細節**：
    1.  **確保上下文成對（進入/離開）**：使用 `TenantContext.runInSystemContext` 確保方法執行完畢後，系統上下文一定會被清除/還原，避免上下文洩漏（ThreadLocal 記憶體洩漏或影響後續請求）。
    2.  **強制執行順序 (`@Order(0)`)**：註解中明確提到，它**必須比 `TenantFilterAspect` 早執行**（數字越小越外層）。這是因為當特權方法內部呼叫 Repository 時，`TenantFilterAspect` 需要能感知到當前已經是 System Context，從而放行跨租戶查詢。
    3.  **處理 Checked Exception**：因為 `runInSystemContext` 內部使用的是 `Supplier` (不允許拋出 Checked Exception)，此 Aspect 內部實作了 `CheckedAroundException` 來包裝並穿透邊界，在方法結束後再解包拋出，確保呼叫端能收到正確的例外。

---

### 2. `TenantFilterAspect.java`
**核心定位：資料存取層的「強制隔離閘門」與「安全守門員」**

*   **主要功能**：
    在每一次呼叫 Repository 方法**之前**（`@Before`），自動啟用 Hibernate 的 `tenantFilter`，將資料庫查詢/更新嚴格限制在當前租戶的範圍內。
*   **設計目的與細節**：
    1.  **精準攔截與分類**：
        *   只攔截實作了 `TenantScopedRepository` 的 Repository（即操作帶有 `@Filter(name="tenantFilter")` 的租戶級實體）。
        *   **直接放行**全域實體（如 `UserEntity`, `TenantEntity`）的 Repository，避免干擾系統底層運作。
    2.  **Fail-closed 原則（防資料外洩）**：
        *   如果當前不是 System Context，且 `TenantContext.getCurrentTenantId()` 為 `null`，會**直接拋出 Exception 拒絕查詢**。這確保了在沒有明確租戶身分的情況下，絕對不會查到任何資料。
    3.  **動態注入 Hibernate Filter**：
        *   取得當前的 `tenantId`，並透過 `entityManager.unwrap(Session.class)` 啟用 Hibernate 的 `tenantFilter`，將 `tenantId` 作為參數注入。這會讓 Hibernate 自動在底層 SQL 加上 `WHERE tenant_id = ?`。
    4.  **Phase B 安全交叉檢查（防權限繞過）**：
        *   如果偵測到當前是 System Context，會進一步檢查是否為 **Trusted（受信任）**。
        *   **Trusted**：由 `TenantSystemContextAspect` 透過 `@RunInSystemTenantContext` 設定的，視為合法程式碼邏輯，直接放行。
        *   **非 Trusted**：如果是透過程式碼直接 `setSystemContext` 設定的，則會檢查 Spring Security 的 `Authentication`。如果當前登入者**不是 SUPER_ADMIN**，則拋出 `IllegalStateException`，防止惡意或錯誤的程式碼濫用系統權限。

---

### 總結：兩者的協同運作關係

這兩個 Aspect 形成了一個 **「特權通道」** 與 **「底層閘門」** 的配合：

1.  **一般請求**：`TenantFilterAspect` 檢查到一般租戶 ID $\rightarrow$ 啟用 Hibernate Filter $\rightarrow$ 資料嚴格隔離。若沒帶 Tenant ID $\rightarrow$ 攔截報錯 (Fail-closed)。
2.  **特權請求 (如系統排程、後台超管操作)**：
    *   方法加上 `@RunInSystemTenantContext`。
    *   `TenantSystemContextAspect` (外層) 先執行，將 ThreadLocal 設為 System Context (Trusted)。
    *   方法內部呼叫 Repository。
    *   `TenantFilterAspect` (內層) 執行，發現是 System Context $\rightarrow$ 檢查到是 Trusted $\rightarrow$ **不啟用 tenantFilter，直接放行跨租戶查詢**。
    *   方法執行完畢，`TenantSystemContextAspect` 清除 System Context。

這套設計非常嚴謹，既解決了多租戶資料隔離的痛點，又兼顧了系統級別任務的靈活性，同時堵住了潛在的權限繞過漏洞。