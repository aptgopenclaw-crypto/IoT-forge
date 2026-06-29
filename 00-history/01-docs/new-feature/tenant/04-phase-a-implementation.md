# Phase A 實作紀錄：權限碼矩陣 + 端點守衛改寫 + 菜單 Scope

> 完成日期：2026-05-29  
> 狀態：✅ 全部完成（Backend 1057 tests pass, Frontend 244 tests pass）

---

## 1. 新增權限碼（V58）

| permission_id | code | 名稱 | group_name | 授予角色 |
|---|---|---|---|---|
| PERM_PASSWORD_POLICY_MANAGE | `PASSWORD_POLICY_MANAGE` | 管理租戶密碼策略 | 密碼策略 | ADMIN |
| PERM_PLATFORM_TENANT_MANAGE | `PLATFORM_TENANT_MANAGE` | 管理租戶（含認證方式設定） | 平台管理 | SUPER_ADMIN* |
| PERM_PLATFORM_PASSWORD_POLICY_MANAGE | `PLATFORM_PASSWORD_POLICY_MANAGE` | 管理平台密碼策略預設值 | 平台管理 | SUPER_ADMIN* |
| PERM_PLATFORM_USER_TENANT_MAPPING | `PLATFORM_USER_TENANT_MAPPING` | 管理跨租戶使用者—角色對應 | 平台管理 | SUPER_ADMIN* |
| PERM_PLATFORM_IMPERSONATE | `PLATFORM_IMPERSONATE` | 以租戶身份操作 | 平台管理 | SUPER_ADMIN* |

> \* SUPER_ADMIN 無需 `role_permissions` 行。`AuthServiceImpl.resolvePermissions()` 對 SUPER_ADMIN 回傳 `findAllCodesOrderByCode()`，自動取得所有權限碼。

---

## 2. 端點守衛改寫對照

| 端點 | 舊守衛 | 新守衛 | 檔案 |
|------|--------|--------|------|
| `/v1/admin/tenants/**` | `hasRole("SUPER_ADMIN")` | `hasAuthority("PLATFORM_TENANT_MANAGE")` | SecurityConfig.java |
| `TenantAdminController` (class) | `hasRole('SUPER_ADMIN')` | `hasAuthority('PLATFORM_TENANT_MANAGE')` | TenantAdminController.java |
| `PlatformPasswordPolicyController` (class) | `hasRole('SUPER_ADMIN')` | `hasAuthority('PLATFORM_PASSWORD_POLICY_MANAGE')` | PlatformPasswordPolicyController.java |
| `TenantAuthConfigController` (class) | `hasRole('SUPER_ADMIN')` | `hasAuthority('PLATFORM_TENANT_MANAGE')` | TenantAuthConfigController.java |
| `TenantPasswordPolicyController` GET/PUT/DELETE tenant | `hasRole('ADMIN') or hasRole('SUPER_ADMIN')` | `hasAuthority('PASSWORD_POLICY_MANAGE')` | TenantPasswordPolicyController.java |
| `UserAdminController` getUserTenantMappings | `hasRole('SUPER_ADMIN')` | `hasAuthority('PLATFORM_USER_TENANT_MAPPING')` | UserAdminController.java |
| `UserAdminController` addTenantRole | `hasRole('SUPER_ADMIN')` | `hasAuthority('PLATFORM_USER_TENANT_MAPPING')` | UserAdminController.java |
| `UserAdminController` removeTenantRole | `hasRole('SUPER_ADMIN')` | `hasAuthority('PLATFORM_USER_TENANT_MAPPING')` | UserAdminController.java |

---

## 3. 移除項目

### restoreUser 端點整條移除
- `UserAdminController.restoreUser()` — `@PatchMapping("/{userId}/restore")`
- `UserAdminService.restoreUser()` 方法
- 對應 3 個單元測試（`UserAdminServiceTest`）
- 前端無對應 UI（確認未使用）

---

## 4. CreateTenant 擴充

