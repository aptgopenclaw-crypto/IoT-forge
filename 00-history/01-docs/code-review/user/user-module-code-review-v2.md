# User 模組 Code Review & Security Review v2

> 本文件為 [user-module-code-review.md](user-module-code-review.md) 的後續複查與擴充。  
> 重點：(1) 驗證 v1 全部 15 項議題的修復狀態 (2) 新增本輪發現的問題 (3) 提出值得優化的功能建議。  
> 複查日期：2026-05-27。  
> 審查範圍：`backend/src/main/java/com/taipei/iot/user/` 全部 controller / service / entity / repository / dto、跨模組整合（`auth/UserEntity`、`UserTenantMappingEntity`、`DataScopeHelper`、`RoleService.isRoleAssignable`、`SecurityConfig`）、前端 `frontend/src/views/admin/*.vue` 與 `views/user/*.vue`。

---

## 一、整體評價

| 維度       | v1 評分 | v2 修復前 | v2 修復後 | 變化 |
| ---------- | ------- | --------- | --------- | ---- |
| 安全性     | 7.0/10  | 7.0/10 | **8.5/10** | ⬆ 1.5 — 舊密碼驗證、`@JsonIgnore` 防 Entity 洩漏、change-password 速率限制、updateUser/disable/delete 全補 DataScope、特殊字元密碼強制 |
| 正確性     | 7.5/10  | 7.5/10 | **8.5/10** | ⬆ 1.0 — `notifySmsFlag` / `notifyEmailFlag` 已生效；`EditUserView` 改用單筆 `getUser` + `listAssignableRoles` |
| 可維護性   | 8.0/10  | 8.0/10 | **8.4/10** | ⬆ 0.4 — `UserTenantMappingRepository` 加 `JOIN FETCH` + listUsers 部門名稱 batch fetch（N-2 ✅）解 N+1；`PageResponse` 仍在 user 模組、雙重審計仍存在 |
| 可觀測性   | 8.5/10  | 8.5/10 | **8.5/10** | ↔ `UserAuditService` + `@AuditEvent` 雙寫未整併，但追蹤性完整 |
| 前端品質   | 8.0/10  | 8.0/10 | **9.0/10** | ⬆ 1.0 — 密碼正則改用動態 policy API（N-9 ✅） |
| **總分**   | **7.8/10** | **7.8/10** | **8.5/10** | ⬆ 0.7 |

v1 共識別 15 項議題，本輪逐檔對照後 **12 項 ✅ 已修復、1 項 ⚠ 部分改善、2 項刻意保留為低優先未處理**（見 §二）。本輪 v2 新發現的問題集中在：
- **跨租戶權限指派的縱深防禦缺口**（N-1）——`addTenantRole` 雖然 Controller 層限 SUPER_ADMIN，但 Service 層完全沒有 `isRoleAssignable` / `isDeptInScope` / `tenant exists` 三道檢查，與 `createUser` 的標準不一致。
- **與近期 tenant.enabled 守門邏輯的整合缺口**（N-3）——使用者查詢未過濾 disabled tenant 下的 mapping，與 [AuthServiceImpl 的 login gate](../../../backend/src/main/java/com/taipei/iot/auth/service/impl/AuthServiceImpl.java) 規則不一致。
- **changePassword 缺 session invalidation**（N-4）——已驗證舊密碼但未撤銷其他裝置的 refresh token，與 auth v2 [N-7 user_session](../auth/auth-module-code-review-v2.md) 機制未串接。
- **若干 N+1、TOCTOU、DTO 細節**（N-2、N-5、N-6 ~ N-10）。

**結論**：v1 主要安全議題已全數修復，模組成熟度顯著提升；本輪建議優先處理 **N-1 / N-3 / N-4**，其餘為可依序處理的健壯性改善。

---

## 二、v1 議題複查

