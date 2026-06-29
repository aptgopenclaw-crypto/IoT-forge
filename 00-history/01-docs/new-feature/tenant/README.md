# Tenant 角色 / SUPER_ADMIN 邊界重整

本目錄記錄「SUPER_ADMIN 權限過廣」議題的完整討論與設計脈絡。

## 文件索引

| # | 文件 | 說明 | 狀態 |
|---|---|---|---|
| 1 | [01-super-admin-inventory.md](./01-super-admin-inventory.md) | Step 1：盤點所有 SUPER_ADMIN 使用點（端點、bypass、UI 判斷） | ✅ 完成 |
| 2 | [02-role-redesign.md](./02-role-redesign.md) | Step 2：角色矩陣、權限碼設計、端點遷移對照、Phase A/B 拆分 | ✅ 全部確認 |
| 3 | [03-menu-separation.md](./03-menu-separation.md) | Step 3：平台菜單 vs 租戶菜單分離、`scope` 欄位、菜單樹重組 | ✅ 全部確認 |
| 4 | [04-phase-a-implementation.md](./04-phase-a-implementation.md) | Phase A 實作紀錄：權限碼、端點守衛、菜單 scope、測試 | ✅ 完成 |
| 5 | [05-phase-b-impersonation.md](./05-phase-b-impersonation.md) | Phase B：impersonation、getMyMenus 改寫、審計追蹤 | ✅ 完成 |

## 背景

起因：使用者回報「場域管理者登入後看不到公佈欄」，定位後發現是 `MenuService.getMyMenus` 用 `IN` 查詢無法匹配 `permission_code IS NULL` 的公開菜單（已修復，見 [user-module-code-review-v2.md](../../code-review/user/user-module-code-review-v2.md) Menu 修復段）。

延伸討論：藉此機會檢視 `SUPER_ADMIN` 是否被過度使用、以及多租戶下的角色邊界是否清楚。三個關鍵問題：

1. **菜單 bypass 過度寬鬆**：SUPER_ADMIN 在 `getMyMenus` 直接回傳所有 visible 菜單，包含「不屬於當前 tenant context」的項目。
2. **無 impersonation 機制**：SUPER_ADMIN 直接寫租戶資料時，audit log 看不到「他是代誰操作」。
3. **`hasRole('SUPER_ADMIN')` 邊界模糊**：部分端點守衛混用 `ADMIN or SUPER_ADMIN`，把平台角色和租戶角色綁在同一條規則上。

## 處理流程

```
Step 1 盤點 ──► Step 2 角色再設計 ──► Step 3 菜單分離 ──► Step 4 權限矩陣 + DB migration ──► Step 5 impersonation (Phase B)
   ✅              ✅                     ✅                  Phase A 實作                       Phase B
```

---

## Phase A 執行清單（待使用者下達 GO 後依序實作）

### 後端

1. **RBAC seed**（新 migration `V53__rbac__platform_permissions.sql`）
   - 新增 5 個權限碼：`PASSWORD_POLICY_MANAGE`、`PLATFORM_TENANT_MANAGE`、`PLATFORM_PASSWORD_POLICY_MANAGE`、`PLATFORM_USER_TENANT_MAPPING`、`PLATFORM_IMPERSONATE`
   - 角色權限映射：
     - `ROLE_SUPER_ADMIN` 取得全部 4 個 `PLATFORM_*`
     - `ROLE_ADMIN` 取得 `PASSWORD_POLICY_MANAGE`

