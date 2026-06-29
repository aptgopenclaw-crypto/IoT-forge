# 密碼策略（Password Policy）規格書

> 版本：v1.0 · 2026-05-27
> 對齊參考：Keycloak Password Policies
> 範圍：後端 + 資料庫 + REST API + 前端設定頁
> 適用模組：`auth`、`user`、`setting`

---

## 1. 目標與設計原則

讓**租戶管理者**（Tenant Admin）在 UI 上設定本租戶的密碼規則；同時保留**平台預設規則（Platform Default）**作為下限。

### 1.1 核心設計決策（已敲定）

| # | 決策 | 說明 |
|---|------|------|
| D-1 | **平台預設 + 租戶可覆寫**（兩層繼承） | tenant 查不到 setting 時 fallback 到平台預設；平台預設由 SUPER_ADMIN 維護 |
| D-2 | **舊密碼不阻擋登入** | 新規則上線後，原有密碼即使不符新規則也仍可登入；只在「改密 / 重設 / 新建使用者」時驗證新規則 |
| D-3 | **過期規則上線時 `password_changed_at` 預設為 NOW()** | 避免全員被瞬間踢出 |
| D-4 | **平台只能設「下限」** | 例如平台規定 `min_length ≥ 8`，租戶只能設 ≥ 8、不能再放寬 |
| D-5 | **快取** | 走 Caffeine cache（TTL 60s），對齊現有 `TenantEnabledCache` 模式，避免登入熱路徑每次 DB query |
| D-6 | **平台預設儲存方式** | 使用保留 sentinel `tenant_id = '__PLATFORM__'` 存於同一張 `system_settings` 表 |

### 1.2 非目標（Out of Scope，留待後續）

- 密碼黑名單字典（HaveIBeenPwned 整合）
- 自訂正則（`password.regex_pattern`）
- 最短密碼存活期（`min_password_age_days`，避免使用者快速循環密碼以繞過 history）
- 密碼過期 e-mail 提前提醒（`expire_warning_days`）

---

## 2. 現況盤點

### 2.1 已具備

| 能力 | 位置 | 狀態 |
|------|------|------|
| 多租戶 setting 表 | [system_settings](backend/src/main/resources/db/migration/) + [SystemSettingEntity](backend/src/main/java/com/taipei/iot/setting/entity/SystemSettingEntity.java) | ✅ 已 tenant-scoped（`@Filter tenant_id`） |
| 密碼複雜度驗證 | [PasswordValidator](backend/src/main/java/com/taipei/iot/user/service/PasswordValidator.java) | ✅ 有 min_length / uppercase / lowercase / digit / special |
| 密碼歷史 | [PasswordHistoryRepository](backend/src/main/java/com/taipei/iot/user/repository/PasswordHistoryRepository.java) | 🟡 `findTop5...` 寫死 5 筆，需動態化 |
| Setting CRUD API | [SystemSettingController](backend/src/main/java/com/taipei/iot/setting/controller/SystemSettingController.java) | ✅ 已有 GET / PUT，可複用 |
| RBAC | `TENANT_ADMIN`、`SUPER_ADMIN` role | ✅ 已就緒 |

### 2.2 缺口

| 缺口 | 影響 phase |
|------|-----------|
| `SettingKey` enum 只有 2 個 key（idle_timeout、frontend_base_url） | Phase 1 |
| `PasswordValidator` 規則來源是 `@Value`（全域 yml），需改為動態讀 tenant setting | Phase 1 |
| 沒有「平台預設 vs 租戶覆寫」的解析層 | Phase 1 |
| 沒有設定快取 | Phase 1 |
| 沒有 `user.password_changed_at` 欄位 | Phase 3 |
| 沒有 `user.force_change_password` 欄位 | Phase 3 |
| 沒有密碼過期檢查與強制改密流程 | Phase 3 |
| 沒有「不可包含 username/email」驗證 | Phase 2 |

---

## 3. 規則清單（對齊 Keycloak）

### 3.1 Phase 1：基礎複雜度（**MVP**）

| Setting Key | 型別 | 預設值 | 平台下限 | 說明 |
|---|---|---|---|---|
| `password.min_length` | int | 8 | 8 | 密碼最小長度 |
| `password.require_uppercase` | bool | true | — | 必須含大寫英文 |
| `password.require_lowercase` | bool | true | — | 必須含小寫英文 |
| `password.require_digit` | bool | true | — | 必須含數字 |
| `password.require_special` | bool | true | — | 必須含特殊字元 |
| `password.history_count` | int | 5 | 1 | 不得重複前 N 次密碼（0 = 關閉） |

### 3.2 Phase 2：進階複雜度

| Setting Key | 型別 | 預設值 | 平台下限 | 說明 |
|---|---|---|---|---|
| `password.max_length` | int | 128 | 64 | 密碼最大長度（防 DoS） |
| `password.min_special_chars` | int | 1 | 1 | 至少 N 個特殊字元 |
| `password.min_digits` | int | 1 | 1 | 至少 N 個數字 |
| `password.min_uppercase` | int | 1 | 1 | 至少 N 個大寫 |
| `password.min_lowercase` | int | 1 | 1 | 至少 N 個小寫 |
| `password.not_contains_username` | bool | true | — | 不可包含 username 或 email local-part |

