# 分階段執行計畫 — Platform / Tenant 職責分離

> 文件版本：v1.0
> 產出日期：2026-05-31
> 前置文件：[01-inventory.md](01-inventory.md) / [02-adr.md](02-adr.md)
> 狀態：📋 待實作

---

## 總覽

依 ADR 決議，將重構拆為 5 個 Phase。**核心隔離（移除 super_admin 旁路）放在 Phase 3**，前面兩個 Phase 為其鋪路（避免一上線就把 super_admin 鎖死無法操作）。

```
Phase 1：基礎建設         ──▶ JWT scope、Impersonation、補權限缺口
   │
Phase 2：路徑搬遷         ──▶ Platform endpoint 統一到 /v1/platform/**
   │
Phase 3：強制隔離 ⚠️核心  ──▶ 移除 super_admin 旁路、Filter 強制檢查
   │
Phase 4：前端分流         ──▶ PlatformLayout / TenantLayout、登入分流
   │
Phase 5：清理             ──▶ 移除舊路徑、舊邏輯、補 E2E
```

每個 Phase 為一個 PR/feature branch；Phase 1–3 上線前需通過完整 E2E 測試。

---

## Phase 1：基礎建設

**目標**：把 ADR-001（強制隔離）所需的「事前準備」全部就位，但**還不要打開開關**。

### 1.1 工作項目

| # | 任務 | 對應 ADR | 檔案 / 模組 | 狀態 |
|---|------|---------|-----------|:----:|
| 1.1.1 | JWT 新增 `scope` claim（PLATFORM / TENANT / IMPERSONATION） | ADR-007 | `JwtUtil.java`、`TokenScope.java`、`JwtClaimKeys.java`、`AuthServiceImpl.java` | ✅ |
| 1.1.2 | SecurityFilter / Interceptor 讀取 `scope` 但**僅記 log 不拒絕**（warning mode） | ADR-007 | `ScopeEnforcementFilter.java`、`SecurityConfig.java`、`SecurityEvent.SCOPE_MISMATCH` | ✅ |
| 1.1.3 | Flyway V62：新增 `ROLE_DEPT_ADMIN` 至 `roles` + `role_permissions`（baseline、ON CONFLICT DO NOTHING；V13 streetlight 種子已先插入該 role，本 migration 為平台層級 baseline） | ADR-005 | `db/migration/V62__rbac__seed_dept_admin_role.sql` | ✅ |
| 1.1.4 | Flyway V60 (既有) + V63：`user_event_log` / `user_info_log` 增加 `impersonated_by`（V60）與 `impersonation_session_id`（V63） | ADR-002 | `db/migration/V60__audit__add_impersonated_by.sql` (既有)、`db/migration/V63__audit__add_impersonation_session_id.sql` | ✅ |
| 1.1.5 | Flyway V64：新增 `impersonation_session` 表（id、operator_user_id、target_tenant_id、reason、status、started_at、expires_at、revoked_at、revoked_by_user_id） | ADR-002 | `db/migration/V64__platform__impersonation_session.sql` | ✅ |
| 1.1.6 | Impersonation API：`POST/DELETE/GET /v1/platform/impersonations` | ADR-002 | 新增 `platform/impersonation/{controller,service,entity,repository,dto}/*`；`@PreAuthorize("hasAuthority('PLATFORM_IMPERSONATE')")`；簽發 IMPERSONATION scope token（`JwtUtil.generateImpersonationAccessToken`）；新增 `ErrorCode.IMPERSONATION_*`、`AuditEventType.IMPERSONATE_START/END`；單元測試 `ImpersonationServiceTest`（8 案例：成功、duration 超界、tenant 不存在/已停用、revoke 成功/非 owner/找不到/冪等、list lazy EXPIRED）。**通知整合留 1.1.7** | ✅ |
| 1.1.7 | Impersonation 通知：寄信給目標租戶所有 ROLE_ADMIN | ADR-002 | `ImpersonationService.notifyTargetTenantAdmins` 於 `create()` 後 best-effort 呼叫 `NotificationService.send`；`NotificationRefType` 新增 `IMPERSONATION`；cross-tenant 查詢透過 `TenantContext.runInSystemContext` 取得 target tenant 的 ROLE_ADMIN mapping；通知失敗不阻擋 token 簽發。新增 3 個單元測試（成功 fan-out、無 admin 時跳過、查詢失敗時不影響主流程） | ✅ |
| 1.1.8 | 補 `@PreAuthorize` 至 `MenuController`（CREATE/UPDATE/DELETE/toggleVisible） | ADR-008 | `MenuController.java` | ✅ |
| 1.1.9 | 補 `@PreAuthorize` 至 `DeptController`（getDeptTree → DEPT_LIST；options/scope-options/getDeptById → isAuthenticated，保留下拉用途） | ADR-008 | `DeptController.java` | ✅ |
| 1.1.10 | 補 `@PreAuthorize` 至 `RoleController`（list/listAssignable/getRolePermissions → ROLE_LIST）、`MenuController.getMenuTree → MENU_LIST` | ADR-008 | `RoleController.java`、`MenuController.java` | ✅ |
| 1.1.11 | i18n：`ROLE_DEPT_ADMIN` 顯示名稱（zh-TW / en） | ADR-005 | 前端 i18n 檔 | ✅ 於 `frontend/src/locales/{zh-TW,en,zh-CN}.ts` 新增 `role.displayNames` 對應表（涵蓋 7 個內建 role：SUPER_ADMIN / ADMIN / DEPT_ADMIN / OPERATOR / VIEWER / FIELD_USER / MONITOR），`RolePermissionView.vue` 改用 `localizedRoleName()` helper（`te()` 偵測後回退至後端 `role.name`）。新增 `roleDisplayNames.test.ts`（25 cases：3 locales × 8 assertion + 1 distinctness）；frontend 單元測試 284/284 通過 |
| 1.1.12 | 單元測試：`scope` claim 寫入、Impersonation service 流程、新增的 @PreAuthorize 覆蓋 | — | `src/test/...` | 🟡 部分（scope claim ✅） |

