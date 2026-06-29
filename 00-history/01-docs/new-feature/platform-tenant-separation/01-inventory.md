# Platform / Tenant 職責分離 — 第一階段盤點

> 目的：為「super_admin 只處理平台層、不碰租戶資料」的架構重構，建立現況基準與分類依據。
>
> 產出日期：2026-05-31
> 範圍：Spring Boot backend + Vue.js frontend
> 狀態：盤點完成，待 ADR 決議

---

## 0. 重構目標回顧

| 角色 | 應該管 | 不該碰 |
|------|-------|-------|
| **Super Admin**（平台層） | 租戶 CRUD、訂閱、平台設定、全域稽核、系統健康 | 任何單一租戶的部門、使用者、角色、公告、業務資料 |
| **Tenant Admin / User**（租戶層） | 自己租戶內的部門、使用者、角色、公告、選單、稽核 | 其他租戶資料、平台設定、租戶本身的建立/刪除 |

例外通道：**Impersonation**（受控、可稽核、有時限）。

---

## 1. 現況摘要

| 項目 | 數量 | 備註 |
|------|------|------|
| REST endpoints | 95+ | 跨 16 個 Controller |
| 權限代碼 | 28 | Platform 4 + Tenant 24 |
| 角色 | 5 | SUPER_ADMIN / ADMIN / OPERATOR / VIEWER / DEPT_ADMIN |
| 前端靜態路由 | 21 | NoAuth 5 + Public 5 + Admin 11 |
| DB 選單 | 35+ | 已有 `scope` 欄位（V59 加入） |

**已具備的基礎**：
- ✅ `PLATFORM_*` 權限已存在（V58）
- ✅ 選單 `scope`（PLATFORM / TENANT / PUBLIC）已就位
- ✅ 部分 platform endpoint 已用 `/v1/admin/*`、`/v1/platform/*` 前綴
- ✅ JWT 已有 `tenantId` claim；多租戶選擇用 `temporary` token

---

## 2. 後端 Controller 與 API 盤點

### 2.1 Authentication & Session

| Endpoint | Method | Controller.Method | @PreAuthorize | 分類 | 說明 |
|---|---|---|---|---|---|
| `/v1/noauth/turnstile/config` | GET | AuthController.getTurnstileConfig | — | PLATFORM | Turnstile 設定 |
| `/v1/noauth/captcha` | POST | AuthController.generateCaptcha | — | PLATFORM | 產生圖形驗證碼 |
| `/v1/noauth/login` | POST | AuthController.login | — | PLATFORM | 登入 |
| `/v1/noauth/token/refresh` | POST | AuthController.refreshToken | — | PLATFORM | Refresh token |
| `/v1/noauth/user/forgot-password` | POST | AuthController.forgotPassword | — | PLATFORM | 寄送重設信 |
| `/v1/noauth/user/reset-password` | PUT | AuthController.resetPassword | — | PLATFORM | 重設密碼 |
| `/v1/noauth/user/force-change-password` | POST | AuthController.forceChangePassword | — | PLATFORM | 強制改密碼 |
| `/v1/auth/select-tenant` | POST | AuthController.selectTenant | isAuthenticated | SHARED | 多租戶使用者選擇租戶 |
| `/v1/auth/switch-tenant` | POST | AuthController.switchTenant | isAuthenticated | SHARED | 切換租戶 |
| `/v1/auth/logout` | POST | AuthController.logout | isAuthenticated | SHARED | 登出 |
| `/v1/auth/idle-logout` | POST | AuthController.idleTimeoutLogout | isAuthenticated | SHARED | 閒置登出 |
| `/v1/auth/user/info` | GET | AuthController.getUserInfo | isAuthenticated | SHARED | 取得自身資訊 |
| `/v1/auth/sessions` | GET | AuthController.listMySessions | isAuthenticated | SHARED | 自己的 sessions |
| `/v1/auth/sessions/{id}` | DELETE | AuthController.revokeSession | isAuthenticated | SHARED | 撤銷 session |

### 2.2 User Management