### 3.3 Phase 3：過期與強制改密

| Setting Key | 型別 | 預設值 | 平台下限 | 說明 |
|---|---|---|---|---|
| `password.expire_days` | int | 90 | — | 密碼有效天數（0 = 永不過期） |
| `password.force_change_on_first_login` | bool | true | — | 首次登入強制改密 |
| `password.force_change_on_admin_reset` | bool | true | — | 管理者重設後強制改密 |

---

## 4. 資料庫設計

### 4.1 Phase 1 - 新增 `system_settings` 內容（**無 schema 變更**）

直接使用既有 `system_settings` 表，新增 setting key。**平台預設**使用 sentinel `tenant_id = '__PLATFORM__'` 儲存於同表。

```sql
-- V44__auth__password_policy_platform_defaults.sql（範例）
INSERT INTO system_settings (tenant_id, setting_key, setting_value, description, created_at, updated_at)
VALUES
  ('__PLATFORM__', 'password.min_length',         '8',    '平台預設：密碼最小長度', NOW(), NOW()),
  ('__PLATFORM__', 'password.require_uppercase',  'true', '平台預設：須含大寫',   NOW(), NOW()),
  ('__PLATFORM__', 'password.require_lowercase',  'true', '平台預設：須含小寫',   NOW(), NOW()),
  ('__PLATFORM__', 'password.require_digit',      'true', '平台預設：須含數字',   NOW(), NOW()),
  ('__PLATFORM__', 'password.require_special',    'true', '平台預設：須含特殊字元', NOW(), NOW()),
  ('__PLATFORM__', 'password.history_count',      '5',    '平台預設：歷史保留',   NOW(), NOW());
```

> **影響**：既有 `SystemSettingService` 的 `tenantFilter` 會擋掉 `__PLATFORM__` 列，因此需新增 `PolicyResolver` 直接走 `EntityManager` 跳過 filter 讀取（見 §5.3）。

### 4.2 Phase 3 - `user` 表擴充

```sql
-- V46__auth__password_expiry_columns.sql
ALTER TABLE "user"
  ADD COLUMN password_changed_at TIMESTAMP,
  ADD COLUMN force_change_password BOOLEAN NOT NULL DEFAULT FALSE;

-- D-3：避免全員瞬間過期
UPDATE "user" SET password_changed_at = NOW() WHERE password_changed_at IS NULL;

ALTER TABLE "user" ALTER COLUMN password_changed_at SET NOT NULL;

CREATE INDEX idx_user_password_changed_at ON "user" (password_changed_at);
```

維護點：
- `AuthServiceImpl.resetPassword()` 完成 → `password_changed_at = NOW()`、`force_change_password = false`
- `UserSelfService.changePassword()` 完成 → 同上
- `UserAdminService.createUser()` → `password_changed_at = NOW()`、`force_change_password = true`（依 `force_change_on_first_login` 規則）
- `UserAdminService.resetPassword()` → `force_change_password = true`（依 `force_change_on_admin_reset` 規則）

---

## 5. 後端設計

### 5.1 模組結構

```
backend/src/main/java/com/taipei/iot/auth/
├── policy/
│   ├── PasswordPolicy.java                  # immutable value object
│   ├── PasswordPolicyResolver.java          # 介面：tenantId → PasswordPolicy
│   ├── PasswordPolicyResolverImpl.java      # 實作：__PLATFORM__ + tenant 合併
│   ├── PasswordPolicyCache.java             # Caffeine cache 包裝（TTL 60s）
│   └── PasswordPolicyKey.java               # setting key 常數 enum
└── controller/
    └── PasswordPolicyController.java        # GET /v1/auth/password-policy（公開讀，給 reset 頁顯示規則）

backend/src/main/java/com/taipei/iot/user/service/
└── PasswordValidator.java                   # 改為 validate(tenantId, password, userContext)
```

### 5.2 `PasswordPolicy` value object

```java
@Value
@Builder
public class PasswordPolicy {
    int minLength;
    int maxLength;
    boolean requireUppercase;
    boolean requireLowercase;
    boolean requireDigit;
    boolean requireSpecial;
    int minSpecialChars;
    int minDigits;
    int minUppercase;
    int minLowercase;
    boolean notContainsUsername;
    int historyCount;
    int expireDays;
    boolean forceChangeOnFirstLogin;
    boolean forceChangeOnAdminReset;

    /** 給前端「密碼提示文字」用。 */
    public List<String> describe();
}
```

### 5.3 `PasswordPolicyResolver` 解析順序

```
租戶值 (tenant_id = X) ──存在──▶ 採用
                       └─不存在─▶ 平台預設 (tenant_id = '__PLATFORM__') ──存在──▶ 採用
                                                                       └─不存在─▶ 程式硬編碼預設
```

實作要點：
- **必須繞過 `tenantFilter`**：使用 `entityManager.unwrap(Session.class).disableFilter("tenantFilter")` 或開新 `Repository` method 用原生 SQL；建議走 `PasswordPolicyRepository` 自訂 query 隔離。
- **驗證平台下限**：寫入租戶 setting 時，若某 key 有下限規則（見 §3 表），且租戶值放寬到低於平台下限 → 拋 `BusinessException(PASSWORD_POLICY_BELOW_PLATFORM_MINIMUM)`。