### 1.2 驗收標準

- ✅ super_admin 登入後 JWT 含 `scope=PLATFORM`、tenant 使用者含 `scope=TENANT`
- ✅ Impersonation API：可建立、可主動結束、自動過期、被 impersonate 的租戶 ROLE_ADMIN 收到通知
- ✅ `audit_log` 在 impersonation 模式下記錄 `impersonated_by_user_id`
- ✅ 「角色管理」UI 看到 ROLE_DEPT_ADMIN 並可指派
- ✅ 無權限的呼叫者打 MenuController/DeptController/RoleController 補強過的端點回 403
- ✅ 所有現有 E2E 測試通過（未引入 regression）
- ✅ ScopeEnforcementFilter 在 log 看到 scope 判斷，但**不拒絕請求**

### 1.3 風險

- ROLE_DEPT_ADMIN seed 與既有資料衝突 → 用 `ON CONFLICT DO NOTHING`
- 補 `@PreAuthorize` 後某些角色突然 403 → 上線前跑完整 E2E、檢視 OPERATOR/VIEWER 角色權限是否有缺

---

## Phase 2：路徑搬遷

**目標**：把所有 platform-level endpoint 統一到 `/v1/platform/**`；舊路徑保留並回傳 `Deprecation` header。

### 2.1 工作項目

