# User 模組 Code Review

## 整體評價

User 模組結構清晰，涵蓋管理端（UserAdmin）和使用者自助端（UserSelf）兩條路線，分層合理。安全設計具備 DataScope 控管、角色指派檢核、密碼複雜度與歷史比對、操作審計日誌等多層防線。以下從安全性、正確性、可維護性三個維度進行評估。

---

## 模組結構

| 層級 | 檔案 | 說明 |
|------|------|------|
| Controller | `UserAdminController` | 管理端 CRUD + 場域角色管理 |
| Controller | `UserSelfController` | 個人資料修改 + 修改密碼 |
| Service | `UserAdminService` | 管理端操作（含 DataScope/角色檢核） |
| Service | `UserSelfService` | 使用者自助操作 |
| Service | `UserAuditService` | 操作日誌記錄 |
| Service | `PasswordValidator` | 密碼複雜度 + 歷史重複檢查 |
| Entity | `UserInfoLogEntity` | 操作異動紀錄表 |
| Entity | `PasswordHistoryEntity` | 密碼歷史表 |
| Repository | `UserInfoLogRepository`, `PasswordHistoryRepository` | 資料存取 |
| DTO | `CreateUserRequest`, `UpdateUserRequest`, `ChangePasswordRequest`, `UpdateOwnProfileRequest`, `AddTenantRoleRequest` | 請求物件 |
| DTO | `UserListItemDto`, `UserTenantMappingDto`, `PageResponse<T>` | 回應物件 |

前端：

| 檔案 | 說明 |
|------|------|
| `types/user.ts` | TypeScript 型別定義 |
| `api/user/index.ts` | API 呼叫封裝 |
| `stores/userStore.ts` | Pinia 狀態管理 |
| `views/user/ProfileView.vue` | 個人資料修改頁 |
| `views/user/ChangePasswordView.vue` | 修改密碼頁 |
| `views/user/MyActivityView.vue` | 個人操作紀錄頁 |
| `views/admin/UserListView.vue` | 管理端使用者列表 |
| `views/admin/CreateUserView.vue` | 管理端新增使用者 |
| `views/admin/EditUserView.vue` | 管理端編輯使用者 |

---

## 優點 (值得肯定)

1. **DataScope 權限控管**
   - **UserAdminService.java — createUser()**
   - 透過 `DataScopeHelper.isDeptInScope()` 檢核操作者是否有權在目標部門建立帳號，防止越權操作。

2. **角色指派檢核**
   - **UserAdminService.java — createUser()**
   - 使用 `RoleService.isRoleAssignable()` 確保操作者只能指派自身權限範圍內的角色，防止權限升級攻擊。

3. **DataScope 驅動的列表查詢**
   - **UserAdminService.java — listUsers()**
   - 根據 `getVisibleDeptIds()` 結果動態切換查詢邏輯（全場域 / 單一部門 / 多部門），確保資料隔離。

4. **防止自我停用/刪除**
   - **UserAdminService.java — disableUser(), softDeleteUser()**
   - `adminUserId.equals(targetUserId)` 檢核防止管理員停用或刪除自己的帳號。

5. **密碼複雜度驗證 + 歷史重複防護**
   - **PasswordValidator.java**
   - 可設定的密碼最小長度、大小寫英文字母、數字要求，加上最近 5 次密碼歷史比對，符合資安規範。

6. **完整的審計日誌**
   - **UserAuditService.java + @AuditEvent annotation**
   - 所有管理端操作（CREATE、UPDATE、DISABLE、DELETE、ADD_TENANT、REMOVE_TENANT）和自助操作都有日誌追蹤。

7. **軟刪除機制**
   - **UserAdminService.java — softDeleteUser()**
   - 帳號軟刪除（設 `deleted=true` + `deletedAt` + 停用），保留資料可追溯性。

8. **前端 URL 參數編碼**
   - **api/user/index.ts**
   - 所有路徑參數使用 `encodeURIComponent(userId)` 防止路徑注入。

