# SUPER_ADMIN 權限盤點（Step 1）

> 目的：在重新設計平台 / 租戶角色邊界之前，先列出目前所有 `SUPER_ADMIN`（角色名 `ROLE_SUPER_ADMIN`，role_id 同名常數）被使用的位置，並逐一判斷其屬性：
>
> - **PLATFORM**：純平台維運，應保留為 SUPER_ADMIN 專屬。
> - **TENANT**：實際上是「該租戶內最高權限」操作，不該綁在 SUPER_ADMIN，應拆給租戶角色（例如未來的 `TENANT_OWNER`）。
> - **AMBIGUOUS**：目前是 `ADMIN or SUPER_ADMIN` 並存，需要決定是否仍要讓平台角色直接執行租戶內操作。
> - **BYPASS**：非端點，而是 service / filter 內部的「跳過租戶隔離 / 跳過權限」邏輯。

盤點範圍：`backend/src/main/java/**`。
盤點關鍵字：`hasRole('SUPER_ADMIN')`、`ROLE_SUPER_ADMIN`、`SUPER_ADMIN_ROLE_ID`、`isSuperAdmin`、`SecurityConfig` 中的 `hasRole("SUPER_ADMIN")`。

---

## A. HTTP 端點

| # | HTTP | Path | 位置 | 守衛表達式 | 分類 | 建議 |
|---|---|---|---|---|---|---|
| 1 | * | `/v1/admin/tenants/**` | [SecurityConfig.java#L151](backend/src/main/java/com/taipei/iot/config/SecurityConfig.java#L151) | `hasRole("SUPER_ADMIN")` | PLATFORM | 保留。租戶 CRUD 是平台操作。 |
| 2 | GET | `/v1/admin/tenants` | [TenantAdminController.java#L34](backend/src/main/java/com/taipei/iot/tenant/controller/TenantAdminController.java#L34) | class-level `hasRole('SUPER_ADMIN')` | PLATFORM | 保留。 |
| 3 | POST | `/v1/admin/tenants` | [TenantAdminController.java#L43](backend/src/main/java/com/taipei/iot/tenant/controller/TenantAdminController.java#L43) | class-level | PLATFORM | 保留。 |
| 4 | PUT | `/v1/admin/tenants/{tenantId}` | [TenantAdminController.java#L51](backend/src/main/java/com/taipei/iot/tenant/controller/TenantAdminController.java#L51) | class-level | PLATFORM | 保留。 |
| 5 | PATCH | `/v1/admin/tenants/{tenantId}/enabled` | [TenantAdminController.java#L58](backend/src/main/java/com/taipei/iot/tenant/controller/TenantAdminController.java#L58) | class-level | PLATFORM | 保留。 |
| 6 | GET / PUT | `/v1/platform/password-policy` | [PlatformPasswordPolicyController.java#L30](backend/src/main/java/com/taipei/iot/auth/controller/PlatformPasswordPolicyController.java#L30) | class-level `hasRole('SUPER_ADMIN')` | PLATFORM | 保留。平台預設下限。 |
| 7 | GET | `/v1/auth/password-policy/tenant` | [TenantPasswordPolicyController.java#L49](backend/src/main/java/com/taipei/iot/auth/controller/TenantPasswordPolicyController.java#L49) | `hasRole('ADMIN') or hasRole('SUPER_ADMIN')` | AMBIGUOUS | 主屬租戶；SUPER_ADMIN 走代操（impersonation / 支援模式）較乾淨。短期保留 OR 條件。 |
| 8 | PUT | `/v1/auth/password-policy/tenant` | [TenantPasswordPolicyController.java#L55](backend/src/main/java/com/taipei/iot/auth/controller/TenantPasswordPolicyController.java#L55) | 同上 | AMBIGUOUS | 同上。 |
| 9 | DELETE | `/v1/auth/password-policy/tenant/{key}` | [TenantPasswordPolicyController.java#L63](backend/src/main/java/com/taipei/iot/auth/controller/TenantPasswordPolicyController.java#L63) | 同上 | AMBIGUOUS | 同上。 |
| 10 | GET / PUT / DELETE / POST | `/v1/auth/tenant-auth-config/**` | [TenantAuthConfigController.java#L28](backend/src/main/java/com/taipei/iot/auth/provider/config/controller/TenantAuthConfigController.java#L28) | class-level `hasRole('SUPER_ADMIN')` | AMBIGUOUS / TENANT | 操作對象是 `TenantContext.getCurrentTenantId()`，屬於該租戶設定。目前完全擋掉租戶端 → 租戶 IT 無法自助設定 IdP。建議改為「TENANT_OWNER 可改、SUPER_ADMIN 可代操」。 |
| 11 | PATCH | `/v1/admin/users/{userId}/restore` | [UserAdminController.java#L89](backend/src/main/java/com/taipei/iot/user/controller/UserAdminController.java#L89) | `hasRole('SUPER_ADMIN')` | TENANT | 還原使用者屬於該租戶治理範圍。建議改為 `hasAuthority('USER_RESTORE')`（新權限碼），預設給 TENANT_OWNER。 |
| 12 | GET | `/v1/admin/users/{userId}/tenant-roles` | [UserAdminController.java#L98](backend/src/main/java/com/taipei/iot/user/controller/UserAdminController.java#L98) | `hasRole('SUPER_ADMIN')` | PLATFORM | 保留。跨租戶 mapping 查詢。 |
| 13 | POST | `/v1/admin/users/{userId}/tenant-roles` | [UserAdminController.java#L105](backend/src/main/java/com/taipei/iot/user/controller/UserAdminController.java#L105) | `hasRole('SUPER_ADMIN')` | PLATFORM | 保留。跨租戶授權。 |
| 14 | DELETE | `/v1/admin/users/{userId}/tenant-roles/{mappingId}` | [UserAdminController.java#L114](backend/src/main/java/com/taipei/iot/user/controller/UserAdminController.java#L114) | `hasRole('SUPER_ADMIN')` | PLATFORM | 保留。 |

> 註：`AuditController` 的 `ROLE_SUPER_ADMIN` 出現在 [AuditController.java#L57](backend/src/main/java/com/taipei/iot/audit/controller/AuditController.java#L57)、[L80](backend/src/main/java/com/taipei/iot/audit/controller/AuditController.java#L80)，不是端點守衛，而是用來判斷 `isAdmin` 旗標決定回傳資料的範圍（admin 看全部、一般人只看自己）。屬於資料範圍判斷，不在本盤點端點清單；列入 § C。

---

## B. 服務層 / 內部 bypass

| # | 位置 | 行為 | 分類 | 建議 |
|---|---|---|---|---|
| B1 | [MenuService.java#L42](backend/src/main/java/com/taipei/iot/rbac/service/MenuService.java#L42) | `getMyMenus()` 若使用者具 SUPER_ADMIN，回傳所有 visible 菜單，不檢查 `permission_code` | BYPASS | 短期保留；中期改為「平台菜單 + 該租戶菜單聯集」，避免 SUPER_ADMIN 在租戶模式下看到平台維運項。 |
| B2 | [RoleService.java#L37](backend/src/main/java/com/taipei/iot/rbac/service/RoleService.java#L37) | `SUPER_ADMIN_ROLE_ID` 常數 | BYPASS | 保留，但要文件化「此角色不可被一般 UI 指派」。 |
| B3 | [RoleService.java#L258](backend/src/main/java/com/taipei/iot/rbac/service/RoleService.java#L258) | 角色 assign 流程裡的 SUPER_ADMIN 限制 / 例外 | BYPASS | 待 Step 2 細看流程後決定。 |
| B4 | TenantFilterAspect / 資料隔離 AOP（先前討論已知） | SUPER_ADMIN 跳過 tenant filter | BYPASS | 維持，但建議加 impersonation context，記錄「以哪個 tenant 身份操作」。 |

---

## C. 資料範圍 / 顯示判斷（非守衛）

| # | 位置 | 用途 |
|---|---|---|
| C1 | [AuditController.java#L57](backend/src/main/java/com/taipei/iot/audit/controller/AuditController.java#L57)、[L80](backend/src/main/java/com/taipei/iot/audit/controller/AuditController.java#L80) | `isAdmin = hasAnyAuthority("ROLE_SUPER_ADMIN","ROLE_ADMIN","ROLE_DEPT_ADMIN")`，影響 `queryForExport` / `getUserUsageHistory` 的查詢範圍 |
| C2 | [authStore.ts#L93](frontend/src/stores/authStore.ts#L93)、[L117](frontend/src/stores/authStore.ts#L117) | 登入後若 `isSuperAdmin && tenants.length > 0`，提示選租戶 |
| C3 | [AppSidebar.vue#L46](frontend/src/components/AppSidebar.vue#L46)、[L103](frontend/src/components/AppSidebar.vue#L103) | 側邊欄顯示「系統 / 租戶」入口 |
| C4 | [TenantRoleManager.vue#L30](frontend/src/components/TenantRoleManager.vue#L30)、[L60](frontend/src/components/TenantRoleManager.vue#L60)、[L182](frontend/src/components/TenantRoleManager.vue#L182) | 是否顯示 tenant 選擇欄位 |
| C5 | [MenuManageView.vue#L22](frontend/src/views/admin/menu/MenuManageView.vue#L22) | `canWrite` 僅 SUPER_ADMIN |
| C6 | [CreateUserView.vue#L54](frontend/src/views/admin/CreateUserView.vue#L54)、[L114](frontend/src/views/admin/CreateUserView.vue#L114)、[L189](frontend/src/views/admin/CreateUserView.vue#L189) | 建立使用者時是否顯示 tenant 選擇 |

---

## D. 初步分類統計

- **PLATFORM**（明確平台級，應留）：A1–A6、A12–A14 共 9 項端點。
- **AMBIGUOUS**（平台/租戶兩可，需 Step 2 決策）：A7–A10 共 4 項端點。
- **TENANT**（誤掛在平台角色上，應下放）：A11（`restoreUser`）1 項。
- **BYPASS / 內部**：B1–B4 共 4 項。
- **資料範圍 / UI 顯示**：C1–C6 共 6 項，不需要重新切角色，但要隨 Step 2 一起確認顯示邏輯仍正確。

---

## E. Step 2 待決議題（先列，本步驟不展開）

1. 是否新增 `TENANT_OWNER` 角色，把 A11、A7–A10 的租戶側操作移過去？
2. 是否新增 `PLATFORM_SUPPORT` + impersonation：SUPER_ADMIN 預設只看平台，要操作租戶資料必須切換成「以租戶 X 身份」並寫 audit？
3. `MenuService` SUPER_ADMIN bypass 是否改為「平台菜單清單 union」，避免在租戶 context 看到不相干項？
4. `TenantAuthConfigController` 是否開放給租戶內角色管理？（目前完全平台壟斷）
5. `restoreUser` 是否確實需要平台批准？或租戶內 OWNER 自治即可？

> 完成盤點後，下一步請使用者確認上表分類無誤，再進入 Step 2「角色再設計」。