| # | 任務 | 對應 ADR | 狀態 |
|---|------|---------|------|
| 2.1.1 | `TenantAdminController`：`/v1/admin/tenants/**` → `/v1/platform/tenants/**`（4 個 endpoint）；同步更新 `SecurityConfig` requestMatcher 與 `WebMvcConfigTest` 範例路徑。**舊路徑保留 + Deprecation header 於 2.1.4** | ADR-003 | ✅ |
| 2.1.2 | `TenantAuthConfigController` 拆出 → `PlatformTenantAuthConfigController`，掛在 `/v1/platform/tenants/{tenantId}/auth-config`（4 個 endpoint） | ADR-006 | ✅ 新增 `PlatformTenantAuthConfigController`（GET/PUT/DELETE 根路徑 + POST `/test-connection`），改用 `@PathVariable tenantId` 取代 `TenantContext`；服務介面無需改動；舊 `TenantAuthConfigController` 保留至 2.1.4 加 Deprecation header；新增 6 個單元測試（class-level routing/auth 註解 + 4 endpoint 委派驗證）；mvn test 1111/1111 BUILD SUCCESS |
| 2.1.3 | `UserAdminController` 三支 tenant-roles endpoint 拆出 → `PlatformUserTenantMappingController`，掛在 `/v1/platform/users/{userId}/tenant-roles/**` | ADR-003 | ✅ 新增 `PlatformUserTenantMappingController`（GET list / POST add / DELETE remove），class-level `@PreAuthorize('PLATFORM_USER_TENANT_MAPPING')`；委派至既有 `UserAdminService` 三支方法無需改動；舊 `UserAdminController` tenant-roles 端點保留至 2.1.4 加 Deprecation header；新增 5 個單元測試（routing/auth 註解 + 3 endpoint 委派）；mvn test 1116/1116 BUILD SUCCESS |
| 2.1.4 | 舊路徑保留：在原 controller method 加 `@Deprecated` + 回應 `Deprecation: true` header；轉呼新 service 方法 | ADR-003 | ✅ 新增 `@DeprecatedApi(successor, sunset)` 註解 + `DeprecatedApiInterceptor`（依 RFC 8594 寫入 `Deprecation: true` / `Link: <successor>; rel="successor-version"` / `Sunset` header，method-level 蓋過 class-level）；註冊於 `WebMvcConfig`，`CorsProperties.exposedHeaders` 補上 `Deprecation, Link, Sunset` 讓前端 JS 可讀；標記 `TenantAuthConfigController`（class-level，successor `/v1/platform/tenants/{tenantId}/auth-config`）與 `UserAdminController` 三支 tenant-roles method（successor `/v1/platform/users/{userId}/tenant-roles[/{mappingId}]`）；說明：2.1.1 已將 `TenantAdminController` 路徑原地遷移至 `/v1/platform/tenants/**`，無 legacy 共存路徑，故不需 Deprecation header；新增 5 個 interceptor 單元測試 + `WebMvcConfigTest` 修正建構式；mvn test 1121/1121 BUILD SUCCESS |
| 2.1.5 | 前端 API client：`frontend/src/api/*.ts` 改用新路徑 | — | ✅ `frontend/src/api/tenant/admin.ts` 全部 4 個函式改為 `/platform/tenants/**`；`frontend/src/api/user/index.ts` 三支 tenant-roles 函式改為 `/platform/users/{userId}/tenant-roles[...]`；`frontend/src/api/authConfig/index.ts` 全部 4 個函式增加 `tenantId` 參數並改為 `/platform/tenants/{tenantId}/auth-config[...]`；`TenantAuthConfigView.vue` 改傳 `authStore.userInfo.tenantId`（暫用 store，2.1.6 將改為 route param）；新增 `frontend/src/__tests__/api/platformPaths.test.ts`（12 個 case 驗證所有新路徑與 `encodeURIComponent`）；vitest 256/256 通過 |
| 2.1.6 | 前端路由：`/platform/auth-config` 改為 `/platform/tenants/:tenantId/auth-config` | — | ✅ `router/index.ts` 主路由改為 `/platform/tenants/:tenantId/auth-config`（name `TenantAuthConfig`，`props: true`），保留 legacy `/platform/auth-config` 別名 (`TenantAuthConfigLegacy`) 以 redirect 至主路由（讀 `localStorage.lastTenantId` 補 param，無則導至 Login），向後相容舊書籤與 V52/V59 seed 之 `route_path`；`TenantAuthConfigView.vue` 改用 `useRoute().params.tenantId` 並保留 `authStore.userInfo.tenantId` 作 fallback；新增 `__tests__/router/tenantAuthConfigRoute.test.ts`（3 case：source 含正確 path/name、legacy alias 存在、vue-router resolve 兩個 path）；vitest 259/259 unit 通過 |
| 2.1.7 | 更新 OpenAPI / Swagger 文件 | — | ✅ 新增 `@Tag` (Platform / Tenants、Platform / Tenants / Auth Config、Platform / Users / Tenant Roles、Tenant / Auth Config (Deprecated)) 與 `@Operation` 至 5 個 controller（`TenantAdminController`、`PlatformTenantAuthConfigController`、`PlatformUserTenantMappingController` + legacy `TenantAuthConfigController`、`UserAdminController` 之 tenant-roles 3 個方法）。legacy 端點皆標 `deprecated = true` 並在 description 指向 successor 路徑；`mvn test` 1121/1121 BUILD SUCCESS |

### 2.2 驗收標準