### 5.4 `PasswordValidator` 簽名變更

```java
// 變更前：依賴 @Value，無 tenant context
public void validate(String newPassword);

// 變更後：tenant-aware，可選 userContext（用於 not_contains_username）
public void validate(String tenantId, String newPassword, @Nullable UserContext userContext);

// userContext 含 username + email，供 password.not_contains_username 用
public record UserContext(String username, String email) {}
```

呼叫點需同步修改：
- [AuthServiceImpl.resetPassword](backend/src/main/java/com/taipei/iot/auth/service/impl/AuthServiceImpl.java)
- `UserSelfController.changePassword`（透過 service 層）
- `UserAdminService.createUser` / `resetPassword`

### 5.5 快取

```java
@Component
@RequiredArgsConstructor
public class PasswordPolicyCache {
    private final PasswordPolicyResolverImpl delegate;
    private final Cache<String, PasswordPolicy> cache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofSeconds(60))
            .maximumSize(500)
            .build();

    public PasswordPolicy get(String tenantId) {
        return cache.get(tenantId == null ? "__PLATFORM__" : tenantId,
                k -> delegate.resolve(k));
    }

    /** 寫入新設定時呼叫。 */
    public void evict(String tenantId) { cache.invalidate(tenantId); }
}
```

### 5.6 密碼過期檢查（Phase 3）

新增 `PasswordExpiryChecker`：

```java
@Component
@RequiredArgsConstructor
public class PasswordExpiryChecker {
    private final PasswordPolicyCache policyCache;

    public PasswordExpiryStatus check(UserEntity user, String tenantId) {
        if (Boolean.TRUE.equals(user.getForceChangePassword())) {
            return PasswordExpiryStatus.FORCE_CHANGE;
        }
        PasswordPolicy p = policyCache.get(tenantId);
        if (p.getExpireDays() <= 0) return PasswordExpiryStatus.OK;
        LocalDateTime expireAt = user.getPasswordChangedAt().plusDays(p.getExpireDays());
        return LocalDateTime.now().isAfter(expireAt)
                ? PasswordExpiryStatus.EXPIRED : PasswordExpiryStatus.OK;
    }
}

public enum PasswordExpiryStatus { OK, EXPIRED, FORCE_CHANGE }
```

整合於 `AuthServiceImpl.login()`：
- `OK` → 正常發 token
- `EXPIRED` / `FORCE_CHANGE` → 發**臨時 token**（`type=temporary`，TTL 5 分鐘），回 `LoginResult.passwordChangeRequired = true`，前端跳轉強制改密頁

> **D-2 落實**：登入流程的密碼比對仍走 `passwordEncoder.matches`，不呼叫 `PasswordValidator.validate`，因此舊密碼仍可登入。只有 expiry / force_change 才會觸發強制改密。

---

## 6. REST API

### 6.1 平台設定（SUPER_ADMIN）

| Method | Path | 說明 |
|---|---|---|
| `GET` | `/v1/platform/password-policy` | 取得平台預設規則 |
| `PUT` | `/v1/platform/password-policy` | 更新平台預設（驗證每個 key 自有的硬下限） |

### 6.2 租戶設定（TENANT_ADMIN）

| Method | Path | 說明 |
|---|---|---|
| `GET` | `/v1/auth/password-policy` | 取得**有效**規則（merged: tenant ∪ platform default） |
| `GET` | `/v1/auth/password-policy/tenant` | 取得「租戶層覆寫值」（未合併，供設定頁顯示「未覆寫 / 已覆寫」狀態） |
| `PUT` | `/v1/auth/password-policy/tenant` | 更新租戶覆寫（驗證 ≥ 平台下限） |
| `DELETE` | `/v1/auth/password-policy/tenant/{key}` | 刪除單一覆寫（回退到平台預設） |

### 6.3 公開端點（給 reset password 頁顯示「密碼必須符合 ...」）

| Method | Path | 說明 |
|---|---|---|
| `GET` | `/v1/noauth/password-policy/describe` | 依 `?tenantId=` 回傳人類可讀規則陣列。**無 tenantId 則回平台預設**。 |

### 6.4 強制改密流程（Phase 3）

| Method | Path | 變更 |
|---|---|---|
| `POST` | `/v1/noauth/login` | response 多 `passwordChangeRequired: boolean`、`temporaryToken: string`（如為 true） |
| `PUT` | `/v1/noauth/user/force-change-password` | 帶 temporary token + new password，成功後發正常 access/refresh token |

### 6.5 Request / Response 範例

```http
GET /v1/auth/password-policy
Authorization: Bearer ...

200 OK
{
  "errorCode": "00000",
  "body": {
    "minLength": 10,
    "maxLength": 128,
    "requireUppercase": true,
    "requireLowercase": true,
    "requireDigit": true,
    "requireSpecial": true,
    "minSpecialChars": 1,
    "historyCount": 5,
    "expireDays": 90,
    "forceChangeOnFirstLogin": true,
    "forceChangeOnAdminReset": true,
    "describe": [
      "密碼長度至少 10 字元（最多 128）",
      "須包含大寫、小寫、數字、特殊字元",
      "不可與前 5 次密碼相同",
      "每 90 天需更換"
    ]
  }
}
```