| Endpoint | Method | Controller.Method | @PreAuthorize | 分類 | 說明 |
|---|---|---|---|---|---|
| `/v1/auth/users` | GET | UserAdminController.listUsers | USER_LIST | TENANT | 使用者列表 |
| `/v1/auth/users/{id}` | GET | UserAdminController.getUser | USER_LIST | TENANT | 單一使用者 |
| `/v1/auth/users` | POST | UserAdminController.createUser | USER_CREATE | TENANT | 建立使用者 |
| `/v1/auth/users/{id}` | PUT | UserAdminController.updateUser | USER_UPDATE | TENANT | 更新使用者 |
| `/v1/auth/users/{id}` | DELETE | UserAdminController.disableUser | USER_DISABLE | TENANT | 停用使用者 |
| `/v1/auth/users/{id}/soft-delete` | PATCH | UserAdminController.softDeleteUser | USER_DELETE | TENANT | 軟刪除 |
| `/v1/auth/users/{id}/tenant-roles` | GET | UserAdminController.getUserTenantMappings | PLATFORM_USER_TENANT_MAPPING | **PLATFORM** | 跨租戶角色查詢 |
| `/v1/auth/users/{id}/tenant-roles` | POST | UserAdminController.addTenantRole | PLATFORM_USER_TENANT_MAPPING | **PLATFORM** | 指派跨租戶角色 |
| `/v1/auth/users/{id}/tenant-roles/{mid}` | DELETE | UserAdminController.removeTenantRole | PLATFORM_USER_TENANT_MAPPING | **PLATFORM** | 移除指派 |
| `/v1/auth/user/my` | PUT | UserSelfController.updateOwnProfile | isAuthenticated | SHARED | 更新自己 profile |
| `/v1/auth/user/change-password` | POST | UserSelfController.changePassword | isAuthenticated | SHARED | 改密碼 |

⚠️ **問題**：`UserAdminController` 同時混了 TENANT 與 PLATFORM 三支 endpoint，路徑卻都在 `/v1/auth/users/*`。建議拆出 `PlatformUserTenantMappingController`。

### 2.3 Role & Permission

| Endpoint | Method | Controller.Method | @PreAuthorize | 分類 | 說明 |
|---|---|---|---|---|---|
| `/v1/auth/roles` | GET | RoleController.listRoles | — | TENANT | 角色列表 |
| `/v1/auth/roles/assignable` | GET | RoleController.listAssignableRoles | — | TENANT | 可指派角色 |
| `/v1/auth/roles` | POST | RoleController.createRole | ROLE_CREATE | TENANT | 建立角色 |
| `/v1/auth/roles/{id}` | PUT | RoleController.updateRole | ROLE_UPDATE | TENANT | 更新角色 |
| `/v1/auth/roles/{id}/enabled` | PATCH | RoleController.toggleEnabled | ROLE_UPDATE | TENANT | 啟用/停用 |
| `/v1/auth/roles/{id}/permissions` | GET | RoleController.getRolePermissions | — | TENANT | 角色權限 |
| `/v1/auth/roles/{id}/permissions` | PUT | RoleController.assignPermissions | ROLE_ASSIGN_PERM | TENANT | 指派權限 |
| `/v1/auth/permissions` | GET | PermissionController.listPermissions | ROLE_LIST | TENANT | 權限清單 |

### 2.4 Menu

| Endpoint | Method | Controller.Method | @PreAuthorize | 分類 | 說明 |
|---|---|---|---|---|---|
| `/v1/auth/menus/tree` | GET | MenuController.getMenuTree | — | TENANT | 完整選單樹 |
| `/v1/auth/menus/my` | GET | MenuController.getMyMenus | isAuthenticated | SHARED | 依角色過濾的選單 |
| `/v1/auth/menus` | POST | MenuController.createMenu | — | TENANT | 建立 |
| `/v1/auth/menus` | PUT | MenuController.updateMenu | — | TENANT | 更新 |
| `/v1/auth/menus/{id}` | DELETE | MenuController.deleteMenu | — | TENANT | 刪除 |
| `/v1/auth/menus/{id}/visible` | PATCH | MenuController.toggleVisible | — | TENANT | 顯示切換 |

⚠️ **問題**：寫操作沒掛 `@PreAuthorize`，僅靠登入即可呼叫。

### 2.5 Department

| Endpoint | Method | Controller.Method | @PreAuthorize | 分類 |
|---|---|---|---|---|
| `/v1/auth/dept/list` | GET | DeptController.getDeptTree | — | TENANT |
| `/v1/auth/dept/options` | GET | DeptController.getDeptOptions | — | TENANT |
| `/v1/auth/dept/scope-options` | GET | DeptController.getScopedDeptOptions | — | TENANT |
| `/v1/auth/dept/{id}` | GET | DeptController.getDeptById | — | TENANT |
| `/v1/auth/dept` | POST | DeptController.createDept | DEPT_CREATE | TENANT |
| `/v1/auth/dept` | PUT | DeptController.updateDept | DEPT_UPDATE | TENANT |
| `/v1/auth/dept/{id}` | DELETE | DeptController.deleteDept | DEPT_DELETE | TENANT |