- ✅ 新舊路徑同時可用，回應相同
- ✅ 舊路徑回應 header 含 `Deprecation: true` 與 `Sunset: <6-month-future-date>`
- ✅ 前端完全改用新路徑（grep 確認 `/v1/admin/tenants` 與舊 `tenant-auth-config` 路徑已清除）
- ✅ E2E 測試使用新路徑通過

### 2.3 風險

- 巢狀路徑 `/v1/platform/tenants/{tenantId}/auth-config` 與 `PlatformTenantAuthConfigController` 內部需正確 resolve `tenantId`（從 path variable 而非 JWT）

---

## Phase 3：強制隔離 ⚠️核心

**目標**：移除 super_admin 旁路；ScopeEnforcementFilter 切換到 enforce mode。

> ⚠️ **此 Phase 上線前 Phase 1（Impersonation）與 Phase 2（路徑搬遷）必須已完成且穩定**，否則 super_admin 將無任何方式碰觸租戶資料。

### 3.1 工作項目

| # | 任務 | 對應 ADR | 檔案 | 狀態 |
|---|------|---------|------|------|
| 3.1.1 | 移除 `AuthServiceImpl.resolvePermissions()` super_admin 旁路；改為 `findCodesByRoleAndTenant("ROLE_SUPER_ADMIN", null)` 從 DB 抓 | ADR-001 | `AuthServiceImpl.java` ~line 896 | ✅ 移除 `if ("ROLE_SUPER_ADMIN".equals(roleId)) return findAllCodesOrderByCode()` 旁路，super_admin 改與其他 role 一樣走 `findCodesByRoleAndTenant`（JPQL 內 `rp.tenantId IS NULL OR = :tenantId` 保證只取 PLATFORM_* 全租戶共用權限）。新增 `selectTenant_superAdmin_resolvesPermissionsFromRolePermissions_noBypass` 測試驗證 `findAllCodesOrderByCode` 不再被呼叫；mvn test 1122/1122 BUILD SUCCESS。⚠️ 在 3.1.2 (V63 種入 4 個 PLATFORM_* 至 `role_permissions`) 完成前，super_admin 在 DB 將取得空權限集 — 兩任務需連續執行 |
| 3.1.2 | Flyway V63：確保 `role_permissions` 中 ROLE_SUPER_ADMIN 只綁定 4 個 `PLATFORM_*` 權限 | ADR-001 | `db/migration/V63__super_admin_platform_only.sql` | ✅ 實際使用 **V65**（V63 已被 audit `impersonation_session_id` 佔用）。`V65__rbac__super_admin_platform_only.sql` 先 `DELETE FROM role_permissions WHERE role_id = 'ROLE_SUPER_ADMIN'` 清除歷史殘留，再以 `tenant_id = NULL` 綁定 4 個 PLATFORM_* 權限：`PLATFORM_TENANT_MANAGE` / `PLATFORM_PASSWORD_POLICY_MANAGE` / `PLATFORM_USER_TENANT_MAPPING` / `PLATFORM_IMPERSONATE`（V58 已建立的 permission 條目）。新增 `SuperAdminPlatformOnlyMigrationTest`（5 cases：驗證 DELETE 子句、4 個 PLATFORM_* 條目、tenant_id=NULL、不含任何租戶權限如 USER_LIST/DEPT_LIST、ON CONFLICT DO NOTHING）；同步修正 3.1.1 測試中錯誤的 `PLATFORM_AUDIT_VIEW` 為實際的 `PLATFORM_PASSWORD_POLICY_MANAGE`；mvn test 1127/1127 BUILD SUCCESS |
| 3.1.3 | 移除 `MenuService.getMyMenus()` super_admin 旁路；改為走正常 role-based 過濾 | ADR-001 | `MenuService.java` | ✅ 移除 `if (isSuperAdmin) { ... }` 取所有 PLATFORM scope menu 之旁路；super_admin 改與一般 role 共走 `findCodesByRoleIdsAndTenant` + `permission_code` 過濾；保留 SYSTEM→PLATFORM+PUBLIC、tenant→TENANT+PUBLIC scope 區分。更新 `MenuServiceGetMyMenusTest` 兩個 super_admin 測試以 mock V65 種入的 PLATFORM_* perms，並新增 `getMyMenus_superAdmin_withNoPermissions_returnsEmpty_noBypass` 鎖定無 bypass 行為。mvn test 1128/1128 BUILD SUCCESS |
| 3.1.4 | `ScopeEnforcementFilter` 切換為 enforce mode：違規回 403 | ADR-007 | `ScopeEnforcementFilter.java` | ✅ 改 ctor 注入 `@Value("${app.security.scope-enforcement.mode:enforce}")`，預設 enforce；mismatch 時寫 HTTP 403 + `{"errorCode":"10031","message":"Token scope 與請求路徑不符"}` JSON 並中斷 chain。保留 `warning` 模式作為 §3.3 rollback escape hatch（僅 log、不阻擋）。新增 `ErrorCode.SCOPE_FORBIDDEN("10031", 403, ...)`；`application.yml` 加 `app.security.scope-enforcement.mode: ${SCOPE_ENFORCEMENT_MODE:enforce}`。`ScopeEnforcementFilterTest` 重構為 enforce + warning 雙模式覆蓋（新增 5 cases：兩個 enforce 拒絕、兩個 warning 通過、legacy 無 scope claim 命中 PLATFORM 路徑拒絕），共 15/15 通過；mvn test 1130/1130 BUILD SUCCESS（無既有 controller 測試 regression — JWT helper 早於 1.1.1 即正確帶 scope claim） |
| 3.1.5 | `Impersonation` 取得的 token：`scope=IMPERSONATION`、`tenantId=<目標>`、permissions 套用目標租戶 ROLE_ADMIN | ADR-002 | `ImpersonationService.issueToken()` | ✅ `ImpersonationService.create()` 將 permissions 來源從 `permissionRepository.findAllCodesOrderByCode()`（super_admin 全權）改為 `findCodesByRoleAndTenant("ROLE_ADMIN", targetTenantId)`，僅取得目標租戶 ROLE_ADMIN 的權限集。JWT 其餘 claim 保持：`scope=IMPERSONATION`（由 `JwtUtil.generateImpersonationAccessToken` 強制）、`tenantId=<目標>`、`roles=["SUPER_ADMIN"]`（讓 `JwtAuthenticationFilter` 仍可設定 `TenantContext.setImpersonator()`）、`impersonation.{originalUserId, sessionId, expiresAt}`、`dataScope=ALL`。`ImpersonationServiceTest` 既有 4 個 stub 從 `findAllCodesOrderByCode` 改為 `findCodesByRoleAndTenant`；新增 2 cases：`create_jwtClaimsCarryTargetTenantRoleAdminPermissions`（用 `ArgumentCaptor` 驗證 JWT 4 個關鍵 claim）、`create_whenTargetTenantRoleAdminHasNoPermissions_tokenGetsEmptyPermissions`（鎖定無 fallback 全權行為）。mvn test 1132/1132 BUILD SUCCESS |
| 3.1.6 | 補測試：super_admin 呼叫 `/v1/auth/users` → 403；impersonation token 呼叫 → 200 並記錄 `impersonated_by_user_id` | — | E2E | ✅ 於 `UserAdminControllerTest` 新增 2 個 MockMvc 整合測試覆蓋 Phase 3 端到端契約：(1) `listUsers_superAdminWithPlatformScope_shouldReturn403_byScopeFilter` — super_admin 持 PLATFORM scope token 打 `/v1/auth/users`，`ScopeEnforcementFilter`（3.1.4 enforce）回 403/`10031`、`UserAdminService` 從未被呼叫；(2) `listUsers_impersonationToken_shouldReturn200_andSetImpersonatorMarker` — IMPERSONATION scope + roles=[SUPER_ADMIN] + tenantId=TENANT_A 通過 filter；用 `doAnswer` 在 service 執行當下捕獲 `TenantContext.getImpersonator()`，assert 等於發起代操的 super_admin uid（即 `UserInfoLogEntity.impersonated_by` / `UserEventLogEntity.impersonated_by` 的填值來源），並 assert `TenantContext.getCurrentTenantId()=TENANT_A`。同步擴充 `mockJwtValid` 助手新增第三層 overload 帶 `scope` claim（既有呼叫沿用 default `TENANT`）。mvn test 1134/1134 BUILD SUCCESS |
| 3.1.7 | 監控：上線後 24h 觀察 `ScopeEnforcementFilter` 403 log，確認沒有合法請求被誤擋 | — | 監控/log | ⏳ |