---

## 7. 前端（Phase 1+ 同步）

| 頁面 | 路由 | 對應角色 |
|---|---|---|
| 平台密碼策略設定頁 | `/platform/password-policy` | `SUPER_ADMIN` |
| 租戶密碼策略設定頁 | `/admin/security/password-policy` | `TENANT_ADMIN` |
| 強制改密頁（Phase 3） | `/force-change-password` | 持 temporary token |

UI 要點：
- 租戶設定頁要顯示「平台下限」標示（例如：`min_length` 輸入框旁註明「平台規定不得低於 8」）。
- 切換「使用平台預設 / 自訂」toggle，對應 PUT / DELETE API。
- 密碼輸入元件（登入、改密、reset）即時顯示規則符合狀態（拉 `/v1/noauth/password-policy/describe`）。

---

## 8. 測試計畫

### 8.1 單元測試（新增）

| 測試類別 | 涵蓋 |
|---|---|
| `PasswordPolicyResolverImplTest` | tenant 覆寫 / 平台預設 / 程式硬編碼三層 fallback |
| `PasswordPolicyValidationTest` | 平台下限拒絕 / 平台下限放行 |
| `PasswordPolicyCacheTest` | TTL 行為、evict |
| `PasswordValidatorTest` 擴充 | 新 tenant-aware 簽名、`not_contains_username` |
| `PasswordExpiryCheckerTest`（Phase 3） | OK / EXPIRED / FORCE_CHANGE 三狀態 |

### 8.2 整合測試（新增）

- 平台 SUPER_ADMIN 設 `min_length=10` → 租戶 admin 嘗試設 `min_length=6` 回 400
- 租戶覆寫 `require_special=false` → 該租戶的 reset 流程接受無特殊字元密碼，其他租戶仍需特殊字元
- D-2 驗證：舊使用者用不符新規則的密碼登入 → 200 成功
- Phase 3：D-3 驗證：migration 後立即登入，不被判定過期

### 8.3 既有測試的影響

| 測試 | 影響 |
|---|---|
| `PasswordValidatorTest` | 簽名變更，需更新所有呼叫處 |
| `AuthServiceTest` | `resetPassword` 相關 case 需 mock `PasswordPolicyResolver` |
| `UserAdminServiceTest` | `createUser` / `resetPassword` 同上 |

---

## 9. 實作分期與時程（粗估）

| Phase | 範圍 | 狀態 |
|---|---|---|
| **Phase 1** | §3.1 基礎複雜度 + §5 解析層 + 快取 + 平台/租戶 API | ✅ **2026-05-27 完成（後端）** |
| **Phase 1.5** | 前端設定頁（平台 / 租戶 / 公開規則描述） | ✅ **2026-05-27 完成（前端）** |
| **Phase 2** | §3.2 進階規則（含 `not_contains_username`、字數限制） | ✅ **2026-05-27 完成（後端）** |
| **Phase 3** | §3.3 過期 + 強制改密 + DB 欄位 + 登入流程整合 + 強制改密頁 | ✅ **2026-05-27 完成（後端）** |
| **Phase 4**（可選，超出本規格） | 字典 / 正則 / 即將到期通知 | — |

### 9.1 Phase 1 後端交付清單（2026-05-27）

