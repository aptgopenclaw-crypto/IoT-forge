經過詳細的 Code Review，這兩個檔案的整體架構非常成熟，具備很高的安全意識（如雙 Token 機制、嚴格的 Security Headers、速率限制等）。

但在**安全防護的連貫性**、**邊界條件處理**以及**Servlet API 的相容性**上，仍有幾個潛在的 Issue 需要修正。以下為您整理出具體的問題與建議：

---

### 🚨 高風險 / 安全漏洞 (Critical / High)

#### 1. CSRF 防護形同虛設 (致命衝突)
* **問題位置**：`SecurityConfig.java` 第 58 行 `.csrf(csrf -> csrf.disable())` 與 `AuthController.java` 依賴 Cookie 的端點。
* **說明**：您在 `AuthController` 中將 `refresh_token` 存放在 **HttpOnly Cookie** 中，這是非常好的做法（防 XSS）。**但是**，瀏覽器在發送跨站請求時，**會自動帶上 Cookie**。
  由於您在 `SecurityConfig` 中完全禁用了 CSRF (`csrf.disable()`)，這導致以下端點暴露在 **CSRF (跨站請求偽造) 攻擊**之下：
  * `POST /v1/noauth/token/refresh`
  * `POST /v1/auth/logout`
  * `POST /v1/auth/idle-logout`
* **攻擊場景**：攻擊者可以建構一個惡意網頁，誘導已登入的受害者點擊。瀏覽器會自動帶上受害者的 `refresh_token` Cookie 發送 POST 請求，攻擊者可藉此不斷刷新受害者的 Token，或惡意強制受害者登出 (Logout CSRF)。
* **解決建議**：
  * **方案 A (推薦)**：既然使用了 Cookie 承載狀態，就**不能**完全 `csrf.disable()`。請針對這些依賴 Cookie 的 state-changing 請求啟用 CSRF 防護（例如使用 Spring Security 預設的 `CsrfTokenRepository`，或實作 Double Submit Cookie 機制）。
  * **方案 B**：如果您堅持要 `csrf.disable()`，您自定義的 `csrfCookieFilter` 必須在**每一個**依賴 Cookie 的 POST 請求中，嚴格驗證 `Origin` 或 `Referer` Header 是否與伺服器網域完全匹配。請檢查 `csrfCookieFilter` 是否真的實作了這個驗證邏輯，而不僅僅是「產生 Cookie」。

---

### ⚠️ 中風險 / 邏輯與邊界條件 (Medium)

#### 2. `doLogout` 缺乏 Null 檢查 (潛在 NPE)
* **問題位置**：`AuthController.java` 第 154 行 `doLogout` 方法。
* **說明**：
  ```java
  private void doLogout(String refreshToken, HttpServletResponse httpResponse) {
      authService.logout(refreshToken); // 如果 refreshToken 為 null 會怎樣？
      clearRefreshTokenCookie(httpResponse);
  }
  ```
  當前端呼叫 `logout` 時，如果 Cookie 已經被清除或不存在，`@CookieValue(required = false)` 會傳入 `null`。如果 `authService.logout(null)` 內部沒有處理 null，將會拋出 `NullPointerException`。
* **解決建議**：在呼叫 Service 前加上 Null 檢查：
  ```java
  private void doLogout(String refreshToken, HttpServletResponse httpResponse) {
      if (refreshToken != null && !refreshToken.isBlank()) {
          authService.logout(refreshToken);
      }
      clearRefreshTokenCookie(httpResponse);
  }
  ```

#### 3. `getCurrentUserId()` 的 Principal 轉型風險
* **問題位置**：`AuthController.java` 第 188 行。
* **說明**：
  ```java
  return auth.getPrincipal().toString();
  ```
  這行程式碼假設 `JwtAuthenticationFilter` 將 `Principal` 設定為「UserID 的字串」。但如果 Filter 存入的是自定義的 `UserDetails` 物件或 `JwtUser` 物件，`toString()` 可能會回傳物件的記憶體位址（如 `com.example.User@1a2b3c`）或預設字串，導致後續業務邏輯拿到錯誤的 ID。
