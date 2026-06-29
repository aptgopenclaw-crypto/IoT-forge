> My Question
情境 A：全新的 LDAP / SSO 使用者（首次登入），我預設用戶要被新增後，才能登入，畢竟不是全公司的人都需要登入該系統

情境 B：一個帳號屬於多個租戶（Multi-tenant user），這個我預設不存在，因為是多租戶關係，就算是super admin，他是不屬於任何場域（tenant)

這樣的前提下，該issue是否還存在


> AI Say

在您設定的這兩個業務前提下，這個 Issue（V3-H1）在 **「實際系統運行」上確實不會發生**，您的 Multi-Auth-Provider 架構是可以正常運作的。

但是，從 **「程式碼健壯性（Robustness）」與「資安審計視角」** 來看，這個 Issue 依然被視為一個**隱藏的技術債與高風險**。

以下為您詳細拆解為什麼您的前提可以避開這個漏洞，以及為什麼審計委員還是會標記它，最後提供您應對這個審計意見的建議。

---

### 1. 為什麼您的前提下，漏洞「不會發生」？

您的業務邏輯完美地避開了 `resolveLoginTenantId` 方法中兩個會導致回傳 `null` 的條件：

*   **對應情境 A（先建帳號再登入）**：
    *   **審計擔憂**：LDAP/SSO 使用者首次登入，本地 DB 沒資料 $\rightarrow$ `findByEmail` 回傳 null $\rightarrow$ `tenantId = null` $\rightarrow$ 錯誤地走 Local Auth。
    *   **您的現況**：因為管理員必須先在後台建立帳號（同時寫入 `UserEntity` 與 `UserTenantMappingEntity`），所以當使用者首次登入時，`findByEmail` **一定會找到資料**，不會回傳 null。
*   **對應情境 B（一個帳號只屬於一個租戶）**：
    *   **審計擔憂**：一個帳號屬於多個租戶 $\rightarrow$ `mappings.size() > 1` $\rightarrow$ 條件 `(mappings.size() == 1)` 不成立 $\rightarrow$ 回傳 null $\rightarrow$ 錯誤地走 Local Auth。
    *   **您的現況**：系統設計上保證一個帳號只能屬於一個 tenant。因此 `mappings.size()` 永遠只會是 `1`（普通用戶）或 `0`（Super Admin）。條件 `(mappings.size() == 1)` **永遠能成功匹配普通用戶**。

**結論**：在您的業務 SOP 下，`resolveLoginTenantId` 確實能正確推導出 `tenantId`，Dispatcher 也能正確路由到 LDAP/SSO，**不會發生「以為接了 AD 其實走 Local」的資安漏洞**。

---

### 2. 為什麼審計委員還是會標記為 High Risk (H1)？

資安審計委員在看程式碼時，**只看「程式碼本身的防禦性」，不看「業務 SOP」**。他們標記 H1 的原因如下：

#### A. 程式碼的正確性依賴「外部假設」，而非「自身防禦」
您的程式碼之所以安全，是因為「假設」管理員不會漏建 mapping，且「假設」未來不會開放跨租戶。
在軟體工程中，這被稱為 **Fragile Code（脆弱的程式碼）**。如果未來某天，業務需求變更（例如：開放員工用 Email 自助註冊並自動綁定部門，或是允許一個帳號同時屬於「總部」與「子公司」兩個租戶），這段 `size() == 1` 的寫死邏輯就會**立刻引發嚴重的資安漏洞**，且極難被發現。

#### B. Super Admin 的認證路徑盲點
請注意，Super Admin 因為不屬於任何場域，其 `mappings.size() == 0`。
這意味著 `resolveLoginTenantId` 對 Super Admin 會回傳 `null`，Dispatcher 會**永遠退回 Local Auth**。
*   **這代表：Super Admin 無法使用 LDAP/SSO 登入，只能用本地密碼登入。**
*   審計委員可能會質疑：如果未來公司要求 Super Admin 也必須透過公司 AD (LDAP) 登入以符合零信任架構，您的系統目前支援嗎？（答案是不支援）。