| 類別 | 檔案 |
|---|---|
| Migration | [V44__auth__password_policy_platform_defaults.sql](backend/src/main/resources/db/migration/V44__auth__password_policy_platform_defaults.sql) |
| Value / Enum | [PasswordPolicy](backend/src/main/java/com/taipei/iot/auth/policy/PasswordPolicy.java)、[PasswordPolicyKey](backend/src/main/java/com/taipei/iot/auth/policy/PasswordPolicyKey.java) |
| DAO（bypass tenantFilter） | [PasswordPolicyDao](backend/src/main/java/com/taipei/iot/auth/policy/PasswordPolicyDao.java) — 透過 native SQL 操作 `system_settings`，未實作 `TenantScopedRepository`，因此略過 `TenantFilterAspect` |
| Resolver + 內建 TTL 快取 | [PasswordPolicyResolver](backend/src/main/java/com/taipei/iot/auth/policy/PasswordPolicyResolver.java) — 60 秒 TTL，寫入時 evict |
| Service | [PasswordPolicyService](backend/src/main/java/com/taipei/iot/auth/policy/PasswordPolicyService.java) — 平台 / 租戶讀寫 + 平台下限驗證 |
| DTO | [PasswordPolicyDto](backend/src/main/java/com/taipei/iot/auth/policy/dto/PasswordPolicyDto.java)、[UpdatePasswordPolicyRequest](backend/src/main/java/com/taipei/iot/auth/policy/dto/UpdatePasswordPolicyRequest.java) |
| Controllers | [PlatformPasswordPolicyController](backend/src/main/java/com/taipei/iot/auth/controller/PlatformPasswordPolicyController.java)、[TenantPasswordPolicyController](backend/src/main/java/com/taipei/iot/auth/controller/TenantPasswordPolicyController.java)、[NoauthPasswordPolicyController](backend/src/main/java/com/taipei/iot/auth/controller/NoauthPasswordPolicyController.java) |
| 重構 | [PasswordValidator](backend/src/main/java/com/taipei/iot/user/service/PasswordValidator.java) — 新簽名 `(tenantId, password, UserContext)`、[PasswordHistoryRepository](backend/src/main/java/com/taipei/iot/user/repository/PasswordHistoryRepository.java) — 動態 `Pageable` |
| 呼叫端更新 | [AuthServiceImpl.resetPassword](backend/src/main/java/com/taipei/iot/auth/service/impl/AuthServiceImpl.java)、[UserSelfService.changePassword](backend/src/main/java/com/taipei/iot/user/service/UserSelfService.java)、[UserAdminService.createUser](backend/src/main/java/com/taipei/iot/user/service/UserAdminService.java) |
| 列舉新增 | `ErrorCode.PASSWORD_POLICY_BELOW_PLATFORM_MINIMUM` / `_INVALID_KEY` / `_INVALID_VALUE`；`AuditEventType.UPDATE_PASSWORD_POLICY` / `UPDATE_PLATFORM_PASSWORD_POLICY` |
| 單元測試（新增 27） | [PasswordPolicyResolverTest](backend/src/test/java/com/taipei/iot/auth/policy/PasswordPolicyResolverTest.java) ×5、[PasswordPolicyServiceTest](backend/src/test/java/com/taipei/iot/auth/policy/PasswordPolicyServiceTest.java) ×12、[PasswordValidatorTest](backend/src/test/java/com/taipei/iot/user/service/PasswordValidatorTest.java) ×10 |

**測試結果**：`mvn -o test` → 438/438 通過（411 baseline + 27 new）。

### 9.2 Phase 1 對 D-6 的微調

規格原本要求平台預設「儲存於同一張 `system_settings` 表」。Phase 1 確實照此實作（仍是同一張表 + `tenant_id = '__PLATFORM__'` sentinel），但**不**透過既有的 `SystemSettingRepository`（其受 `TenantFilterAspect` 強制套用 tenant filter），而是新增 [PasswordPolicyDao](backend/src/main/java/com/taipei/iot/auth/policy/PasswordPolicyDao.java) 走 native SQL。優點：避免污染 `SystemSettingService` 既有 tenant-scoped 語義，也不需在每個讀取點包 `runInSystemContext`。

### 9.3 Phase 1 已知限制（保留給後續 phase）

- `AuthServiceImpl.resetPassword`（reset-password no-auth 流程）目前以 `tenantId = null` 取得**平台預設**規則 — 若日後 reset token 能反查使用者所屬 tenant，可改為 tenant-aware（不影響 D-2/D-4）。
- `UserContext` 已預留但 Phase 1 未使用；Phase 2 `password.not_contains_username` 上線時即可啟用。
- 單節點 TTL 快取；多節點部署需依 §10 規畫導入跨節點失效機制。

### 9.4 Phase 3 後端交付清單（2026-05-27）

| 類別 | 檔案 |
|---|---|
| Migration | [V45__auth__password_expiry_columns.sql](backend/src/main/resources/db/migration/V45__auth__password_expiry_columns.sql) — `users` 加 `password_changed_at` / `force_change_password`，並 backfill `NOW()` 以實現 D-3<br>[V46__auth__password_policy_phase3_defaults.sql](backend/src/main/resources/db/migration/V46__auth__password_policy_phase3_defaults.sql) — 平台預設：expire_days=90、force_change_on_first_login=true、force_change_on_admin_reset=true |
| Entity 擴充 | [UserEntity](backend/src/main/java/com/taipei/iot/auth/entity/UserEntity.java) +`passwordChangedAt`、`forceChangePassword` |
| Policy 模型擴充 | [PasswordPolicy](backend/src/main/java/com/taipei/iot/auth/policy/PasswordPolicy.java) +`expireDays`、`forceChangeOnFirstLogin`、`forceChangeOnAdminReset`；[PasswordPolicyKey](backend/src/main/java/com/taipei/iot/auth/policy/PasswordPolicyKey.java) +3 個 enum、[PasswordPolicyResolver](backend/src/main/java/com/taipei/iot/auth/policy/PasswordPolicyResolver.java) populate 新欄位、[PasswordPolicyDto](backend/src/main/java/com/taipei/iot/auth/policy/dto/PasswordPolicyDto.java) 對外暴露 |
| 過期判定 | [PasswordExpiryChecker](backend/src/main/java/com/taipei/iot/auth/policy/PasswordExpiryChecker.java) + [PasswordExpiryStatus](backend/src/main/java/com/taipei/iot/auth/policy/PasswordExpiryStatus.java)（OK / EXPIRED / FORCE_CHANGE） |
| Token | [JwtUtil.generatePasswordChangeToken](backend/src/main/java/com/taipei/iot/auth/security/JwtUtil.java) — `purpose=password_change` claim，TTL = `jwt.temporary-token-expiration` |
| DTO | [ForceChangePasswordRequest](backend/src/main/java/com/taipei/iot/auth/dto/request/ForceChangePasswordRequest.java)、[LoginResult.passwordChangeRequired](backend/src/main/java/com/taipei/iot/auth/dto/response/LoginResult.java) |
| Service | [AuthServiceImpl.login](backend/src/main/java/com/taipei/iot/auth/service/impl/AuthServiceImpl.java) — 在 `matches()` 之後（D-2）插入 expiry check；[AuthServiceImpl.forceChangePassword](backend/src/main/java/com/taipei/iot/auth/service/impl/AuthServiceImpl.java) — 驗 token → 寫密碼 → 同 login 分支發 token；resetPassword / [UserSelfService.changePassword](backend/src/main/java/com/taipei/iot/user/service/UserSelfService.java) / [UserAdminService.createUser](backend/src/main/java/com/taipei/iot/user/service/UserAdminService.java) 同步維護 `passwordChangedAt` / `forceChangePassword` |
| API | `POST /v1/noauth/user/force-change-password`（[AuthController](backend/src/main/java/com/taipei/iot/auth/controller/AuthController.java)） |
| 列舉新增 | `ErrorCode.FORCE_CHANGE_PASSWORD_TOKEN_INVALID` (20020)、`AuditEventType.FORCE_CHANGE_PASSWORD` |
| 單元測試（新增 10） | [PasswordExpiryCheckerTest](backend/src/test/java/com/taipei/iot/auth/policy/PasswordExpiryCheckerTest.java) ×5；[AuthServiceTest](backend/src/test/java/com/taipei/iot/auth/service/AuthServiceTest.java) +5（login expired / force-change、forceChangePassword 成功 / 錯 purpose / parse 失敗） |