- `CreateTenantRequest` 新增 `@NotNull AuthType initialAuthMethod = AuthType.LOCAL`
- `TenantAdminService.createTenant()` 建立租戶後，在 `TenantContext.runInSystemContext()` 內寫入 `TenantAuthConfigEntity`（tenantId, authType, enabled=true, fallbackLocal=true）
- 新建 admin user 時設定 `passwordChangedAt = LocalDateTime.now()`（修復 NOT NULL 違規）

---

## 5. 菜單 Scope 分離（V59）

### Schema 變更
```sql
ALTER TABLE menus ADD COLUMN scope VARCHAR(20) NOT NULL DEFAULT 'TENANT';
-- CHECK constraint: PLATFORM / TENANT / PUBLIC
-- Index: idx_menus_scope
```

### 菜單分類結果

| menu_id | 名稱 | scope | parent_id | 備註 |
|---------|------|-------|-----------|------|
| 100 | 平台管理 | PLATFORM | NULL | 新增 DIRECTORY |
| 101 | 租戶管理 | PLATFORM | 100 | 新增 PAGE，perm=PLATFORM_TENANT_MANAGE |
| 37 | 認證方式設定 | PLATFORM | 100 | 遷移自 parent=10，route 改為 /platform/auth-config |
| 11 | Menu Management | PLATFORM | 100 | 遷移自 parent=10 |
| 34 | 公告欄 | PUBLIC | NULL | 所有已登入使用者可見 |
| 其餘 | — | TENANT | 原值 | 預設不動 |

### 後端 Entity/Repository/DTO
- `MenuEntity.scope` — `@Column(name="scope") @Builder.Default private String scope = "TENANT"`
- `MenuRepository.findByScopeAndVisibleTrue(String scope)` — Phase B 用
- `MenuDto.scope`、`UserMenuDto.scope`
- `CreateMenuRequest.scope`、`UpdateMenuRequest.scope`（@Pattern PLATFORM|TENANT|PUBLIC）
- `MenuService`：create 時預設 TENANT、update 時可改、toDto 帶上 scope

### 前端
- `types/rbac.ts`：`MenuScope` type + 各 DTO 加 scope 欄位
- `MenuFormDialog.vue`：新增「適用範圍」下拉選單
- `AppSidebar.vue`：移除 hardcoded `v-if="isSuperAdmin"` 租戶管理入口（改由 API 驅動）
- `router/index.ts`：auth-config 路由 `/admin/security/auth-config` → `/platform/auth-config`

---

## 6. Migration 檔案

| 檔案 | 說明 |
|------|------|
| `V58__rbac__platform_permissions.sql` | 5 個權限碼 + ADMIN ↔ PASSWORD_POLICY_MANAGE 綁定 |
| `V59__rbac__menu_scope.sql` | scope 欄位 + 菜單重組 + 新增平台管理目錄 |

---

## 7. 測試驗證

| 範疇 | 結果 |
|------|------|
| Backend 全套 | 1057 tests, 0 failures |
| Frontend 全套 | 244 tests (34 files), 0 failures |
| 重點修改類 | UserAdminServiceTest (23), TenantAdminServiceTest (11), TenantAdminServiceSeedSettingsTest (5), TenantAdminControllerRateLimitTest (7), MenuServiceTest (10) |

---

## 8. 設計決策記錄

| # | 決策 | 理由 |
|---|------|------|
| 1 | SUPER_ADMIN 不寫 `role_permissions` 行 | `resolvePermissions()` auto-grant 所有 codes，新增 permission row 即自動授予 |
| 2 | 用 `hasAuthority` 取代 `hasRole` | 解耦端點守衛與角色名，未來新增角色只需綁 permission |
| 3 | `PLATFORM_IMPERSONATE` 預埋不使用 | Phase B impersonation 機制啟動時再消費此權限碼 |
| 4 | restoreUser 直接移除 | 無 UI、無前端呼叫、軟刪除 timestamp 保留供 audit |
| 5 | TenantAuthConfig 在 createTenant 寫入 | 避免「租戶已存在但無認證設定」的不一致狀態 |
| 6 | menu scope 用 DB CHECK constraint | 編譯期 + DB 層雙重校驗，防止非法值 |