### 2.6 Announcement

| Endpoint | Method | Controller.Method | @PreAuthorize | 分類 |
|---|---|---|---|---|
| `/v1/auth/announcements` | GET | AnnouncementController.list | isAuthenticated | TENANT |
| `/v1/auth/announcements/{id}` | GET | AnnouncementController.getById | isAuthenticated | TENANT |
| `/v1/auth/announcements/admin` | GET | AnnouncementController.listAdmin | ANNOUNCEMENT_MANAGE | TENANT |
| `/v1/auth/announcements/{id}/read` | POST | AnnouncementController.markAsRead | isAuthenticated | TENANT |
| `/v1/auth/announcements/read-all` | POST | AnnouncementController.markAllAsRead | isAuthenticated | TENANT |
| `/v1/auth/announcements/{id}/read-stats` | GET | AnnouncementController.getReadStats | ANNOUNCEMENT_MANAGE | TENANT |
| `/v1/auth/announcements/{id}/unread-users` | GET | AnnouncementController.getUnreadUsers | ANNOUNCEMENT_MANAGE | TENANT |
| (CRUD) | POST/PUT/DELETE | AnnouncementController.* | ANNOUNCEMENT_MANAGE | TENANT |

### 2.7 Password Policy

| Endpoint | Method | Controller.Method | @PreAuthorize | 分類 |
|---|---|---|---|---|
| `/v1/noauth/password-policy/describe` | GET | NoauthPasswordPolicyController.describe | — | PLATFORM |
| `/v1/auth/password-policy` | GET | TenantPasswordPolicyController.getEffective | — | TENANT |
| `/v1/auth/password-policy/tenant` | GET | TenantPasswordPolicyController.getTenantOverrides | PASSWORD_POLICY_MANAGE | TENANT |
| `/v1/auth/password-policy/tenant` | PUT | TenantPasswordPolicyController.updateTenantOverride | PASSWORD_POLICY_MANAGE | TENANT |
| `/v1/auth/password-policy/tenant/{key}` | DELETE | TenantPasswordPolicyController.deleteTenantOverride | PASSWORD_POLICY_MANAGE | TENANT |
| `/v1/platform/password-policy` | GET | PlatformPasswordPolicyController.getPlatformDefaults | PLATFORM_PASSWORD_POLICY_MANAGE | **PLATFORM** |
| `/v1/platform/password-policy` | PUT | PlatformPasswordPolicyController.update | PLATFORM_PASSWORD_POLICY_MANAGE | **PLATFORM** |

### 2.8 Tenant Auth Config (OIDC / SAML)

| Endpoint | Method | Controller.Method | @PreAuthorize | 分類 |
|---|---|---|---|---|
| `/v1/auth/tenant-auth-config` | GET | TenantAuthConfigController.get | PLATFORM_TENANT_MANAGE | **PLATFORM** |
| `/v1/auth/tenant-auth-config` | PUT | TenantAuthConfigController.createOrUpdate | PLATFORM_TENANT_MANAGE | **PLATFORM** |
| `/v1/auth/tenant-auth-config` | DELETE | TenantAuthConfigController.delete | PLATFORM_TENANT_MANAGE | **PLATFORM** |
| `/v1/auth/tenant-auth-config/test-connection` | POST | TenantAuthConfigController.testConnection | PLATFORM_TENANT_MANAGE | **PLATFORM** |

⚠️ **問題**：權限是 PLATFORM 但路徑掛在 `/v1/auth/*`，建議搬到 `/v1/platform/tenants/{id}/auth-config`。

### 2.9 Audit

| Endpoint | Method | Controller.Method | @PreAuthorize | 分類 |
|---|---|---|---|---|
| `/v1/auth/audit/categories` | GET | AuditController.getCategories | — | TENANT |
| `/v1/auth/audit/user/usage/history` | GET | AuditController.getUserUsageHistory | AUDIT_LIST | TENANT |
| `/v1/auth/audit/user/usage/history/export` | GET | AuditController.exportUsageHistory | AUDIT_LIST | TENANT |
| `/v1/auth/audit/user/login/my` | GET | AuditController.getMyLoginLog | — | SHARED |