**測試結果**：`mvn -o test` → 448/448 通過（438 baseline + 10 new）。

**架構決策回顧**：
- D-2 遵循：登入仍只用 `passwordEncoder.matches` 判斷憑證對錯；expiry 在 step 6（清 fail_count）之後、token 發放之前作為另一層 gate，因此「舊密碼仍能通過 login」這條 contract 不變。
- D-3 遵循：V45 先 `ALTER ADD COLUMN`（nullable）→ `UPDATE ... NOW()` backfill → 才 `SET NOT NULL`，避免上線當下整批使用者被視為過期。
- 規格 §4.2 原寫 `user` 表名為筆誤；實際 schema 為 `users`，V45 已採用正確名稱。
- Policy tenant 解析：登入時若使用者僅綁定單一 tenant，使用該 tenant 的 policy；其餘（super admin / 多 tenant / 無 mapping）退回 platform 預設。`forceChangePassword` 提交時沿用同樣邏輯，確保「被擋下的 login」與「remediation 的 validate」使用同一份 policy。

### 9.5 Phase 2 後端交付清單（2026-05-27）

| 類別 | 檔案 |
|---|---|
| Migration | [V47__auth__password_policy_phase2_defaults.sql](backend/src/main/resources/db/migration/V47__auth__password_policy_phase2_defaults.sql) — 平台預設：max_length=128、min_special_chars / min_digits / min_uppercase / min_lowercase=1、not_contains_username=true |
| Policy 模型擴充 | [PasswordPolicyKey](backend/src/main/java/com/taipei/iot/auth/policy/PasswordPolicyKey.java) +6 enum（含 `MAX_LENGTH` floor=64、`MIN_*` floor=1）<br>[PasswordPolicy](backend/src/main/java/com/taipei/iot/auth/policy/PasswordPolicy.java) +6 欄位、`describe()` 反映新規則<br>[PasswordPolicyResolver](backend/src/main/java/com/taipei/iot/auth/policy/PasswordPolicyResolver.java) populate 新欄位<br>[PasswordPolicyDto](backend/src/main/java/com/taipei/iot/auth/policy/dto/PasswordPolicyDto.java) + [PasswordPolicyService.toDto](backend/src/main/java/com/taipei/iot/auth/policy/PasswordPolicyService.java) 對外暴露 |
| 驗證規則 | [PasswordValidator.validate](backend/src/main/java/com/taipei/iot/user/service/PasswordValidator.java) — 新增：<br>① `max_length` DoS 上限；<br>② `min_uppercase / min_lowercase / min_digits / min_special_chars` 計數（僅當 require_* 為 true 且 min > 1 時生效，避免與 Phase 1「至少 1 個」重複報錯）；<br>③ `not_contains_username`：case-insensitive、檢查 `UserContext.username` 與 email local-part（`@` 之前）；`userContext == null` 時跳過 |
| 單元測試（新增 10） | [PasswordValidatorTest](backend/src/test/java/com/taipei/iot/user/service/PasswordValidatorTest.java) +10：maxLength 超限 / 0 停用、min_uppercase / min_digits / min_special 不足、4 種計數全部達標、含 username（大小寫不敏感）、含 email local-part、`userContext=null` 跳過、`not_contains_username=false` 不阻擋 |

**測試結果**：`mvn -o test` → 458/458 通過（448 baseline + 10 new）。