* **解決建議**：確保型別安全，明確轉型或取得 Username：
  ```java
  // 如果 Principal 是 String
  return (String) auth.getPrincipal(); 
  
  // 如果 Principal 是 UserDetails
  // return ((UserDetails) auth.getPrincipal()).getUsername();
  ```

#### 4. Cookie 設定的 Servlet 容器相容性
* **問題位置**：`AuthController.java` 第 204 行 `cookie.setAttribute("SameSite", cookieSameSite);`
* **說明**：雖然 `jakarta.servlet.http.Cookie` 在 Servlet 5.0+ 支援 `setAttribute`，但在某些舊版的 Tomcat 或特定的 Servlet 容器中，使用 `jakarta.servlet.http.Cookie` 設定 `SameSite` 可能**無法正確序列化**到 HTTP Response 的 `Set-Cookie` Header 中。
* **解決建議**：建議改用 Spring 提供的 `ResponseCookie`，它能確保跨容器的絕對相容性與正確的 Header 輸出：
  ```java
  private void setRefreshTokenCookie(HttpServletResponse response, String refreshToken) {
      ResponseCookie cookie = ResponseCookie.from("refresh_token", refreshToken)
          .httpOnly(true)
          .secure(cookieSecure)
          .path(cookiePath)
          .maxAge(cookieMaxAge)
          .sameSite(cookieSameSite)
          .domain(cookieDomain != null && !cookieDomain.isEmpty() ? cookieDomain : null)
          .build();
      response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
  }
  ```

---

### 💡 低風險 / 設計與維護性 (Low)

#### 5. `extractJtiSafely` 吞沒異常導致前端狀態判斷模糊
* **問題位置**：`AuthController.java` 第 176 行。
* **說明**：當 `refreshToken` **已過期**時，`jwtUtil.parseToken` 會拋出 `ExpiredJwtException`。目前的 `catch (Exception e)` 會將其吞沒並回傳 `null`。
  這會導致前端在呼叫 `GET /v1/auth/sessions` 時，無法區分「使用者沒有 Token」和「使用者的 Token 已過期」。如果 Token 已過期，`currentJti` 為 `null`，前端可能無法正確在列表中標記「當前裝置」。
* **解決建議**：如果業務上需要區分，可以只 catch 特定異常，或讓過期的 Token 依然能解析出 `jti`（有些 JWT 庫允許在過期時仍讀取 Claims）。若目前前端邏輯可接受此行為，則可維持現狀，但建議在註解中明確說明此設計取捨。

#### 6. `forceChangePassword` 手動解析 Header 的潛在衝突
* **問題位置**：`AuthController.java` 第 119 行。
* **說明**：該端點 `/v1/noauth/user/force-change-password` 屬於 `/v1/noauth/**`，在 `SecurityConfig` 中是 `permitAll()`。
  這裡手動從 `Authorization` Header 提取了一個「短期的 password-change token」。需要確認 `JwtAuthenticationFilter` 的邏輯：當它遇到這個非標準的 Bearer Token 時，是**直接忽略並放行**，還是**因為驗證失敗而中斷 Filter Chain 回傳 401**？
  如果 Filter 會拋出 401，這個端點就永遠無法被成功呼叫。
* **解決建議**：確認 `JwtAuthenticationFilter` 在解析 Token 失敗時，是否會呼叫 `filterChain.doFilter()` 繼續往下走。如果是，則沒問題；如果會拋出異常，則需要調整 Filter 邏輯或將此端點移出 JWT Filter 的攔截範圍。

### 總結
您的程式碼品質很高，最迫切需要處理的是 **Issue 1 (CSRF 防護)**。只要確認 `csrfCookieFilter` 有實作嚴格的 Origin/Referer 檢查，或者改用標準的 CSRF Token 機制，這個架構就會非常堅固。其他 Issue 則可安排在接下來的 Sprint 中逐步優化。