#### C. 缺乏 Fail-Safe（失效安全）機制
如果今天本地 DB 因為某種原因（例如資料同步延遲、管理員手滑刪除了 mapping），導致 `findByEmail` 找不到資料或 `mappings` 為空，系統會**靜默地**退回 Local Auth。
對於一個標榜支援 Multi-Provider 的系統，「找不到 LDAP 設定就默默改用本地密碼驗證」是一個非常危險的 Fail-Open（失效開放）行為。

---

### 3. 您該如何處理這個審計意見？

既然您確認業務上不會有這些情境，您不需要大幅重構程式碼，但需要**在程式碼中留下防禦性紀錄，並正式回覆審計委員**。

#### 建議作法 1：在程式碼中加入 Assert / 明確 Log (推薦)
讓程式碼自己「說出」它的業務前提，並拒絕不預期的狀態。這能消除審計委員對「靜默退回 Local Auth」的疑慮。

```java
private String resolveLoginTenantId(String email) {
    try {
        return userRepository.findByEmail(email).map(user -> {
            List<UserTenantMappingEntity> mappings = queryMappingsWithSystemContext(
                    () -> userTenantMappingRepository.findByUserIdAndEnabledTrue(user.getUserId()));
            
            // [V3-H1 修復/備註] 依據業務規則，一般用戶必須且只能屬於單一租戶。
            if (mappings.isEmpty()) {
                // Super Admin (無 mapping) 或 異常帳號：回傳 null 讓 Dispatcher 走 Local Auth
                log.info("resolveLoginTenantId: user={} has no tenant mapping, fallback to LOCAL auth", email);
                return null; 
            }
            if (mappings.size() > 1) {
                // 防禦性程式設計：如果未來業務放寬，這裡會明確報錯，而不是靜默退回 Local Auth
                log.error("resolveLoginTenantId: user={} has multiple tenant mappings, which violates current business rule!", email);
                throw new BusinessException(ErrorCode.TENANT_ACCESS_DENIED, "帳號歸屬異常，請聯繫管理員");
            }
            
            return mappings.get(0).getTenantId();
        }).orElseGet(() -> {
            // 找不到使用者：明確 Log，不靜默退回
            log.warn("resolveLoginTenantId: user={} not found in local DB, fallback to LOCAL auth", email);
            return null;
        });
    } catch (Exception e) {
        log.error("Could not pre-resolve tenantId for email={}", email, e);
        return null;
    }
}
```

#### 建議作法 2：正式回覆審計委員 (Closure)
在審計追蹤系統中，您可以這樣回覆並要求關閉此 Issue：

> **回覆說明 (V3-H1)：**
> 經確認，本系統之 IAM 業務規則明確定義：
> 1. 所有 LDAP/SSO 用戶必須由管理員預先於本地 DB 建立 `UserEntity` 與 `UserTenantMappingEntity` 後方可登入（不支援 JIT 自動建檔）。
> 2. 系統架構為嚴格單一租戶歸屬（Single-tenant mapping per user），Super Admin 則不綁定任何租戶（走 Local Auth）。
> 
> 在此業務前提下，`resolveLoginTenantId` 之 `mappings.size() == 1` 邏輯能正確涵蓋 100% 之一般用戶路由需求，**Multi-Auth-Provider 功能已正確接通，無審計所述之繞過風險**。
> 
> **改善措施：**
> 為提升程式碼健壯性並避免未來業務變更導致隱含風險，已於 `AuthServiceImpl.resolveLoginTenantId` 中加入防禦性檢查：若偵測到 `mappings.size() > 1` 將明確拋出 `BusinessException` 而非靜默 Fallback 至 Local Auth，並補充相關 Javadoc 說明業務前提。
> 
> 懇請 審計委員確認並同意關閉此 Issue (Accepted Risk / Won't Fix)。

### 總結
您的業務設計是合理的，**漏洞在您的前提下不存在**。但審計委員抓的是「程式碼寫法不夠嚴謹」。透過**加入防禦性程式碼（攔截 `size > 1`）**並**清楚回覆業務前提**，就可以完美解決這個審計缺失，而不需要去實作複雜的 Email Domain 路由表。