**架構決策回顧**：
- `min_*` 計數規則「require_* 開啟且 min > 1 才檢查」：避免與 Phase 1 既有的 `require_uppercase`（至少 1 個大寫）規則重複拋錯，也讓「require=true 且 min=1」這個預設值組合等價於純 Phase 1 行為，向後相容。
- `max_length` 預設 128、floor 64：floor 防止租戶設過小（如 max_length=10 把所有人鎖在外）。`maxLength=0` 視為「不設上限」以保留關閉開關。
- `not_contains_username` 在 `userContext == null` 時靜默跳過：reset-password no-auth 流程現已都傳入 `UserContext(email, email)`（沒有獨立 username 欄位），但保留 null-safe 行為避免未來新增呼叫點時遺漏。
- `not_contains_username` 同時檢查 email 的 local-part：`john.doe@example.com` 的 `john.doe` 也被視為敏感子字串（spec §11 對應 Keycloak 的 _Not Email_）。
- 「平台下限」對 `max_length` 採用 floor 而非 ceiling：與其它 INT key 介面一致（`PasswordPolicyService` 對所有 INT key 套 `proposed >= platformMin`）。實際上 max_length 的下限語義仍然合理（防止租戶設過嚴）。

### 9.6 Phase 1.5 前端交付清單（2026-05-27）

| 類別 | 檔案 |
|---|---|
| Menu / Permission Migration | [V48__auth__password_policy_menus.sql](backend/src/main/resources/db/migration/V48__auth__password_policy_menus.sql) — 新增 menu 33 `TenantPasswordPolicy` / 34 `PlatformPasswordPolicy` + 4 個 permission code，並把 tenant-scope view/manage 綁到 `ADMIN` 角色 |
| Types | [passwordPolicy.ts](frontend/src/types/passwordPolicy.ts) — `PasswordPolicyDto`（15 欄位對齊後端）、`UpdatePasswordPolicyRequest`、`POLICY_KEYS` 靜態目錄（basic / advanced / expiry 分組 + i18n label key）、`ForceChangePasswordRequest` |
| API Client | [passwordPolicy/index.ts](frontend/src/api/passwordPolicy/index.ts) — `getEffectivePolicy` / `getTenantOverrides` / `updateTenantOverride` / `deleteTenantOverride` / `getPlatformDefaults` / `updatePlatformDefault` / `describePolicy(tenantId?)` / `forceChangePassword(token, payload)`（後者直接帶 `Authorization: Bearer ${token}` header，繞過 store） |
| Auth Store 擴充 | [auth.ts](frontend/src/types/auth.ts) `LoginResult.passwordChangeRequired?: boolean`；[authStore.ts](frontend/src/stores/authStore.ts) +`passwordChangeToken` state、`doLogin` 加入「若 `passwordChangeRequired` 則跳轉 `/force-change-password`」early-return 分支、新增 `applyPostForceChangeLogin(loginResult)` 動作（清 token 後沿用 doLogin 的 select-tenant / direct-login 分支） |
| Router | [router/index.ts](frontend/src/router/index.ts) — `/force-change-password`（無認證，meta.requiresAuth=false）、`/admin/security/password-policy`（TENANT_ADMIN，受 menu access 控制）、`/platform/password-policy`（meta.superAdminOnly=true，繞過 menu 檢查） |
| Shared Component | [PasswordRulesHint.vue](frontend/src/components/PasswordRulesHint.vue) — 接 `tenantId? / password? / username? / email?`，mount + watch 時拉 `describePolicy(tenantId)`；若帶 `password` 則對 8 條基本規則做 client-side eval 顯示 Check/X，否則用 `policy.describe` 條列；history / expiry / not_contains_username 純資訊呈現 |
| 強制改密頁 | [ForceChangePasswordView.vue](frontend/src/views/login/ForceChangePasswordView.vue) — onMounted 防止直接導頁（`passwordChangeToken` 為空則回 `/login`）；提交呼叫 `forceChangePassword(token, ...)` → `applyPostForceChangeLogin(res.body)`；errorCode 20020 特例顯示「驗證資訊已失效」訊息；內嵌 `PasswordRulesHint` |
| 平台設定頁 | [PlatformPasswordPolicyView.vue](frontend/src/views/admin/setting/PlatformPasswordPolicyView.vue) — SUPER_ADMIN；載入 `getPlatformDefaults()` 後按 category 分區；編輯彈窗用 `el-input-number` (INT) / `el-switch` (BOOL)；無 floor 限制（自身就是 floor 來源） |
| 租戶設定頁 | [TenantPasswordPolicyView.vue](frontend/src/views/admin/setting/TenantPasswordPolicyView.vue) — TENANT_ADMIN；`Promise.all([getEffectivePolicy(), getTenantOverrides()])` 並行；以 `FIELD_BY_KEY` 把 snake-case key 對映到 DTO camelCase 欄位顯示當前值；有 override 的列顯示「客製化」tag + 一鍵 reset 按鈕（呼叫 `deleteTenantOverride`）；編輯時 client-side 先做 floor check 再 PUT |
| 既有頁面整合 | [ResetPasswordView.vue](frontend/src/views/login/ResetPasswordView.vue)、[ChangePasswordView.vue](frontend/src/views/user/ChangePasswordView.vue) — 移除 / 簡化 hardcoded `passwordChecks` / `passwordPattern`，改用 `<PasswordRulesHint :password="..." />`；保留 8 字元最小長度做為 UX 即時回饋，最終規則仍以後端為準 |
| i18n | [zh-TW.ts](frontend/src/locales/zh-TW.ts)、[en.ts](frontend/src/locales/en.ts)、[zh-CN.ts](frontend/src/locales/zh-CN.ts) — 新增 `passwordPolicy.*`（platform/tenant 標題、categories、15 個 key labels、column / dialog / error 文案）、`forceChangePassword.*`（標題 / placeholders / 7 個 error key） |