| # | v1 議題 | 狀態 | 證據 |
| - | ------- | ---- | ---- |
| 1 | `changePassword` 缺舊密碼驗證 | ✅ 已修復 | [ChangePasswordRequest.java](../../../backend/src/main/java/com/taipei/iot/user/dto/request/ChangePasswordRequest.java) 加入 `oldPassword` 欄位；[UserSelfService.java](../../../backend/src/main/java/com/taipei/iot/user/service/UserSelfService.java) `changePassword()` 進入後立刻 `passwordEncoder.matches(req.getOldPassword(), user.getPasswordHash())`，失敗丟 `OLD_PASSWORD_INCORRECT` |
| 2 | `change-password` 端點缺速率限制 | ✅ 已修復 | [UserSelfController.java](../../../backend/src/main/java/com/taipei/iot/user/controller/UserSelfController.java) `@RateLimit(key="change-password", limit=5, period=300)` |
| 3 | `UserAdminController` 缺 `@PreAuthorize` | ✅ 已修復 | [UserAdminController.java](../../../backend/src/main/java/com/taipei/iot/user/controller/UserAdminController.java) 全部端點皆有 `@PreAuthorize("hasAuthority('USER_*')")` 對應權限碼 |
| 4 | `updateUser` 缺 DataScope 檢核 | ✅ 已修復 | [UserAdminService.java](../../../backend/src/main/java/com/taipei/iot/user/service/UserAdminService.java) `updateUser()` 進入後先 `dataScopeHelper.isDeptInScope(targetDeptId)` 驗證目標部門；若有改 `roleId` 額外呼叫 `roleService.isRoleAssignable()` |
| 5 | `disableUser` / `softDeleteUser` 缺 DataScope | ✅ 已修復 | [UserAdminService.java](../../../backend/src/main/java/com/taipei/iot/user/service/UserAdminService.java) `disableUser()` / `softDeleteUser()` 兩處均補上 DataScope 驗證 |
| 6 | `UserSelfController` 回傳 `UserEntity` 含 `passwordHash` | ✅ 已修復 | [UserEntity.java](../../../backend/src/main/java/com/taipei/iot/auth/entity/UserEntity.java) `passwordHash` / `loginFailCount` / `isSuperAdmin` 等敏感欄位加 `@JsonIgnore`；JSON 序列化已不再外洩 |
| 7 | `PasswordValidator` 缺特殊字元要求 | ✅ 已修復 | [PasswordValidator.java](../../../backend/src/main/java/com/taipei/iot/user/service/PasswordValidator.java) 定義 `SPECIAL_CHARS` 常數，依 `policy.isRequireSpecial()` 強制驗證 |
| 8 | `userId` 路徑參數無格式驗證 | ✅ 已修復 | [UserAdminController.java](../../../backend/src/main/java/com/taipei/iot/user/controller/UserAdminController.java) 所有 `@PathVariable String userId` 加上 `@Pattern(regexp="^[a-f0-9-]{36}$")` UUID 驗證 |
| 9 | `updateOwnProfile` 未處理 `notifySmsFlag` / `notifyEmailFlag` | ✅ 已修復 | [UserSelfService.java](../../../backend/src/main/java/com/taipei/iot/user/service/UserSelfService.java) `updateOwnProfile()` 已加 `if (req.getNotifySmsFlag() != null) user.setNotifySmsFlag(...)` 與對應 email flag |
| 10 | `EditUserView` 載入全部使用者再 `find` | ✅ 已修復 | [EditUserView.vue](../../../frontend/src/views/admin/EditUserView.vue) 改用 `getUser(userId)` 單筆 API；後端 [UserAdminController.java](../../../backend/src/main/java/com/taipei/iot/user/controller/UserAdminController.java) 新增 `GET /v1/auth/users/{userId}` |
| 11 | `EditUserView` 用 `listRoles` 而非 `listAssignableRoles` | ✅ 已修復 | [EditUserView.vue](../../../frontend/src/views/admin/EditUserView.vue) 已改 `listAssignableRoles()` 與 Create 頁一致 |
| 12 | `createUser` UUID 未做唯一性確認 | ⚪ 接受 | 不修；`UUID.randomUUID()` 碰撞機率 ≈ 10⁻¹⁸ 且 DB PK 為 unique constraint 兜底，過度防禦無實質效益 |
| 13 | `toUserListItemDto` 存在 N+1 | ⚠ 部分改善 | [UserTenantMappingRepository.java](../../../backend/src/main/java/com/taipei/iot/user/repository/UserTenantMappingRepository.java) 對 `user` / `role` 加 `JOIN FETCH` 解決主要關聯；**但 `deptId → deptName` 查找仍在迴圈內逐筆 `deptInfoRepository.findById()`**（見 N-2）|
| 14 | `UserAuditService` 與 `@AuditEvent` 重複 | ❌ 未改 | 仍同時存在 `UserAuditService.logAction()` 手動寫入 `user_info_log` + `@AuditEvent` 觸發通用審計，產生兩份不同格式紀錄 |
| 15 | `PageResponse<T>` 位置不對 | ❌ 未改 | 仍位於 user 模組；其他模組（如 announcement）已自行重複實作 |

> **小結**：v1 共 15 項，**12 ✅ 修復 / 1 ⚠ 部分 / 2 ❌ 未改**（後 2 項為可維護性低優項目，建議排入後續重構 Sprint）。

