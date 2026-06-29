這是一個非常經典且致命的 **「認證降級（Authentication Fallback）機制設計不良」** 所導致的 **「認證繞過（Authentication Bypass）」** 漏洞。

簡單來說：**當系統設定「必須使用外部認證（如 LDAP/AD）」時，如果外部認證失敗，系統會「好心地」自動降級改用「本地密碼」驗證。攻擊者可以利用這個機制，用資料庫裡殘留的舊本地密碼，繞過 LDAP 的限制強行登入。**

以下為您詳細拆解這個攻擊情境、程式碼盲點以及修復方案：

### 1. 攻擊情境還原（駭客是如何繞過的？）

假設您的系統有一個租戶 A（例如某政府機關），資安政策要求 **「所有員工必須使用公司 AD (LDAP) 登入，嚴禁使用本地密碼」**。
管理員在後台將租戶 A 的認證方式設為 `LDAP`。但是，因為系統剛上線時是用本地帳號建的，或者管理員在後台「新增用戶」時系統自動產生了暫存本地密碼，導致這些員工在 `user` 表中**依然存有 `passwordHash`（本地密碼）**。

**攻擊劇本：**
1. 駭客知道租戶 A 只能用 LDAP 登入，且知道目標員工的 Email。
2. 駭客在登入畫面輸入該員工的 Email，以及**一組隨便亂打的密碼**。
3. 系統將請求送給 `LdapAuthProvider`。因為密碼是亂打的，LDAP 伺服器回傳「認證失敗」，`LdapAuthProvider` 拋出 `BusinessException(ErrorCode.INVALID_CREDENTIAL)`。
4. **漏洞觸發點**：`AuthenticationDispatcher` 的 `catch` 區塊攔截到了 `BusinessException`。因為 `fallbackLocal` 預設為 `true`（或管理員怕 LDAP 掛掉而開啟了此選項），Dispatcher 決定「降級」到 `LocalAuthProvider` 再試一次。
5. `LocalAuthProvider` 拿駭客剛才輸入的密碼去比對資料庫裡的 `passwordHash`。
6. **如果駭客剛好知道該員工以前的本地密碼（或預設密碼），此時就會驗證成功，駭客順利登入系統！**

*註：審計意見提到「故意讓 LDAP timeout」也是一種路徑，但其實**只要「密碼錯誤」觸發 BusinessException 就足夠繞過了**，攻擊門檻極低。*

---

### 2. 程式碼盲點剖析

請看您提供的 `AuthenticationDispatcher.java` 第 63-74 行：

```java
try {
    return provider.authenticate(request, decryptedConfig);
}
catch (BusinessException e) {
    // 🚨 盲點：這裡 catch 了「所有」 BusinessException，沒有區分失敗原因！
    if (authType != AuthType.LOCAL && config != null && Boolean.TRUE.equals(config.getFallbackLocal())) {
        log.warn("External auth ({}) failed for tenant {}, falling back to LOCAL", authType,
                request.getTenantId());
        AuthenticationProvider localProvider = providerMap.get(AuthType.LOCAL);
        if (localProvider != null) {
            return localProvider.authenticate(request, null); // 降級驗證
        }
    }
    throw e;
}
```

**核心問題在於「不區分例外類型」：**
*   **基礎設施錯誤（Infrastructure Error）**：例如 LDAP 伺服器斷線、連線超時、DNS 解析失敗。這種情況降級到 Local 是合理的（為了維持系統可用性）。
*   **憑證錯誤（Credential Error）**：例如帳號不存在、密碼錯誤、帳號被鎖定。這種情況**絕對不能降級**，否則就失去了強制使用外部認證的意義。

您的程式碼把這兩者混為一談，只要拋出 `BusinessException` 就一律降級，導致了資安漏洞。

---

### 3. 如何修復？（具體實作建議）

要解決這個問題，必須從「例外分級」、「預設值」與「監控」三個層面著手：

#### 步驟 1：區分例外類型（最關鍵的修復）
在您的 `BusinessException` 或 `ErrorCode` 中，必須明確區分「連線/基礎設施錯誤」與「認證失敗」。
只有基礎設施錯誤才允許 fallback。

```java
// 建議在 ErrorCode 或 Exception 中定義一個方法來判斷是否為可降級的錯誤
// 例如：在 BusinessException 中加入 isInfrastructureError() 方法
// 或者在 Dispatcher 中直接判斷 ErrorCode

catch (BusinessException e) {
    // 👇 只有「基礎設施錯誤」才允許 fallback，「憑證錯誤」絕對禁止
    boolean isInfraError = isInfrastructureError(e); 
    
    if (isInfraError && authType != AuthType.LOCAL && config != null && Boolean.TRUE.equals(config.getFallbackLocal())) {
        SecurityLogger.warn(SecurityEvent.AUTH_FALLBACK, request.getIdentifier(), 
            "External auth (" + authType + ") infrastructure failed for tenant " + request.getTenantId() + ", falling back to LOCAL. Reason: " + e.getMessage());
        
        AuthenticationProvider localProvider = providerMap.get(AuthType.LOCAL);
        if (localProvider != null) {
            return localProvider.authenticate(request, null);
        }
    }
    // 如果是密碼錯誤 (INVALID_CREDENTIAL) 或其他業務錯誤，直接拋出，不降級
    throw e; 
}

// 輔助方法：判斷是否為基礎設施錯誤
private boolean isInfrastructureError(BusinessException e) {
    ErrorCode code = e.getErrorCode();
    // 只有這類錯誤才允許 fallback
    return code == ErrorCode.PROVIDER_UNAVAILABLE 
        || code == ErrorCode.LDAP_CONNECTION_TIMEOUT 
        || code == ErrorCode.LDAP_SERVER_ERROR;
    // 注意：INVALID_CREDENTIAL, ACCOUNT_LOCKED, USER_NOT_FOUND 等絕對不能包含在內！
}
```

#### 步驟 2：修改預設值 (對應審計建議 a)
在建立 `TenantAuthConfigEntity` 的邏輯中，或是資料庫 Migration 中，將 `fallbackLocal` 的預設值改為 `false`。
*   **好處**：遵循「最小權限/最嚴格預設」原則。管理員必須「明確知道風險」並「主動勾選」才能開啟降級，避免無意間留下後門。

#### 步驟 3：強化監控與告警 (對應審計建議 c)
如上述程式碼所示，當觸發 Fallback 時，不能只用 `log.warn`，必須使用您系統中的 `SecurityLogger.warn` 記錄 `SecurityEvent.AUTH_FALLBACK`。
*   **目的**：讓 SOC（資安監控中心）或 SIEM 系統能夠設定告警規則。如果短時間內出現大量 `AUTH_FALLBACK` 事件，代表 LDAP 可能遭到攻擊（如 LDAP Injection 導致崩潰）或正在被嘗試繞過，SRE/資安人員可以立即介入。

### 總結
這是一個 **High Risk** 的邏輯漏洞。您的系統雖然提供了「禁用 Local 認證」的開關，但因為 Fallback 機制的設計缺陷，讓這個開關形同虛設。

**修復核心**：在 `catch (BusinessException e)` 中，**嚴格過濾只有「網路/伺服器連線失敗」才能觸發 Fallback**，任何「密碼錯誤/帳號異常」的例外都必須直接拒絕，才能徹底堵住這個繞過漏洞。