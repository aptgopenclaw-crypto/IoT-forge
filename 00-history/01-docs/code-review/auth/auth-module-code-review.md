# Auth 模組 Code Review

## 整體評價

架構清晰、安全性設計相當成熟。Spring Security + 無狀態 JWT + Redis 速率限制 + 雙通道 CAPTCHA（圖片驗證碼 / Cloudflare Turnstile）的組合是實務上合理的選擇。以下按安全性、正確性、可維護性三個維度展開。

---

### 優點 (值得肯定)

1. **速率限制涵蓋所有敏感端點**
   - **AuthController.java:78-121**
   - login 10/min、captcha 20/min、forgot-password 5/5min、reset-password 5/5min，有效防止暴力破解和郵件轟炸。

2. **忘記密碼靜默成功**
   - **AuthServiceImpl.java:525-530**
   - 使用者不存在時直接 return，防止帳號列舉攻擊。

3. **Refresh token 輪替 + 型別驗證**
   - **AuthServiceImpl.java:368-371**
   - 每個 access token 刷新同時發新 refresh token，並驗證 JWT 中 `type: "refresh"` 欄位，防止 access token 被冒用為 refresh token。

4. **帳號鎖定與自動解鎖**
   - **AuthServiceImpl.java:119-134**
   - 可設定的失敗次數上限和鎖定時間，鎖定期滿後自動解鎖。

5. **Refresh token 存入 HttpOnly Cookie**
   - **AuthController.java:201-212**
   - 防止 XSS 攻擊竊取 refresh token，且 SameSite、Secure、Domain 均可設定。

6. **SecurityLogger CRLF 注入防護**
   - **SecurityLogger.java:48-56**
   - 對所有寫入日誌的外部輸入做 \r\n\t 過濾，防止 CWE-117 日誌注入。

7. **RateLimitInterceptor IP 來源安全**
   - **RateLimitInterceptor.java:126-129**
   - 使用 `request.getRemoteAddr()`（TCP 連線層 IP）而非可偽造的 `X-Forwarded-For` header，防止攻擊者透過變換 header 值繞過速率限制。

8. **TenantContext ThreadLocal 清理**
   - **JwtAuthenticationFilter.java:97-103**
   - 在 finally 區塊中清除，防止 Tomcat 執行緒池重用導致租戶 ID 殘留到下一個請求。

9. **原生 SQL 中的租戶隔離**
   - **AuthServiceImpl.java:679-682, 739-742**
   - `resolveDeptName` 和 `batchResolveDeptNames` 的 SQL 都有 `AND d.tenant_id = :tenantId` 條件，防止跨租戶資料洩漏。程式碼中註解標記了 [安全修復] 說明這是後來補上的。

10. **Security Headers 完整**
    - **SecurityConfig.java:43-65**
    - HSTS (1年)、CSP、Referrer-Policy、Permissions-Policy、X-Frame-Options、X-Content-Type-Options 全部設定。

11. **測試覆蓋率好**
    - **AuthControllerTest.java, AuthServiceTest.java**
    - 登入成功/失敗、多租戶、帳號鎖定/停用、select tenant、switch tenant、refresh token、忘記/重設密碼等主要流程都有測試。

---

### 需要改進的問題

1. **[中等] ChangePasswordRequest 缺少驗證標註**
   - **auth/dto/request/ChangePasswordRequest.java:14-17**
   - `oldPassword` 和 `newPassword` 欄位沒有任何 `@NotBlank` 驗證標註。
   - 建議加上：

     ```java
     @NotBlank(message = "oldPassword is required")
     private String oldPassword;

     @NotBlank(message = "newPassword is required")
     private String newPassword;
     ```

2. **[中等] 密碼重設 token 明文儲存**
   - **UserResetPasswordTokenEntity.java:32**
   - token 欄位存的是原始 UUID 明文。若資料庫被攻破，攻擊者可以直接拿 token 重設任意使用者的密碼。
   - 建議改為雜湊儲存，並修改比對方式：

     1. 生成 token → 回傳給使用者。
     2. 後端儲存 `hash(token)`。
     3. 使用者提交原始 token → 後端對所有未過期 token 逐一 `BCrypt.matches(plainToken, storedHash)`。

3. **[中等] Refresh token 無伺服器端撤銷機制**
   - **AuthServiceImpl.java:433-435**
   - `logout()` 方法是空實作。
   - 建議：在 Redis 中維護一個 token 黑名單，或引入 token family/version 機制。

4. **[低] selectTenant 和 switchTenant 程式碼幾乎重複**
   - **AuthServiceImpl.java:228-352**
   - 可提取為私有方法 `generateTenantToken(userId, request, eventType, httpRequest)`。

5. **[低] Super admin 在各方法中的分支邏輯重複**
   - **login() (line 165-179)**、**selectTenant() (line 236-264)**、**switchTenant() (line 302-323)** 等。
   - 考慮抽取為 `handleSuperAdminToken()` 或使用 Strategy 模式。

6. **[低] resolvePermissions() 使用原生 SQL**
   - **AuthServiceImpl.java:656-672**
   - 建議在 SQL 中加入參數化查詢的註解說明，並考慮是否可以用 JPQL 搭配 explicit tenant filter。

7. **[低] 日誌中洩漏部分重設 token**
   - **AuthServiceImpl.java:547-548**
   - 建議改為記錄一個完全不相關的 operation ID，或直接不記錄 token 的任何部分。

8. **[低] SelectTenantRequest 和 SwitchTenantRequest 完全相同**
   - 可合併為一個 `TenantSelectionRequest`，或者讓一個繼承另一個。

9. **[建議] changePassword() 缺少速率限制**
   - **AuthServiceImpl.java:502-521**
   - 建議在 `UserSelfController` 的 change-password 端點加入 `@RateLimit`。

10. **[建議] 密碼重設 token 的 delete 操作應在驗證成功前執行**
    - **CaptchaServiceImpl.java:74-75**
    - 建議先標記 token 為 used，再進行密碼驗證。

---

## 架構總評

| 維度   | 評分    | 說明                                                                                     |
|--------|---------|------------------------------------------------------------------------------------------|
| 安全性 | 8.5/10  | BCrypt、HttpOnly cookie、速率限制、CAPTCHA、CRLF 防護、安全 headers 都到位。主要扣分在 token 明文儲存和缺少 refresh token 撤銷。 |
| 正確性 | 8/10    | 核心流程（登入、多租戶選擇、權限解析）邏輯正確，邊界案例有處理。原生 SQL 部分需要持續關注租戶隔離。                     |
| 可維護性 | 7.5/10 | DTO/Entity/Repository 分層清晰，但 Service 層程式碼重複較多（super admin 分支、select/switch tenant）。測試覆蓋率不錯但可以補更多邊界案例。 |
| 可觀測性 | 8/10   | SecurityLogger 統一安全事件、AuditEvent 審計日誌、結構化 key=value 格式。日誌層級使用恰當（warn for security events, info for normal operations）。 |