---

## 三、本輪新發現問題

### 🔴 高風險

#### N-1. `addTenantRole` Service 層缺三道安全檢核（縱深防禦缺口） ✅ 已修復

- **檔案**：[UserAdminService.java](../../../backend/src/main/java/com/taipei/iot/user/service/UserAdminService.java)（`addTenantRole`）
- **問題**：Controller 層雖限定 `@PreAuthorize("hasRole('SUPER_ADMIN')")`，但 Service 完全未檢查：
  1. `req.getRoleId()` 是否可由呼叫者指派 → **缺 `roleService.isRoleAssignable()`**
  2. `req.getDeptId()` 是否在呼叫者 DataScope 內 → **缺 `dataScopeHelper.isDeptInScope()`**
  3. `req.getTenantId()` 是否確實存在 → **缺 `tenantRepository.existsById()`**
- **修復內容**：
  - 注入 `TenantRepository`，在 `addTenantRole` 解析 tenantId 後依序加入三道檢核：
    1. `tenantRepository.existsById(tenantId)` → 拋 `TENANT_NOT_FOUND`
    2. `roleService.isRoleAssignable(req.getRoleId())` → 拋 `PERMISSION_DENIED("無權指派該角色")`
    3. `dataScopeHelper.isDeptInScope(req.getDeptId())` → 拋 `PERMISSION_DENIED("無權在該部門新增場域角色")`
  - 與 `createUser()` 安全模型一致。
- **測試**：`UserAdminServiceTest` 新增 3 案例：
  - `addTenantRole_tenantNotFound_shouldThrow`
  - `addTenantRole_roleNotAssignable_shouldThrowPermissionDenied`
  - `addTenantRole_deptNotInScope_shouldThrowPermissionDenied`
- **優先級**：🔴 高（縱深防禦原則；單一守門被攻破即裸奔）

---

#### N-2. `toUserListItemDto` 仍有 `deptId → deptName` 的 N+1 查詢 ✅ 已修復

- **檔案**：[UserAdminService.java](../../../backend/src/main/java/com/taipei/iot/user/service/UserAdminService.java)（`toUserListItemDto`、`listUsers`）
- **問題**：v1 #13 的修復僅針對 `user` / `role` 加 `JOIN FETCH`，但部門名稱仍在 mapping → DTO 轉換迴圈內以 `deptInfoRepository.findById(mapping.getDeptId())` 取得：

  ```java
  if (mapping.getDeptId() != null) {
      deptName = deptInfoRepository.findById(mapping.getDeptId())
              .map(DeptInfoEntity::getDeptName)
              .orElse(null);
  }
  ```

  列表回傳 100 筆使用者 → 多打 100 次 dept DB 查詢。
- **修復內容**（採方案 B：service 層 batch fetch + Map，避免 entity 跨模組耦合擴大）：
  - `listUsers` 在進入 stream map 前，先以 `resolveDeptNameMap(mappingPage.getContent())` 收集 distinct deptIds，呼叫 `deptInfoRepository.findAllById(...)` 一次取回。
  - `toUserListItemDto` 改為接受 `Map<Long, String> deptNameMap`，迴圈內以 map.get 取值；保留單筆 fallback（`getUser` / `createUser` 等單筆呼叫路徑用 `Collections.emptyMap()` 重載，map miss 時 fallback 至 `findById`）。
  - 結果：列表查詢的 dept 查詢從 N 次降為 1 次。
- **測試**：`UserAdminServiceTest.listUsers_batchFetchesDeptNames_noNPlusOne` 驗證：
  - 3 筆 mapping、2 個 distinct deptId → 僅 1 次 `findAllById` 呼叫
  - 絕不退化為 per-row `findById`（`verify(deptInfoRepository, never()).findById(anyLong())`）
  - deptName 正確映射
- **優先級**：🔴 高（量級隨使用者數線性放大；大租戶分頁頁面延遲顯著）

---

#### N-3. 使用者查詢未過濾 `tenant.enabled = false` 的 mapping（與 login gate 不一致） ✅ 已修復