9. **前端密碼複雜度同步驗證**
   - **ChangePasswordView.vue, CreateUserView.vue**
   - 前端以正則 `/^(?=.*[a-z])(?=.*[A-Z])(?=.*\d).{8,}$/` 進行即時驗證，與後端規則一致。

10. **前端操作確認機制**
    - **UserListView.vue**
    - 停用/刪除帳號前使用 `ElMessageBox.confirm` 二次確認，且操作按鈕在自己帳號上不顯示。

11. **測試覆蓋良好**
    - `UserAdminControllerTest`：認證/授權邊界測試（401, 403）。
    - `UserAdminServiceTest`：核心業務邏輯（CRUD、DataScope 過濾、場域角色管理）。
    - `UserSelfServiceTest`：修改個人資料、修改密碼成功/失敗場景。

---

## 需要改進的問題

### 安全性問題

1. **[高] changePassword 缺少舊密碼驗證**
   - **UserSelfService.java — changePassword()**
   - `ChangePasswordRequest` 只有 `newPassword` 欄位，沒有要求提供舊密碼進行驗證。
   - 風險：若 access token 被竊取（例如 XSS），攻擊者可直接修改密碼完成帳號接管。
   - 建議：

     ```java
     // ChangePasswordRequest.java 加入：
     @NotBlank
     private String oldPassword;

     // UserSelfService.java 加入驗證：
     if (!passwordEncoder.matches(req.getOldPassword(), user.getPasswordHash())) {
         throw new BusinessException(ErrorCode.OLD_PASSWORD_INCORRECT);
     }
     ```

2. **[高] change-password 端點缺少速率限制**
   - **UserSelfController.java — /v1/auth/user/change-password**
   - 修改密碼端點無 `@RateLimit` 保護。攻擊者可以大量嘗試，搭配第 1 點的問題更為危險。
   - 建議加入 `@RateLimit(key = "change-password", limit = 5, window = 300)`。

3. **[中等] UserAdminController 基礎 CRUD 缺少 @PreAuthorize**
   - **UserAdminController.java — listUsers(), createUser(), updateUser(), disableUser(), softDeleteUser()**
   - 這些端點只依賴 JWT 認證，但沒有 `@PreAuthorize` 限制角色。權限控管完全依賴 DataScope（在 Service 層），若 DataScope 邏輯有缺陷，VIEWER 角色可能也能呼叫這些端點。
   - 建議：至少在 Controller 層加上 `@PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")` 作為多層防禦。

4. **[中等] updateUser 未做 DataScope 檢核**
   - **UserAdminService.java — updateUser()**
   - `createUser()` 有做 `isDeptInScope()` 和 `isRoleAssignable()` 檢核，但 `updateUser()` 完全沒有。
   - 風險：部門管理員可以修改非自己管轄範圍內的使用者（只要知道 userId）。
   - 建議：
     - 驗證目標使用者的部門是否在操作者的 DataScope 內。
     - 若要變更 roleId，也需檢查 `isRoleAssignable()`。

5. **[中等] disableUser / softDeleteUser 未做 DataScope 檢核**
   - **UserAdminService.java — disableUser(), softDeleteUser()**
   - 只做了「不能停用自己」的檢查，未驗證目標使用者是否在操作者的管轄範圍內。
   - 建議加入 DataScope 驗證。

6. **[中等] UserSelfController 回傳完整 UserEntity**
   - **UserSelfController.java — updateOwnProfile() 回傳 `BaseResponse<UserEntity>`**
   - `UserEntity` 包含 `passwordHash`、`loginFailCount`、`isSuperAdmin` 等敏感欄位，直接序列化回傳到前端。
   - 建議：回傳專用 DTO（如 `UserInfoDto`），或在 Entity 上加 `@JsonIgnore`。

7. **[中等] PasswordValidator 缺少特殊字元要求**
   - **PasswordValidator.java**
   - 只驗證大小寫 + 數字，未要求特殊字元。對於企業級系統建議增加。
   - 前端正則也未包含特殊字元驗證。

8. **[低] userId 路徑參數無格式驗證**
   - **UserAdminController.java — 所有 @PathVariable String userId**
   - 未限制 userId 格式（應為 UUID）。惡意 userId 可能包含特殊字元。
   - 建議使用 `@Pattern(regexp = "^[a-f0-9\\-]{36}$")` 驗證。

