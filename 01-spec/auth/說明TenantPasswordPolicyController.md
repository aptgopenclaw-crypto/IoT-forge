這個 `TenantPasswordPolicyController` 是**租戶管理員 (TENANT_ADMIN)** 專用的密碼策略管理控制器，負責在平台設定的安全底線之上，管理個別租戶的密碼策略覆寫值。

它與 `PlatformPasswordPolicyController`（平台級）和 `NoauthPasswordPolicyController`（公開查詢級）共同構成了完整的**三層密碼策略體系**。

---

### 1. 核心定位：三層體系中的「中間層」

| 層級 | Controller | 權限要求 | 職責 |
|------|-----------|---------|------|
| **平台層** | `PlatformPasswordPolicyController` | `PLATFORM_PASSWORD_POLICY_MANAGE` | 設定全域安全底線 (Floor) |
| **租戶層** | `TenantPasswordPolicyController` (本類) | `PASSWORD_POLICY_MANAGE` | 在底線之上設定更嚴格的覆寫值 |
| **公開層** | `NoauthPasswordPolicyController` | 無需登入 | 前端登入/重設密碼頁面查詢最終生效規則 |

本類處於中間層，其核心約束是 **Spec D-4**：租戶只能**加強**（不能弱化）平台設定的密碼策略下限。

---

### 2. API 端點詳細說明

基礎路徑：`/v1/auth/password-policy`

#### A. 查詢最終生效策略 (公開給已登入使用者)
* **`GET /v1/auth/password-policy`**
* **權限**：仅需 `authenticated()`（無 `@PreAuthorize`），任何已登入使用者都可查詢。
* **用途**：讓使用者在修改密碼前，能看到自己租戶的最終密碼規則（租戶覆寫 ∪ 平台預設的合併結果）。
* **底層邏輯**：呼叫 `policyService.getEffective(TenantContext.getCurrentTenantId())`，Resolver 會先讀取租戶覆寫值，再與平台預設值合併。

#### B. 查詢租戶原始覆寫值 (管理員專用)
* **`GET /v1/auth/password-policy/tenant`**
* **權限**：`@PreAuthorize("hasAuthority('PASSWORD_POLICY_MANAGE')")`
* **用途**：供管理後台顯示哪些規則已被租戶自定義（UI 上通常會顯示「已自訂」徽章 badge），以便與平台預設值進行對比。
* **回傳**：`Map<String, String>`，僅包含該租戶有覆寫的鍵值對。

#### C. 新增/更新租戶覆寫值 (管理員專用)
* **`PUT /v1/auth/password-policy/tenant`**
* **權限**：`PASSWORD_POLICY_MANAGE` + `@AuditEvent`
* **核心安全機制**：底層呼叫 `policyService.updateTenantOverride()`，在寫入前會：
  1. 驗證 Key 是否合法 (`PasswordPolicyKey.fromKey`)
  2. 驗證 Value 格式是否正確（INT 或 BOOL）
  3. **強制執行平台下限**：如果租戶提出的值低於平台底線，直接拋出 `PASSWORD_POLICY_BELOW_PLATFORM_MINIMUM` 異常

#### D. 刪除租戶覆寫值 (管理員專用)
* **`DELETE /v1/auth/password-policy/tenant/{key}`**
* **權限**：`PASSWORD_POLICY_MANAGE` + `@AuditEvent`
* **用途**：移除指定 key 的租戶覆寫，刪除後該 key 將自動回退 (fallback) 到平台預設值。
* **參數驗證**：`@PathVariable @NotBlank String key`，配合 class 級別的 `@Validated`，確保 path variable 不會是空字串。

---

### 3. 關鍵設計亮點

#### 多租戶隔離機制
```java
TenantContext.getCurrentTenantId()
```
所有端點都不需要前端手動傳遞 `tenantId`，而是從 `TenantContext`（通常由 `JwtAuthenticationFilter` 在解析 JWT 後寫入 ThreadLocal）中自動取得。這確保了：
* **防篡改**：租戶管理員無法透過修改請求參數來操作其他租戶的策略。
* **零洩漏**：每個租戶只能看到和修改自己的覆寫值。

#### Swagger/OpenAPI 整合
使用 `@Tag` 和 `@Operation` 註解提供完整的 API 文件，包含 `summary`（簡短摘要）和 `description`（詳細說明），方便前端開發者理解每個端點的用途。

#### 讀寫權限分離
* `GET /`（查詢生效策略）：任何已登入使用者都可存取 → 使用者修改密碼前需要知道規則。
* `GET/PUT/DELETE /tenant`（管理覆寫）：僅限 `PASSWORD_POLICY_MANAGE` → 防止一般使用者篡改策略。

---

### 4. 在 SecurityConfig 中的授權路徑

值得注意的是，`SecurityConfig` 中**沒有**為 `/v1/auth/password-policy/**` 設定明確的 URL 級別規則。因此：
* 所有端點都會落入最後的 `.anyRequest().authenticated()` 規則 → **必須持有有效的 Access Token**。
* 細部權限則由 Controller 上的 `@PreAuthorize` 進行方法級別控管。

這是一種**安全的預設行為**：即使忘記在 `SecurityConfig` 中新增 URL 規則，也不會有未授權存取的风险。

---

### 💡 潛在優化建議

#### 1. 建議在 SecurityConfig 中新增明確的 URL 規則
雖然目前 `.anyRequest().authenticated()` 能兜底，但為了**可讀性與一致性**（其他模組如 roles、menus、dept 都有明確列出），建議在 `SecurityConfig` 中補充：
```java
// PASSWORD POLICY: tenant-scope (細部由 @PreAuthorize 控制)
.requestMatchers("/v1/auth/password-policy/**").authenticated()
```

#### 2. `GET /tenant` 是否需要同時加上 `@PreAuthorize` 的 URL 級別防護？
目前 `GET /tenant` 僅靠 `@PreAuthorize` 保護。如果有人重構時不慎移除該註解，該端點將降級為「任何已登入使用者都能查看租戶的密碼策略覆寫值」，雖非高危但屬資訊洩漏。加上 URL 級別規則可提供**雙重防護 (Defense in Depth)**。

---

### 總結
`TenantPasswordPolicyController` 是一個職責清晰、安全嚴謹的租戶級管理控制器。它透過 `TenantContext` 實現自動租戶隔離，透過 `@PreAuthorize` 實現讀寫權限分離，並依賴 `PasswordPolicyService` 的下限強制執行機制，確保了「租戶永遠無法弱化平台安全底線」這一核心不變量 (Invariant)。