- **檔案**：[UserTenantMappingRepository.java](../../../backend/src/main/java/com/taipei/iot/auth/repository/UserTenantMappingRepository.java)、[UserAdminService.java](../../../backend/src/main/java/com/taipei/iot/user/service/UserAdminService.java)
- **背景**：本專案近期已於 [AuthServiceImpl](../../../backend/src/main/java/com/taipei/iot/auth/service/impl/AuthServiceImpl.java) 加入「停用場域底下的帳號禁止登入」守門。但 user 模組查詢 mapping 時未過濾 `tenant.enabled = true`。
- **修復內容**（採嚴格方案，與 login gate 行為對齊）：
  1. `UserTenantMappingRepository`：4 個 `findActive*` 查詢全部加 `JOIN m.tenant t` + `AND t.enabled = true`，停用場域的使用者不再出現在列表中。
  2. 新增 `findByUserIdAndTenantEnabled(userId)` 查詢——只回傳 `tenant.enabled = true` 的 mapping。
  3. `UserAdminService.getUserTenantMappings()` 改用 `findByUserIdAndTenantEnabled`，停用場域的 mapping 不再回傳。
  4. `addTenantRole()` 的場域檢核從 `existsById` 改為 `findById` + 驗證 `tenant.enabled = true`，停用場域不可新增 mapping（拋 `TENANT_DISABLED`）。
- **測試**：`UserAdminServiceTest` 新增 2 案例：
  - `addTenantRole_tenantDisabled_shouldThrow` — 停用場域拋 `TENANT_DISABLED`
  - `getUserTenantMappings_shouldOnlyReturnEnabledTenantMappings` — 驗證僅用 `findByUserIdAndTenantEnabled`，不呼叫 `findByUserId`
- **優先級**：🔴 高（一致性 + 與近期安全決策對齊）

---

### 🟠 中風險

#### N-4. `changePassword` 成功後未撤銷其他裝置的 refresh token ✅ 已修復

- **檔案**：[UserSelfService.java](../../../backend/src/main/java/com/taipei/iot/user/service/UserSelfService.java)（`changePassword`）、[UserSelfController.java](../../../backend/src/main/java/com/taipei/iot/user/controller/UserSelfController.java)、[UserSessionServiceImpl.java](../../../backend/src/main/java/com/taipei/iot/auth/service/impl/UserSessionServiceImpl.java)
- **修復內容**：
  1. `UserSelfController.changePassword()` 新增 `@CookieValue(name = "refresh_token", required = false)` 參數，透過 `JwtUtil.extractJti()` 取得當前 session JTI。
  2. `UserSelfService.changePassword()` 簽名擴充為 `(String currentUserId, ChangePasswordRequest req, String currentSessionJti)`，成功後呼叫 `userSessionService.revokeAllExceptCurrent(currentUserId, currentSessionJti)`。
  3. `UserSessionServiceImpl.revokeAllExceptCurrent()` 實作：查詢所有活躍 session → 排除當前 JTI → 批次 DB revoke + Redis blacklist（best-effort）。若 JTI 為 null 則呼叫 `revokeAll`。
  4. `UserSessionRepository` 新增 `revokeAllByUserIdExcept` / `revokeAllByUserId` 兩個 `@Modifying` 查詢。
- **測試**：`UserSelfServiceTest` 新增 2 案例：
  - `changePassword_success` 驗證呼叫 `revokeAllExceptCurrent(userId, jti)`
  - `changePassword_withNullJti_shouldRevokeAllSessions` 驗證 JTI 為 null 時呼叫 `revokeAll`
- **連動**：落實 auth v2 [F-9 密碼變更後 logout-all](../auth/auth-module-code-review-v2.md)。
- **優先級**：🟠 中

---

#### N-5. `createUser` email 唯一性檢查存在 TOCTOU 競態 ✅ 已修復

- **檔案**：[UserAdminService.java](../../../backend/src/main/java/com/taipei/iot/user/service/UserAdminService.java)（`createUser`）
- **修復內容**：保留前置 `existsByEmail` 檢查（給多數正常情境清晰錯誤），同時對 `userRepository.save(user)` 加 `catch (DataIntegrityViolationException)`，轉換為 `BusinessException(ErrorCode.USER_ALREADY_EXISTS)`，確保併發下仍回傳一致的友善錯誤碼而非 500。
- **測試**：`UserAdminServiceTest` 新增 1 案例：
  - `createUser_concurrentDuplicate_shouldCatchDataIntegrityViolation` — 模擬前置檢查通過但 save 時 DB 觸發 unique constraint，驗證回傳 `USER_ALREADY_EXISTS`
- **優先級**：🟠 中

---

#### N-6. `ChangePasswordRequest.newPassword` 缺 `@NotBlank` ✅ 已修復