### 3.2 驗收標準

- ✅ super_admin login → JWT 只含 4 個 `PLATFORM_*` 權限
- ✅ super_admin 直接呼叫 `/v1/auth/users`、`/v1/auth/dept`、`/v1/auth/menus` → 403
- ✅ super_admin 看不到 tenant 選單（getMyMenus 只回 PLATFORM + PUBLIC）
- ✅ super_admin 啟動 impersonation 後可正常操作目標租戶 API，audit_log 完整記錄
- ✅ 既有 tenant 使用者操作完全不受影響

### 3.3 回滾計畫

- 若上線後發現大量 regression：將 ScopeEnforcementFilter 切回 warning mode（單一設定開關）
- 若 ROLE_SUPER_ADMIN 在 DB 的權限綁錯：執行 V63 的反向 SQL 還原

---

## Phase 4：前端分流

**目標**：依 ADR-004 將前端拆為 PlatformLayout / TenantLayout / NoAuthLayout。

### 4.1 工作項目

| # | 任務 | 對應 ADR | 檔案 | 狀態 |
|---|------|---------|------|------|
| 4.1.1 | 新增 `layouts/PlatformLayout.vue`（深色系、Logo 標 Platform、sidebar 只顯示平台選單） | ADR-004 | `frontend/src/layouts/PlatformLayout.vue`、`frontend/src/__tests__/components/PlatformLayout.test.ts`、`frontend/src/locales/{zh-TW,en,zh-CN}.ts` | ✅ |
| 4.1.2 | 抽 `layouts/TenantLayout.vue`（既有綠色系 + tenant 名稱頂條） | ADR-004 | `frontend/src/layouts/TenantLayout.vue`、`frontend/src/__tests__/components/TenantLayout.test.ts` | ✅ |
| 4.1.3 | `router/index.ts` 重整：`/platform/*` → PlatformLayout、其餘登入後路由 → TenantLayout | ADR-004 | `frontend/src/router/index.ts`、`frontend/src/stores/menuStore.ts`、`frontend/src/App.vue`、`frontend/src/__tests__/router/layoutShells.test.ts` | ✅ |
| 4.1.4 | `router/guards.ts`：登入後分流（PLATFORM → `/platform/tenants`、TENANT → `/`、IMPERSONATION → `/?impersonating=1`） | ADR-004 / ADR-007 | `frontend/src/router/guards.ts`、`frontend/src/router/index.ts`（新增 `/platform/tenants` 靜態路由）、`frontend/src/stores/authStore.ts`、`frontend/src/__tests__/router/guards.test.ts` | ✅ |
| 4.1.5 | `router/guards.ts`：scope 不符的路徑直接重導（PLATFORM 試圖進 `/admin/*` → `/platform/tenants`） | ADR-004 / ADR-007 | `frontend/src/router/guards.ts`（新增 `resolveScopeRedirect`）、`frontend/src/router/index.ts`（beforeEach 接入 scope 攔截）、`frontend/src/__tests__/router/guards.test.ts` | ✅ |
| 4.1.6 | `composables/useImpersonation.ts` + `components/ImpersonationBanner.vue`：紅色 banner + 剩餘時間 + 結束按鈕 | ADR-002 / ADR-004 | `frontend/src/api/impersonation.ts`、`frontend/src/composables/useImpersonation.ts`、`frontend/src/components/ImpersonationBanner.vue`、`frontend/src/layouts/TenantLayout.vue`、`frontend/src/locales/{zh-TW,en,zh-CN}.ts`、`frontend/src/__tests__/composables/useImpersonation.test.ts`、`frontend/src/__tests__/components/ImpersonationBanner.test.ts` | ✅ |
| 4.1.7 | `stores/auth.ts`：解析並儲存 `scope` 與 `impersonation` claim | ADR-002 / ADR-004 / ADR-007 | `frontend/src/stores/authStore.ts`、`frontend/src/__tests__/stores/authStore.scope.test.ts` | ✅ |
| 4.1.8 | 既有 `superAdminOnly` meta 全面替換為 `requiresScope: 'PLATFORM'` | ADR-004 / ADR-007 | `frontend/src/router/index.ts`、`frontend/src/__tests__/router/routerGuard.test.ts`、`frontend/src/__tests__/router/superAdminOnlyRetirement.test.ts` | ✅ |
| 4.1.9 | 新增 `/platform/impersonations` 頁面（建立 / 歷史紀錄） | ADR-002 / ADR-004 / ADR-007 | `frontend/src/types/impersonation.ts`、`frontend/src/api/impersonation.ts`、`frontend/src/router/index.ts`、`frontend/src/stores/authStore.ts`、`frontend/src/views/platform/ImpersonationManageView.vue`、`frontend/src/locales/{zh-TW,en,zh-CN}.ts`、`frontend/src/__tests__/views/ImpersonationManageView.test.ts`、`frontend/src/__tests__/stores/authStore.scope.test.ts` | ✅ |