### 正確性問題

9. **[中等] updateOwnProfile 未處理 notifySmsFlag / notifyEmailFlag**
   - **UserSelfService.java — updateOwnProfile()**
   - `UpdateOwnProfileRequest` 有 `notifySmsFlag` 和 `notifyEmailFlag` 欄位，`UserEntity` 也有對應欄位，但 Service 層沒有設值邏輯。
   - 前端 `ProfileView.vue` 有這兩個開關的 UI，但提交後不會生效。
   - 建議加入：

     ```java
     if (req.getNotifySmsFlag() != null) {
         user.setNotifySmsFlag(req.getNotifySmsFlag());
     }
     if (req.getNotifyEmailFlag() != null) {
         user.setNotifyEmailFlag(req.getNotifyEmailFlag());
     }
     ```

10. **[中等] EditUserView 載入所有使用者以找單一使用者**
    - **EditUserView.vue — onMounted()**
    - 使用 `fetchUserList({ page: 0, size: 9999 })` 載入所有使用者再 `find`，效率極差。
    - 建議新增 `GET /v1/auth/users/{userId}` 端點回傳單一使用者資料。

11. **[中等] EditUserView 使用 listRoles 而非 listAssignableRoles**
    - **EditUserView.vue — onMounted()**
    - `CreateUserView` 正確使用 `listAssignableRoles()`（只取可指派的角色），但 `EditUserView` 使用 `listRoles()`（取所有角色）。
    - 風險：使用者在編輯頁可以看到並選擇不應指派的角色。
    - 建議統一使用 `listAssignableRoles()`。

12. **[低] createUser 產生的 userId 未做唯一性確認**
    - **UserAdminService.java — createUser()**
    - `UUID.randomUUID().toString()` 理論上極低機率重複，但若需嚴謹可加重試邏輯或 DB unique constraint（已有 PK）。

### 可維護性問題

13. **[低] toUserListItemDto 存在 N+1 查詢風險**
    - **UserAdminService.java — toUserListItemDto()**
    - 若 `mapping.getUser()` / `mapping.getRole()` 為 null 時會逐筆查 DB，在大列表時可能產生 N+1 問題。
    - 建議使用 `@EntityGraph` 或 JOIN FETCH 在查詢時一次載入關聯。

14. **[低] UserAuditService 與 @AuditEvent 職責重疊**
    - `UserAuditService` 手動記錄到 `user_info_log` 表，`@AuditEvent` 又觸發通用審計模組。
    - 建議統一到一個機制，避免同一操作產生兩筆不同格式的審計紀錄。

15. **[低] PageResponse<T> 重複定義**
    - `PageResponse` 放在 user 模組下，但語義上是通用工具。
    - 建議搬到 `common` 模組。

---

## 安全性專項審查

### OWASP Top 10 對照

| OWASP 類別 | 狀態 | 說明 |
|------------|------|------|
| A01 Broken Access Control | ⚠️ 中等風險 | DataScope 在 createUser 有做，但 updateUser/disableUser/softDeleteUser 缺失。Controller 層缺少角色檢核。 |
| A02 Cryptographic Failures | ✅ 良好 | 使用 BCrypt 加密密碼，密碼不明文傳輸/儲存。 |
| A03 Injection | ✅ 良好 | 使用 JPA 參數化查詢，無原生 SQL 拼接。前端使用 `encodeURIComponent`。 |
| A04 Insecure Design | ⚠️ 中等風險 | changePassword 缺少舊密碼驗證，設計上存在帳號接管風險。 |
| A05 Security Misconfiguration | ✅ 良好 | 依賴全域 Security 設定（CORS、CSRF、Headers）。 |
| A06 Vulnerable Components | ✅ 良好 | 使用 Spring Boot 生態系主流套件。 |
| A07 Auth Failures | ⚠️ 低風險 | change-password 無速率限制。 |
| A08 Data Integrity Failures | ✅ 良好 | 使用 `@Valid` 驗證輸入，DTO 分層防止 mass assignment。 |
| A09 Logging Failures | ✅ 良好 | 完整的 UserAuditService + AuditEvent 雙重日誌。 |
| A10 SSRF | N/A | 模組無對外 HTTP 請求。 |