- **檔案**：[ChangePasswordRequest.java](../../../backend/src/main/java/com/taipei/iot/user/dto/request/ChangePasswordRequest.java)
- **修復內容**：`newPassword` 欄位已加上 `@NotBlank` + `@Size(min = 8)`，空字串、全空白、null 均在 controller 層即返回 400，不會進入 service。
- **測試**：`UserSelfControllerTest`（`@WebMvcTest`）新增 4 案例：
  - `changePassword_blankNewPassword_shouldReturn400` — 全空白返回 400
  - `changePassword_emptyNewPassword_shouldReturn400` — 空字串返回 400
  - `changePassword_nullNewPassword_shouldReturn400` — null 返回 400
  - `changePassword_validRequest_shouldReturn200` — 正常請求通過驗證
- **優先級**：🟠 中（屬輸入驗證一致性）

---

### 🟡 低風險 / 建議

#### N-7. 軟刪除使用者無「還原」端點 ✅ 已修復

- **檔案**：[UserAdminController.java](../../../backend/src/main/java/com/taipei/iot/user/controller/UserAdminController.java)、[UserAdminService.java](../../../backend/src/main/java/com/taipei/iot/user/service/UserAdminService.java)、[AuditEventType.java](../../../backend/src/main/java/com/taipei/iot/audit/enums/AuditEventType.java)
- **修復內容**：
  1. `AuditEventType` 新增 `RESTORE_USER("RESTORE_USER", AuditCategory.ACCOUNT, true)`。
  2. `UserAdminService.restoreUser()` — 驗證使用者存在且確實已軟刪除，否則拋 `VALIDATION_ERROR`；還原時設 `deleted=false`、`deletedAt=null`、`enabled=true`；寫入審計日誌。
  3. `UserAdminController` 新增 `PATCH /v1/auth/users/{userId}/restore`，限 `SUPER_ADMIN` 角色，掛 `@AuditEvent(RESTORE_USER)`。
- **測試**：`UserAdminServiceTest` 新增 3 案例：
  - `restoreUser_success` — 正常還原，驗證 deleted/enabled 狀態與審計日誌
  - `restoreUser_userNotFound_shouldThrow` — 使用者不存在拋 USER_NOT_FOUND
  - `restoreUser_notDeleted_shouldThrowValidationError` — 未刪除者拋 VALIDATION_ERROR

#### N-8. `PasswordValidator.maxLength = 0` 等於無上限 ✅ 已修復

- **檔案**：[PasswordValidator.java](../../../backend/src/main/java/com/taipei/iot/user/service/PasswordValidator.java)
- **修復內容**：新增 `static final int HARD_MAX = 1024`，在 policy-based maxLength 檢查之前無條件執行。無論 `policy.getMaxLength()` 是否為 0，超過 1024 字元即拋 `RESET_PASSWORD_ERROR`，防止 BCrypt CPU DoS。
- **測試**：`PasswordValidatorTest` 新增 3 案例：
  - `validate_exceedsHardMax_shouldThrow` — 1025 字元被拒（即使 policy.maxLength=0）
  - `validate_exactlyHardMax_shouldPass` — 1024 字元正常通過
  - `validate_policyMaxLengthStillEnforced_withinHardMax` — policy.maxLength=128 仍然生效

#### N-9. 前端密碼正則硬編，未與後端 policy 同步 ✅

- **檔案**：[CreateUserView.vue](../../../frontend/src/views/admin/CreateUserView.vue)、[ChangePasswordView.vue](../../../frontend/src/views/user/ChangePasswordView.vue)
- **問題**：前端規則 `/^(?=.*[a-z])(?=.*[A-Z])(?=.*\d).{8,}$/` 硬編，未對齊 policy 是否啟用特殊字元。若後端 `requireSpecial=true`，使用者輸入符合前端的密碼仍會被後端拒絕；反之若 `requireSpecial=false` 又 `minLength=10`，前端 8 字元放行但後端拒。
- **建議**：新增 `GET /v1/auth/password-policy` 由前端動態取得規則並渲染提示 / 即時校驗。
- **修復內容**：新增 `usePasswordPolicy` composable（`frontend/src/composables/usePasswordPolicy.ts`），透過 `GET /v1/noauth/password-policy/describe` 動態取得後端 policy，取代硬編正則。`ChangePasswordView.vue` 與 `CreateUserView.vue` 皆改用 `validatePassword()` 做即時校驗，`CreateUserView` 另依據選定 tenantId 載入對應租戶 policy。
- **測試**：`usePasswordPolicy.test.ts` 新增 7 案例：
  - `fetches policy on mount and exposes validatePassword` — 各項 complexity 規則驗證
  - `returns true when policy has not loaded` — 未載入時放行（defer to backend）
  - `enforces maxLength when set` — maxLength 超過時拒絕
  - `skips maxLength check when maxLength is 0` — maxLength=0 不限制
  - `reloads when tenantId changes` — tenantId 切換時重載 policy
  - `does not require special when requireSpecial is false` — 動態尊重後端設定
  - `exposes descriptions from the policy` — 公開 describe 欄位