### 4.2 驗收標準

- ✅ super_admin 登入直接進 PlatformLayout（深色），看不到綠色 tenant UI
- ✅ tenant_admin 登入進 TenantLayout（綠色），無 platform 選單
- ✅ impersonation 中：TenantLayout + 頂部紅色 banner + 倒數計時，按結束按鈕即還原
- ✅ 試圖手動輸入跨 scope 路徑被 guard 重導
- ✅ 既有 E2E 測試（`menu-management-visibility.spec.ts` 等）更新後全綠

---

## Phase 5：清理

**目標**：6 個月 deprecation 期結束後，移除舊路徑與相容性程式碼。

### 5.1 工作項目

| # | 任務 |
|---|------|
| 5.1.1 | 移除舊路徑（`/v1/admin/tenants/**`、`/v1/auth/tenant-auth-config`、`/v1/auth/users/{id}/tenant-roles`） |
| 5.1.2 | 移除 `ScopeEnforcementFilter` 中「舊 token 無 scope 視為 TENANT」的相容邏輯 |
| 5.1.3 | 移除 JWT 中 `isSuperAdmin` claim（已被 `scope=PLATFORM` 取代） |
| 5.1.4 | 補齊 E2E：impersonation 完整流程、scope 隔離、跨 scope 重導 |
| 5.1.5 | 更新 README / 開發者文件，標記新架構為 GA |