### 2.10 Notification / Setting / Tenant Admin

| Endpoint | Method | Controller.Method | @PreAuthorize | 分類 |
|---|---|---|---|---|
| `/v1/auth/notifications/**` | * | NotificationController.* | — | SHARED |
| `/v1/auth/system-settings/**` | * | SystemSettingController.* | SYSTEM_SETTINGS_* | TENANT |
| `/v1/admin/tenants` | GET | TenantAdminController.listTenants | PLATFORM_TENANT_MANAGE | **PLATFORM** |
| `/v1/admin/tenants` | POST | TenantAdminController.createTenant | PLATFORM_TENANT_MANAGE | **PLATFORM** |
| `/v1/admin/tenants/{id}` | PUT | TenantAdminController.updateTenant | PLATFORM_TENANT_MANAGE | **PLATFORM** |
| `/v1/admin/tenants/{id}/enabled` | PATCH | TenantAdminController.toggleEnabled | PLATFORM_TENANT_MANAGE | **PLATFORM** |

---

## 3. 權限代碼盤點

### 3.1 PLATFORM Scope（只給 super_admin）

| 代碼 | 名稱 |
|------|------|
| `PLATFORM_TENANT_MANAGE` | 管理租戶（含認證方式設定） |
| `PLATFORM_PASSWORD_POLICY_MANAGE` | 管理平台密碼策略預設值 |
| `PLATFORM_USER_TENANT_MAPPING` | 管理跨租戶使用者—角色對應 |
| `PLATFORM_IMPERSONATE` | 以租戶身份操作（impersonate） |

### 3.2 TENANT Scope

| 群組 | 代碼 |
|------|------|
| User | `USER_LIST` / `USER_CREATE` / `USER_UPDATE` / `USER_DISABLE` / `USER_DELETE` |
| Dept | `DEPT_LIST` / `DEPT_CREATE` / `DEPT_UPDATE` / `DEPT_DELETE` |
| Menu | `MENU_LIST` / `MENU_CREATE` / `MENU_UPDATE` / `MENU_DELETE` |
| Role | `ROLE_LIST` / `ROLE_CREATE` / `ROLE_UPDATE` / `ROLE_ASSIGN_PERM` |
| Audit | `AUDIT_LIST` / `AUDIT_STATS` |
| Device | `DEVICE_VIEW` / `DEVICE_CREATE` / `DEVICE_UPDATE` |
| Monitor | `LOG_SUMMARY_VIEW` |
| Announcement | `ANNOUNCEMENT_MANAGE` |
| Password | `PASSWORD_POLICY_MANAGE` |
| System | `SYSTEM_SETTINGS_VIEW` / `SYSTEM_SETTINGS_MANAGE` |

---

## 4. 角色與 super_admin 處理機制

### 4.1 角色定義

| 角色 | Scope | 權限來源 |
|------|-------|---------|
| `ROLE_SUPER_ADMIN` | PLATFORM | **程式碼旁路自動拿全部權限** |
| `ROLE_ADMIN` | TENANT | DB role_permissions（所有 TENANT 權限 + LOG_SUMMARY_VIEW） |
| `ROLE_OPERATOR` | TENANT | DEVICE_VIEW/CREATE/UPDATE + USER_LIST |
| `ROLE_VIEWER` | TENANT | DEVICE_VIEW + USER_LIST + DEPT_LIST + AUDIT_LIST |
| `ROLE_DEPT_ADMIN` | TENANT | 未在 seed，僅 audit query 出現 → **建議清理** |

### 4.2 super_admin 旁路位置（**重構核心改動點**）

```java
// AuthServiceImpl.resolvePermissions() (約 line 896)
private List<String> resolvePermissions(String roleId, String tenantId) {
    if ("ROLE_SUPER_ADMIN".equals(roleId)) {
        return permissionRepository.findAllCodesOrderByCode();  // ← 取得全部，包含 TENANT
    }
    return permissionRepository.findCodesByRoleAndTenant(roleId, tenantId);
}
```

其他旁路點：
- `MenuService.getMyMenus()`：super_admin 直接拿 PLATFORM+PUBLIC 全部選單
- `@PreAuthorize("hasAuthority('X')")`：super_admin 因有全部 authority，皆通過

### 4.3 相關 Migration 時間軸