#### N-10. `UserAuditService` 與 `@AuditEvent` 雙寫未整併（v1 #14 延續） ✅

- **檔案**：[UserAuditService.java](../../../backend/src/main/java/com/taipei/iot/user/service/UserAuditService.java)、[UserAdminController.java](../../../backend/src/main/java/com/taipei/iot/user/controller/UserAdminController.java)、[UserAdminService.java](../../../backend/src/main/java/com/taipei/iot/user/service/UserAdminService.java)
- **問題**：同一操作（create/update/disable/delete）產生 `user_info_log` 一筆 + `audit_log`（通用模組）一筆，格式不同、欄位不對齊；長期維護成本高。
- **修復方式**（方案 A）：
  1. **移除 `@AuditEvent`**：`UserAdminController` 的 5 個寫入端點（CREATE_USER / UPDATE_USER / DISABLE_USER / SOFT_DELETE_USER / RESTORE_USER）全數移除 `@AuditEvent` 註解，消除雙寫。
  2. **強化 `UserAuditService`**：新增 `logChange(actionType, actionUserId, targetUserId, summary, before, after)` 方法，以 `Map<String, String>` 記錄 before/after diff，序列化進 `detail` 欄位（格式：`summary [field: old → new; ...]`），超過 1000 字元自動截斷。
  3. **`updateUser` 補 diff**：`UserAdminService.updateUser()` 改用 `logChange`，修改前先擷取舊值（displayName / phone / roleId / deptId），修改後傳入新值，audit trail 明確記錄每次異動的欄位差異。
- **測試**：[UserAuditServiceTest.java](../../../backend/src/test/java/com/taipei/iot/user/service/UserAuditServiceTest.java)（7 個測試）：
  - `logAction_shouldSaveLogEntity` — 基本 logAction 存檔正確
  - `logChange_withDiff_shouldIncludeBeforeAfterInDetail` — diff 正確編碼進 detail
  - `logChange_withEmptyBefore_shouldOnlyIncludeSummary` — 無變更時只存 summary
  - `logChange_withNullBefore_shouldOnlyIncludeSummary` — null before 不噴錯
  - `buildDiffDetail_shouldTruncateAt1000Chars` — 超長 detail 截斷為 1000 字元
  - `userAdminController_shouldNotHaveAuditEventAnnotations` — 反射驗證 controller 無 @AuditEvent
  - `userModule_shouldNotHaveOwnPageResponse` — 反射驗證 user 模組已無自有 PageResponse

#### N-11. `PageResponse<T>` 仍在 user 模組（v1 #15 延續） ✅

- **檔案**：`user/dto/response/PageResponse.java`（已刪除）
- **問題**：原為 `@Deprecated` 空子類繼承 `com.taipei.iot.common.dto.PageResponse`，實際已無任何 import 引用。
- **修復內容**：直接刪除 `user/dto/response/PageResponse.java`。全部 16 處引用均已使用 `com.taipei.iot.common.dto.PageResponse`，無需遷移。
- **測試**：`UserAuditServiceTest.userModule_shouldNotHaveOwnPageResponse` — 反射驗證該類已不存在，防止重新引入。

---

## 四、安全性總結

| 面向 | v1 | v2 | 變化原因 |
|------|----|----|----------|
| 認證 / 授權 | ⚠ 部分 | ✅ 良好 | `@PreAuthorize` 全覆蓋 + DataScope 補齊 |
| 越權防護 | ⚠ 部分 | ✅ 完備 | createUser / updateUser / disable / delete / **addTenantRole** 全補三道 service 層檢核（N-1 ✅） |
| 敏感資料外洩 | ⚠ 中風險 | ✅ 良好 | `UserEntity` 敏感欄位全 `@JsonIgnore` |
| 密碼強度 | ✅ 良好 | ✅ 良好 + | 加入特殊字元要求；history 比對保留 |
| 暴力破解防護 | ⚠ 低風險 | ✅ 良好 | change-password rate limit 已加 |
| 帳號接管防護 | ❌ 高風險 | ✅ 良好 | 舊密碼驗證已加；**changePassword 後 revokeAllExceptCurrent 已實作 ✅（N-4）** |
| 多租戶隔離 | ✅ 良好 | ✅ 良好 | **N-3 list 查詢已加 tenant.enabled 過濾；addTenantRole 已驗證 tenant 啟用狀態 ✅** |
| 輸入驗證 | ✅ 良好 | ✅ 良好 | userId UUID Pattern；email/phone 既有；**N-6 `@NotBlank` 已加 ✅**；**N-8 `HARD_MAX=1024` 已加 ✅** |

