這是一個非常經典且致命的 **「先有雞還是先有蛋」的邏輯死結（Chicken-and-Egg Problem）**，導致您的 **多認證源（Multi-Auth-Provider）架構在實際運作時完全失效**。

雖然從您提供的程式碼來看，第 121 行確實有寫 `.tenantId(resolvedTenantId)`，但審查委員指出的問題在於 **`resolveLoginTenantId` 這個方法的「推導邏輯」有嚴重缺陷**，導致它在關鍵情境下**必然回傳 `null`**。

以下為您詳細拆解這個問題的盲點、後果與解法：

### 1. 程式碼的邏輯死結在哪裡？

請看 `AuthServiceImpl.java` 底部的 `resolveLoginTenantId` 方法：

```java
private String resolveLoginTenantId(String email) {
    try {
        return userRepository.findByEmail(email).map(user -> {
            List<UserTenantMappingEntity> mappings = ...;
            // 🚨 盲點 1：只有當使用者「剛好只屬於 1 個租戶」時才回傳 tenantId
            return (mappings.size() == 1) ? mappings.get(0).getTenantId() : null; 
        }).orElse(null); // 🚨 盲點 2：如果本地資料庫找不到這個 email，直接回傳 null
    } ...
}
```

這個邏輯依賴 **「本地資料庫 (`userRepository`) 已經有這個使用者的資料」** 才能查出 `tenantId`。這會導致以下兩種致命情境：

*   **情境 A：全新的 LDAP / SSO 使用者（首次登入）**
    *   使用者第一次用公司 AD 帳號登入，本地資料庫**還沒有**他的資料（尚未觸發 JIT 自動建檔）。
    *   `userRepository.findByEmail(email)` 回傳 `null`。
    *   `resolvedTenantId` 變成 `null`。
    *   Dispatcher 收到 `null`，不知道要去哪個 LDAP 驗證，**直接退回 Local Auth**。
    *   **結果**：Local Auth 去資料庫找密碼，當然找不到，使用者看到「帳號或密碼錯誤」，**SSO 永遠登不進去**。
*   **情境 B：一個帳號屬於多個租戶（Multi-tenant user）**
    *   使用者在本地有資料，但屬於 2 個以上的租戶。
    *   `mappings.size() == 1` 條件不成立，回傳 `null`。
    *   **結果**：同樣退回 Local Auth，LDAP/SSO 失效。

### 2. 造成的嚴重後果（為什麼是 High 風險？）

*   **Multi-Provider 形同虛設**：您系統中設計的 LDAP、OIDC、SAML 等外部認證源，在 Production 環境中**永遠不會被觸發**。所有流量都會被 `LocalAuthProvider` 攔截。
*   **嚴重的資安與管理漏洞**：
    *   假設租戶 A 設定了 LDAP。員工離職時，IT 在 AD 中停用了該帳號。
    *   但因為系統登入時 `tenantId=null`，退回了 Local Auth。如果該員工在本地資料庫中留有舊密碼（或系統預設密碼），**他依然可以繞過 AD 的停用機制，成功登入系統**。
*   **使用者體驗災難**：設定要使用 SSO 的租戶，其使用者在登入畫面輸入帳密後，不會被導向 SSO 登入頁，而是直接噴出「登入失敗」。

### 3. 如何修復？（打破死結）

要解決這個問題，**不能依賴「本地已存在的使用者」來決定認證源**。必須在「認證發生前」，透過其他方式推導出 `tenantId`。

建議採用以下其中一種策略來重構 `resolveLoginTenantId`：

#### 方案 A：Email Domain 路由表（最推薦，UX 最佳）
維護一張 `Email Domain -> Tenant` 的對應關係（可以寫在設定檔、Redis 或資料庫）。
*   邏輯：`@companyA.com` -> Tenant A (LDAP)；`@companyB.com` -> Tenant B (OIDC)。
*   實作：
    ```java
    private String resolveLoginTenantId(String email) {
        String domain = email.substring(email.indexOf("@") + 1);
        // 查詢 Domain 對應的 Tenant ID (例如透過 TenantRepository 或快取)
        return tenantRepository.findByEmailDomain(domain)
                               .map(TenantEntity::getTenantId)
                               .orElse(null); 
    }
    ```

#### 方案 B：前端登入畫面先選擇「所屬企業/租戶」
如果 Email Domain 無法區分（例如都用 `@gmail.com` 或外部客戶），則在登入 UI 上增加一個下拉選單或步驟：
1.  前端先讓使用者選擇「我是 XX 公司的員工」或輸入 `tenantCode`。
2.  前端將 `tenantId` 或 `tenantCode` 放入 `LoginRequest` 的 payload 中。
3.  後端直接讀取 `request.getTenantId()`，不再需要自己猜。

#### 方案 C：要求前端明示（針對 B2B 系統）
在 `LoginRequest` DTO 中強制要求帶入 `tenantCode`（租戶代碼），後端根據 `tenantCode` 去查 `TenantEntity` 取得 `tenantId`，然後傳給 Dispatcher。

### 總結
審查委員的意見一針見血。您的程式碼雖然「形式上」有傳遞 `tenantId`，但「實質上」的推導邏輯會導致它在最需要外部認證的情境（新使用者、多租戶）下回傳 `null`。

**修復核心**：將 `resolveLoginTenantId` 的邏輯從 **「查本地使用者」** 改為 **「查 Email Domain 對應表」** 或 **「讀取前端傳入的租戶識別碼」**，才能讓 Multi-Auth-Provider 真正接通。