| Migration | 內容 |
|-----------|------|
| V1 / V1_1 | auth 表與初始角色 / 測試使用者 |
| V3 / V3_1 | menus 表 + 初始權限 + 角色綁定 |
| V33 | 補 ROLE_CREATE / ROLE_ASSIGN_PERM / USER_DELETE 等 |
| V58 | 新增 PLATFORM_* 權限 + PASSWORD_POLICY_MANAGE |
| V59 | menus 加 `scope` 欄位（PLATFORM / TENANT / PUBLIC） |

---

## 5. JWT 與認證結構

### 5.1 Claims

| Claim | 型別 | 範例 | 來源 |
|-------|------|------|------|
| `uid` / `subject` | String | "user-super-001" | login |
| `tenantId` | String | "TENANT_A" / null | selectTenant / switchTenant |
| `deptId` | String | "DEPT_A1" | user_tenant_mapping |
| `dataScope` | String | ALL / DEPT / SELF | login |
| `roles` | List | ["ROLE_ADMIN"] | login |
| `permissions` | List | ["USER_LIST", ...] | resolvePermissions |
| `temporary` | Boolean | true | tenant 選擇 token |
| `isSuperAdmin` | Boolean | true | temporary token only |
| `purpose` | String | "password_change" | 強制改密碼 token |

⚠️ **缺項**：沒有顯式的 `scope`（PLATFORM / TENANT）claim，目前靠 `tenantId == null` 隱含判斷。

### 5.2 Token 類型

| 類型 | 用途 | tenantId | TTL |
|------|------|---------|-----|
| Access Token | 一般 API 認證 | 有 | 短 |
| Refresh Token | 換新 access | 有 | 長（HttpOnly cookie） |
| Temporary Token | 多租戶選擇 | NULL | 短 |
| Password-Change Token | 強制改密碼 | NULL | 短 |

### 5.3 Tenant Context 取得方式

1. 從 JWT 取 `tenantId` → `TenantContext.getCurrentTenantId()`
2. super_admin「系統情境」下 tenantId = NULL
3. 多租戶 user 透過 select-tenant 取得新 token

---

## 6. 前端路由與選單盤點

### 6.1 NoAuth 路由

| Path | View | 用途 |
|------|------|------|
| `/login` | LoginView.vue | 登入 |
| `/select-tenant` | SelectTenantView.vue | 選擇租戶 |
| `/forgot-password` | ForgotPasswordView.vue | 忘記密碼 |
| `/reset-password` | ResetPasswordView.vue | 重設密碼 |
| `/force-change-password` | ForceChangePasswordView.vue | 強制改密碼 |

### 6.2 Public 路由（登入即可，無權限要求）

| Path | View | 用途 |
|------|------|------|
| `/` | HomeView.vue | Dashboard |
| `/profile` | ProfileView.vue | 個人資料 |
| `/change-password` | ChangePasswordView.vue | 改密碼 |
| `/my/activity` | MyActivityView.vue | 我的活動紀錄 |
| `/announcements` | AnnouncementListView.vue | 公告 |

### 6.3 Admin 靜態路由（DB 選單也會注入同名路由）

| Path | View | superAdminOnly | 分類 |
|------|------|:--:|------|
| `/admin/users` | UserListView.vue | | TENANT |
| `/admin/users/create` | CreateUserView.vue | | TENANT |
| `/admin/users/:userId/edit` | EditUserView.vue | | TENANT |
| `/admin/system/menus` | menu/MenuManageView.vue | | TENANT |
| `/admin/system/tenants` | tenant/TenantManageView.vue | ✓ | **PLATFORM** |
| `/admin/system/roles` | role/RolePermissionView.vue | | TENANT |
| `/admin/audit/history` | audit/AuditHistoryView.vue | | TENANT |
| `/admin/system/settings` | setting/SystemSettingsView.vue | | TENANT |
| `/admin/security/password-policy` | setting/TenantPasswordPolicyView.vue | | TENANT |
| `/platform/auth-config` | setting/TenantAuthConfigView.vue | ✓ | **PLATFORM** |
| `/admin/system/announcements` | announcement/AnnouncementManagementView.vue | | TENANT |

### 6.4 DB 選單