### OWASP Top 10 對照（v2）

| 類別 | v1 | v2 | 備註 |
|------|----|----|----|
| A01 Broken Access Control | ⚠ 中 | ✅ 良好 | DataScope 全覆蓋；N-1 已修復 ✅ |
| A02 Cryptographic Failures | ✅ | ✅ | BCrypt 未變 |
| A03 Injection | ✅ | ✅ | JPA 參數化 |
| A04 Insecure Design | ⚠ 中 | ✅ | 舊密碼驗證已加；N-4 為延伸 |
| A05 Security Misconfiguration | ✅ | ✅ | 沿用全域設定 |
| A07 Auth Failures | ⚠ 低 | ✅ | rate limit 已加 |
| A08 Data Integrity | ✅ | ✅ | DTO `@Valid` 全層 |
| A09 Logging Failures | ✅ | ✅ + | 仍是雙寫但完整 |

---

## 五、值得優化的功能建議

| # | 功能 | 價值 | 概要 |
| - | ---- | ---- | ---- |
| F-1 | **批量使用者匯入 / 匯出 (CSV/Excel)** | 維運 ★★★ | 新場域 onboarding 一次匯入 N 名使用者；前端預覽校驗後送批次 API；支援部分失敗報告 |
| F-2 | **帳號鎖定看板 + 一鍵解鎖** | 維運 + 安全 ★★★ | Admin 列表顯示登入失敗鎖定的帳號 / 鎖定原因 / 解鎖按鈕；目前無 UI，僅能改 DB |
| F-3 | **使用者操作時間軸** | UX + 稽核 ★★ | 整合 `user_info_log` + `audit_log` 顯示單一使用者的完整操作序列；對客戶詢問非常有用 |
| F-4 | **密碼到期前通知** | 安全 + UX ★★★ | 排程於到期前 14 / 7 / 1 天寄信；目前僅在登入時被擋下 |
| F-5 | **強制登出單一使用者** | 安全應急 ★★★ | 與 auth v2 N-7 `user_session` 串接，admin 可一鍵踢出指定 user 所有裝置；緊急事件必備 |
| F-6 | **Admin Impersonation（代登入）** | 客服支援 ★★★ | Admin 暫以指定 user 身分操作以重現問題；所有操作以 `impersonatedBy` 額外標記稽核 |
| F-7 | ~~**帳號還原端點**~~ | ~~維運 ★★~~ | ✅ 已實作（N-7）；`PATCH /v1/auth/users/{userId}/restore` 限 SUPER_ADMIN |
| F-8 | **使用者頭像** | UX ★ | 上傳 + 縮圖；可整合既有 attachment 機制 |
| F-9 | ~~**動態密碼政策端點**~~ | ~~UX ★★~~ | ✅ 已實作（N-9）；前端 `usePasswordPolicy` composable 動態取 `GET /v1/noauth/password-policy/describe` |
| F-10 | **SSO / OIDC 帳號自動同步** | 企業整合 ★★★ | 配合 auth v2 F-10；首登自動建立 `UserEntity` + tenant mapping |

---

## 六、修復路線圖建議

### Sprint 1 — 縱深防禦與一致性
1. ~~**N-1** — `addTenantRole` 補三項檢核（`tenantExists` / `isRoleAssignable` / `isDeptInScope`）。~~ ✅
2. ~~**N-3** — 統一 user 模組查詢過濾 `tenant.enabled = true`，與 login gate 對齊；UI 配套標示。~~ ✅
3. ~~**N-4** — `changePassword` 成功後呼叫 `userSessionService.revokeAllExceptCurrent`；同步落實 auth v2 F-9。~~ ✅

### Sprint 2 — 健壯性
4. ~~**N-2** — `toUserListItemDto` 部門名稱以 batch fetch + Map 解 N+1。~~ ✅
5. ~~**N-5** — `createUser` 加 `DataIntegrityViolationException` catch，回傳 `USER_ALREADY_EXISTS`。~~ ✅
6. ~~**N-6** — `ChangePasswordRequest.newPassword` 加 `@NotBlank`。~~ ✅
7. ~~**N-8** — `PasswordValidator` 加硬性 `HARD_MAX = 1024`。~~ ✅