2. **端點守衛改寫**（依 [02-role-redesign.md § 4](./02-role-redesign.md) 對照表）
   - `SecurityConfig.java#L151`：`/v1/admin/tenants/**` → `hasAuthority('PLATFORM_TENANT_MANAGE')`
   - `TenantAdminController` class-level：同上
   - `PlatformPasswordPolicyController` class-level：`hasAuthority('PLATFORM_PASSWORD_POLICY_MANAGE')`
   - `TenantPasswordPolicyController` A7–A9：`hasAuthority('PASSWORD_POLICY_MANAGE')`
   - `TenantAuthConfigController` class-level：`hasAuthority('PLATFORM_TENANT_MANAGE')`
   - `UserAdminController` A12–A14（tenant-roles CRUD）：`hasAuthority('PLATFORM_USER_TENANT_MAPPING')`

3. **A11 `restoreUser` 整條移除**
   - 刪除 `UserAdminController.restoreUser`
   - 刪除 `UserAdminService.restoreUser`
   - 刪除前端按鈕 + view
   - 刪除相關測試
   - DB 不動（軟刪除 timestamp 仍保留供 audit）

4. **A10 `CreateTenantRequest` 擴充**
   - 加 `initialAuthMethod` 欄位（預設 `LOCAL`）
   - `TenantAdminService.createTenant()` 同時寫入 `tenant_auth_config`
   - 避免「租戶已建但無認證設定」狀態

5. **菜單 scope 欄位**（新 migration `V54__rbac__menu_scope.sql`）
   - `menus` 表加 `scope VARCHAR(20) NOT NULL DEFAULT 'TENANT'` + check constraint
   - 重分類：`UPDATE menus SET scope='PLATFORM' WHERE menu_id IN (11, 36, 37)`
   - 重分類：`UPDATE menus SET scope='PUBLIC' WHERE menu_id = 34`
   - 新增 menu_id=100「平台管理」DIRECTORY
   - 將 menu_id IN (11, 36, 37) 的 `parent_id` 改為 100
   - 新增 menu_id=101「租戶管理」PAGE，`permission_code='PLATFORM_TENANT_MANAGE'`
   - menu_id=37 路由改為 `/platform/auth-config`

6. **MenuEntity / Repository / DTO 加 scope**
   - `MenuEntity.scope`
   - `MenuRepository.findByScopeAndVisibleTrue(String scope)`（為 Phase B 預備）
   - `MenuDto.scope`、`UserMenuDto.scope`
   - `CreateMenuRequest` / `UpdateMenuRequest` 加 scope

### 前端

7. **菜單管理頁加 scope 下拉**
   - `MenuManageView.vue` 表單 + 列表加 scope 欄位
   - i18n key

8. **AppSidebar 硬編碼移除**
   - 刪除 `v-if="isSuperAdmin"` 的「租戶管理」項
   - 信任後端菜單回傳（menu_id=101 會自動帶上）

9. **認證方式設定路由改名**
   - `router/index.ts` 與相關 view 引用：`/admin/security/auth-config` → `/platform/auth-config`
   - API 路徑不動（後端 `/v1/auth/tenant-auth-config/**`）

10. **配套清理**
    - 任何 `restoreUser` 的前端 API client / 按鈕 / i18n key
    - 任何提示文案中提到「還原已刪除使用者」的字串

### 測試

11. **後端單元測試**
    - 新權限碼的 seed 驗證
    - 各 controller 守衛變更後的 `@WebMvcTest` 更新
    - `softDeleteUser` 後不存在還原路徑（移除 restore 測試）
    - `createTenant` 同時建立 auth config

12. **前端單元測試**
    - `AppSidebar` 不再依賴 isSuperAdmin 來決定租戶管理項
    - 菜單管理表單含 scope
    - `__tests__/types/strictTypeUsage.test.ts` 等若有 `MenuDto` mock 要加 scope

---

## Phase B 待辦（之後另起議題）

- `MenuService.getMyMenus` 重寫為「PLATFORM ∪ TENANT ∪ PUBLIC」
- `TenantFilterAspect` 收緊（裸 SUPER_ADMIN 不能寫租戶資料）
- Impersonation 端點 + JWT claim + 前端 UI
- `audit_log` / `user_info_log` 新增 `impersonated_by` 欄位