| Menu | Type | Scope | 用途 |
|------|------|-------|------|
| 使用者管理 | DIRECTORY | TENANT | |
| 系統管理 | DIRECTORY | TENANT | 部門 / 選單 / 角色 |
| 稽核中心 | DIRECTORY | TENANT | |
| 監控中心 | DIRECTORY | TENANT | |
| 公告 | PAGE | PUBLIC | |
| **平台管理** | DIRECTORY | **PLATFORM** | super_admin 專用 |

---

## 7. 最終分類總表

### A. 純 Platform（應只給 super_admin）

| 元件 | 路徑 |
|------|------|
| TenantAdminController（4） | `/v1/admin/tenants/**` |
| PlatformPasswordPolicyController（2） | `/v1/platform/password-policy` |
| UserAdminController tenant-roles（3） | `/v1/auth/users/{id}/tenant-roles/**` ⚠️ 路徑該搬 |
| TenantAuthConfigController（4） | `/v1/auth/tenant-auth-config` ⚠️ 路徑該搬 |
| TenantManageView.vue | `/admin/system/tenants` |
| TenantAuthConfigView.vue | `/platform/auth-config` |
| Menu scope=PLATFORM | DB |

### B. 純 Tenant（super_admin 不該碰）

| 元件 |
|------|
| UserAdminController（user CRUD） |
| DeptController |
| RoleController |
| MenuController（CRUD） |
| AnnouncementController |
| TenantPasswordPolicyController |
| SystemSettingController |
| AuditController |

### C. 共用 / 中性（兩邊都需要）

| 元件 | 備註 |
|------|------|
| AuthController（login / refresh / logout / sessions） | |
| UserSelfController（profile / 改密碼） | |
| NotificationController | 個人通知 |
| MenuController.getMyMenus | 已依 scope 過濾 |

---

## 8. 已發現的問題清單

| # | 問題 | 影響 |
|---|------|------|
| P1 | super_admin 在 `resolvePermissions()` 直接拿全部權限（含 TENANT） | **核心問題**，導致 super_admin 能進入所有 tenant 介面 |
| P2 | `TenantAuthConfigController` 用 PLATFORM 權限但掛在 `/v1/auth/*` | 路徑語意不一致 |
| P3 | `UserAdminController` 混 TENANT + PLATFORM 三支 endpoint | 路由規則無法乾淨切分 |
| P4 | `MenuController` 寫操作沒掛 `@PreAuthorize` | 安全漏洞，僅靠登入即可呼叫 |
| P5 | JWT 沒有顯式 `scope` claim | 後端只能靠 `tenantId == null` 推斷 |
| P6 | `ROLE_DEPT_ADMIN` 未在 seed 卻被 audit query 引用 | 資料不一致 |
| P7 | 前端 `/admin/*` 與 `/platform/*` 路徑混用 | layout 不易切分 |
| P8 | 沒有 Impersonation API | 缺少 super_admin 合法協助租戶的通道 |

---

## 9. 待決議事項（將由 ADR 處理）

| # | 議題 | 選項 |
|---|------|------|
| Q1 | super_admin 進入 tenant API 是「後端強制禁止」或「僅 UI 隱藏」 | 強制 / 寬鬆 |
| Q2 | Impersonation 是否納入 Phase 1 | 是 / 是但 Phase 2 / 不做 |
| Q3 | API 路徑命名標準 | `/v1/platform/**` + `/v1/tenants/{id}/**` / 保留 `/v1/admin/*` 混合 |
| Q4 | 前端架構 | 同 SPA + 雙 layout / 拆兩個 app |
| Q5 | 是否清除 `ROLE_DEPT_ADMIN` | 清 / 補完 seed |
| Q6 | `TenantAuthConfigController` 是否搬到 `/v1/platform/*` | 搬 / 不搬 |
| Q7 | JWT 是否加 `scope` claim | 加 / 沿用隱含判斷 |
| Q8 | 是否強制 MenuController 寫操作加權限 | 加 MENU_CREATE/UPDATE/DELETE / 維持現狀 |

---

## 10. 下一步

1. 針對 §9 的 Q1–Q8 逐題決議，產出 `02-adr.md`
2. 依決議內容撰寫 `03-phased-plan.md`（Phase 1–4 分階段執行計畫）
3. 進入實作

---

## 附錄：統計

| 類別 | 數量 |
|------|------|
| 純 PLATFORM endpoints | ~15 |
| 純 TENANT endpoints | ~70 |
| 共用 / 中性 endpoints | ~14 |
| 待路徑搬遷 | 7 |
| 安全性需補強處 | 4 endpoints（MenuController CRUD） |