### Sprint 3+ — 細節與重構
8. ~~**N-7** 軟刪除 restore 端點 + F-7 連動。~~ ✅
9. ~~**N-9** + F-9 — `GET /v1/auth/password-policy` 並讓前端動態取規則。~~ ✅
10. ~~**N-10**（v1 #14）— 審計雙寫整併（方案 A：移除 @AuditEvent + user_info_log 加 diff）。~~ ✅
11. ~~**N-11**（v1 #15）— `PageResponse` 移到 common（已確認完成，刪除空殼子類）。~~ ✅
12. F-1 ~ F-6 視優先級排入。

---

## 七、附錄：本次複查涵蓋的檔案

### Controller
- [UserAdminController.java](../../../backend/src/main/java/com/taipei/iot/user/controller/UserAdminController.java)
- [UserSelfController.java](../../../backend/src/main/java/com/taipei/iot/user/controller/UserSelfController.java)

### Service
- [UserAdminService.java](../../../backend/src/main/java/com/taipei/iot/user/service/UserAdminService.java)
- [UserSelfService.java](../../../backend/src/main/java/com/taipei/iot/user/service/UserSelfService.java)
- [UserAuditService.java](../../../backend/src/main/java/com/taipei/iot/user/service/UserAuditService.java)
- [PasswordValidator.java](../../../backend/src/main/java/com/taipei/iot/user/service/PasswordValidator.java)

### Entity
- [UserEntity.java](../../../backend/src/main/java/com/taipei/iot/auth/entity/UserEntity.java)
- [UserTenantMappingEntity.java](../../../backend/src/main/java/com/taipei/iot/auth/entity/UserTenantMappingEntity.java)
- [UserInfoLogEntity.java](../../../backend/src/main/java/com/taipei/iot/user/entity/UserInfoLogEntity.java)
- [PasswordHistoryEntity.java](../../../backend/src/main/java/com/taipei/iot/user/entity/PasswordHistoryEntity.java)

### Repository
- [UserTenantMappingRepository.java](../../../backend/src/main/java/com/taipei/iot/user/repository/UserTenantMappingRepository.java)
- [UserInfoLogRepository.java](../../../backend/src/main/java/com/taipei/iot/user/repository/UserInfoLogRepository.java)
- [PasswordHistoryRepository.java](../../../backend/src/main/java/com/taipei/iot/user/repository/PasswordHistoryRepository.java)

### DTO
- [CreateUserRequest.java](../../../backend/src/main/java/com/taipei/iot/user/dto/request/CreateUserRequest.java)
- [UpdateUserRequest.java](../../../backend/src/main/java/com/taipei/iot/user/dto/request/UpdateUserRequest.java)
- [ChangePasswordRequest.java](../../../backend/src/main/java/com/taipei/iot/user/dto/request/ChangePasswordRequest.java)
- [UpdateOwnProfileRequest.java](../../../backend/src/main/java/com/taipei/iot/user/dto/request/UpdateOwnProfileRequest.java)
- [AddTenantRoleRequest.java](../../../backend/src/main/java/com/taipei/iot/user/dto/request/AddTenantRoleRequest.java)
- `UserListItemDto.java`、`UserTenantMappingDto.java`、`PageResponse.java`

### 跨模組整合
- [SecurityConfig.java](../../../backend/src/main/java/com/taipei/iot/config/SecurityConfig.java)（`/v1/auth/users/**` 與 `/v1/auth/user/**` 路由規則）
- [DataScopeHelper.java](../../../backend/src/main/java/com/taipei/iot/dept/helper/DataScopeHelper.java)
- [RoleService.java](../../../backend/src/main/java/com/taipei/iot/rbac/service/RoleService.java)（`isRoleAssignable`）
- [AuthServiceImpl.java](../../../backend/src/main/java/com/taipei/iot/auth/service/impl/AuthServiceImpl.java)（tenant.enabled gate 參考）

### 測試
- `UserAdminServiceTest`、`UserSelfServiceTest`、`UserAdminControllerTest`、`PasswordValidatorTest`

### 前端
- [CreateUserView.vue](../../../frontend/src/views/admin/CreateUserView.vue)
- [EditUserView.vue](../../../frontend/src/views/admin/EditUserView.vue)
- [UserListView.vue](../../../frontend/src/views/admin/UserListView.vue)
- [ProfileView.vue](../../../frontend/src/views/user/ProfileView.vue)
- [ChangePasswordView.vue](../../../frontend/src/views/user/ChangePasswordView.vue)
- [api/user/index.ts](../../../frontend/src/api/user/index.ts)
- [types/user.ts](../../../frontend/src/types/user.ts)