### 敏感資料處理

| 欄位 | 處理方式 | 狀態 |
|------|---------|------|
| 密碼 | BCrypt 加密儲存 | ✅ |
| passwordHash | 不在 DTO 中回傳（AdminController） | ✅ |
| passwordHash | **UserSelfController 回傳 UserEntity** | ❌ 洩漏風險 |
| email | 正常傳輸，需配合 HTTPS | ✅ |
| phone | 正常傳輸，需配合 HTTPS | ✅ |

### 認證與授權矩陣

| 端點 | 認證 | 授權 | DataScope |
|------|------|------|-----------|
| GET /v1/auth/users | JWT ✅ | 無 ⚠️ | listUsers ✅ |
| POST /v1/auth/users | JWT ✅ | 無 ⚠️ | createUser ✅ |
| PUT /v1/auth/users/{id} | JWT ✅ | 無 ⚠️ | **缺失** ❌ |
| DELETE /v1/auth/users/{id} | JWT ✅ | 無 ⚠️ | **缺失** ❌ |
| PATCH /v1/auth/users/{id}/soft-delete | JWT ✅ | 無 ⚠️ | **缺失** ❌ |
| GET /v1/auth/users/{id}/tenant-roles | JWT ✅ | SUPER_ADMIN ✅ | N/A |
| POST /v1/auth/users/{id}/tenant-roles | JWT ✅ | SUPER_ADMIN ✅ | N/A |
| DELETE /v1/auth/users/{id}/tenant-roles/{mid} | JWT ✅ | SUPER_ADMIN ✅ | N/A |
| PUT /v1/auth/user/my | JWT ✅ | 自己 ✅ | N/A |
| POST /v1/auth/user/change-password | JWT ✅ | 自己 ✅ | N/A |

---

## 建議優先修復順序

| 優先級 | 問題 | 影響 |
|--------|------|------|
| 🔴 P0 | changePassword 加入舊密碼驗證 | 防止 token 被竊後帳號接管 |
| 🔴 P0 | UserSelfController 回傳 DTO 而非 Entity | 防止 passwordHash 洩漏 |
| 🟠 P1 | updateUser/disableUser/softDeleteUser 加入 DataScope 檢核 | 防止越權操作 |
| 🟠 P1 | UserAdminController 加入 @PreAuthorize | 多層防禦 |
| 🟠 P1 | change-password 加入速率限制 | 防止暴力破解 |
| 🟡 P2 | 修復 updateOwnProfile 未處理通知旗標 | 功能正確性 |
| 🟡 P2 | EditUserView 改用 listAssignableRoles | 前端權限一致性 |
| 🟡 P2 | EditUserView 改用單筆 API 取得使用者 | 效能 |
| ⚪ P3 | N+1 查詢最佳化 | 效能 |
| ⚪ P3 | 密碼加入特殊字元要求 | 強化安全 |

---

## 架構總評

| 維度 | 評分 | 說明 |
|------|------|------|
| 安全性 | 7/10 | 密碼加密、審計日誌、DataScope 概念都到位，但 changePassword 缺舊密碼驗證、Entity 洩漏敏感資料、部分端點缺權限檢核是主要扣分點。 |
| 正確性 | 7.5/10 | 核心 CRUD 流程正確，但 updateOwnProfile 漏處理通知旗標、EditUserView 使用錯誤 API 等為小缺陷。 |
| 可維護性 | 8/10 | 分層清晰，DTO/Entity/Service/Controller 職責明確。測試覆蓋率不錯。N+1 和 PageResponse 位置是小問題。 |
| 可觀測性 | 8.5/10 | 雙重審計（UserAuditService + AuditEvent）、結構化日誌，操作可追溯性強。 |
| 前端品質 | 8/10 | UI 表單驗證完整、確認對話框、i18n 國際化、狀態管理合理。主要問題在 EditUserView 的效能和角色 API 選擇。 |