### 5.2 驗收標準

- ✅ 程式碼搜尋 `isSuperAdmin`、`/v1/admin/tenants`、舊路徑：無殘留
- ✅ 全部 E2E 通過
- ✅ 文件同步

---

## Migration 順序總表

| 版本 | 內容 | Phase |
|------|------|-------|
| V60 (existing) | `audit__add_impersonated_by`—已存在，部分滿足 1.1.4 需求 | (prior) |
| V61 (existing) | `rbac__menu_management_back_to_tenant`—已存在，與本計畫無關 | (prior) |
| V62 | seed `ROLE_DEPT_ADMIN` + role_permissions baseline | 1 |
| V63 | `audit_log` 補 `impersonation_session_id` 欄位 | 1 |
| V64 | `impersonation_session` 表 | 1 |
| V65 | ROLE_SUPER_ADMIN 權限只保留 PLATFORM_* | 3 |

---

## 跨 Phase 共用準則

### 命名

- Platform controller 一律 `Platform*Controller`，掛 `/v1/platform/**`
- Service 層 super_admin 邏輯一律從 DB role_permissions 取，**禁止再有「if SUPER_ADMIN return all」旁路**

### 測試

- 每個 Phase PR 必須包含對應單元測試
- Phase 1、3 上線前必跑完整 E2E
- Impersonation 測試帳號：建議在 V62 一併 seed 一組「platform-only super_admin + impersonation 測試案例」

### 文件

- 每完成一個 Phase，在 `01-docs/new-feature/platform-tenant-separation/` 新增 `phase-N-completed.md`，記錄實際與計畫的差異

---

## 依賴關係圖

```
Phase 1 ─┬─▶ Phase 2 ─┐
         │             ├─▶ Phase 3 ─▶ Phase 4 ─▶ Phase 5
         └─────────────┘
```

- Phase 2 與 Phase 1 可並行（不同模組）
- **Phase 3 必須等 Phase 1（Impersonation）+ Phase 2（路徑搬遷）完成**
- Phase 4（前端分流）可在 Phase 3 完成後立即開始
- Phase 5 為 6 個月後清理動作

---

## 下一步

→ 確認本計畫無誤後，從 **Phase 1.1.1（JWT scope claim）** 開始實作。