**驗證結果**：`npx vue-tsc --noEmit` → 0 errors；`npm run build` → 僅剩 baseline 的 `AuditFilterBar.vue` 2 個 TS error（與 Phase 1.5 無關，git stash 驗證確認為既有問題）。所有新增 / 修改檔案均通過 type-check。

**架構決策回顧**：
- **規則描述用公開 API**：`/v1/noauth/password-policy/describe?tenantId=` 不需登入即可呼叫，因此 `PasswordRulesHint` 在登入 / 改密 / reset 三個場景都能取得當下 tenant 的有效規則；client-side 的 Check/X 只是即時 UX，最終判定仍以後端 `PasswordValidator` 為準（與 spec §11「server is source of truth」一致）。
- **強制改密 token 走 Pinia state 而非 URL query**：避免 temp token 出現在 browser history / referer / 伺服器 access log；缺點是 reload 後 state 消失需重新登入，但這正是 D-2 期望的單次性語義。
- **租戶頁採用 `Promise.all` 並行載入**：effective policy 與 overrides 兩個 endpoint 互不依賴，並行可去掉一次 round-trip 延遲。
- **`/platform/password-policy` 用 `meta.superAdminOnly` 而非 menu access**：與 `TenantManage` 路由同模式，避免把平台級頁面塞進每個 tenant 的 menu tree；對 SUPER_ADMIN 而言只需鍵入 URL 即可進入。
- **V48 不自動綁定 platform manage 權限**：`PASSWORD_POLICY_PLATFORM_VIEW/MANAGE` 兩個 perm 只建立、不分配 — backend `@PreAuthorize` 對 SUPER_ADMIN 直接放行，tenant admin 永遠不應看到平台預設，故刻意不把 platform 權限綁給任何 role。
- **既有 `ChangePasswordView` 保留舊正則防線**：未直接刪除 `passwordPattern` 而是補上註解標示其作為 client-side floor 的角色，並依靠 `PasswordRulesHint` 提供 per-rule 視覺回饋；如此即使後端某個 tenant 把規則調得比正則寬鬆，client 也不會把 valid 密碼擋下。


---

## 10. 風險與緩解

| 風險 | 機率 | 緩解 |
|---|---|---|
| 平台預設綁住 `__PLATFORM__` sentinel，未來想拆獨立表 | 中 | 介面 `PasswordPolicyResolver` 已抽象，替換實作不影響呼叫端 |
| 快取造成設定變更延遲生效（最多 60s） | 低 | 寫入時主動 `evict`，跨 instance 走 Redis pub/sub（Phase 4 再做） |
| Phase 3 上線當下「`password_changed_at IS NULL` 的舊資料」 | 中 | migration 內 `UPDATE ... SET NOW()`，且 NOT NULL 約束在 UPDATE 後才加 |
| `not_contains_username` 大小寫 / 子字串判定爭議 | 低 | 規範：case-insensitive、且僅檢查 email local-part（@ 之前） |
| 強制改密 temporary token 被截獲 | 低 | TTL 5 分鐘 + 一次性（用完進 Redis blacklist），對齊現有 reset token 機制 |

---

## 11. 對照 Keycloak Password Policy 覆蓋率

| Keycloak Policy | 本規格 | Phase |
|---|---|---|
| Hash Algorithm | ❌（沿用既有 BCrypt） | — |
| Hash Iterations | ❌ | — |
| Digits | ✅ `require_digit` + `min_digits` | 1 / 2 |
| Lowercase Characters | ✅ `require_lowercase` + `min_lowercase` | 1 / 2 |
| Uppercase Characters | ✅ `require_uppercase` + `min_uppercase` | 1 / 2 |
| Special Characters | ✅ `require_special` + `min_special_chars` | 1 / 2 |
| Minimum Length | ✅ `min_length` | 1 |
| Maximum Length | ✅ `max_length` | 2 |
| Not Username | ✅ `not_contains_username` | 2 |
| Not Email | ✅（同上，檢查 email local-part） | 2 |
| Regex Pattern | ❌（Phase 4） | — |
| Password History | ✅ `history_count` | 1 |
| Force Expired Password Change | ✅ `expire_days` | 3 |
| Not Recently Used | ✅（即 history） | 1 |
| Password Blacklist | ❌（Phase 4） | — |
| HaveIBeenPwned | ❌（Phase 4） | — |

**Phase 1-3 完成後對齊度約 75%**，足以覆蓋多數企業合規需求。
