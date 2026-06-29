# Step 2 — 角色再設計

> 前置：[01-super-admin-inventory.md](./01-super-admin-inventory.md)
>
> 目的：依 Step 1 盤點結果，定義新的角色矩陣，並把每一項 SUPER_ADMIN 守衛重新對應到「正確的角色 / 權限碼」。
> 本步驟只做設計與映射，不動程式碼；實作分到 Step 3（菜單）與 Step 4（權限矩陣文件 + 程式碼遷移）。

---

## 1. 設計原則

1. **平台 / 租戶分離**：平台角色只做平台事；租戶內事務一律走租戶角色 + 權限碼。
2. **權限碼優先於角色名**：除了「跨租戶平台操作」與「impersonation 切換能力」這兩類本質性角色判斷外，端點守衛一律使用 `hasAuthority('XXX')`，不再 `hasRole('SUPER_ADMIN')`。
3. **SUPER_ADMIN 不能直接寫租戶內資料**：要操作租戶內資料必須先進入 impersonation context，且該 context 內以「目標租戶的 TENANT_OWNER」身份運作，並全程審計。
4. **TENANT_OWNER 自治**：每個租戶擁有一個最高權限角色，可管自家所有設定（含 IdP、密碼政策、還原已刪除帳號），不必再 ping 平台。
5. **去除 `hasRole('ADMIN') or hasRole('SUPER_ADMIN')` 模式**：兩種情境合在同一個守衛，導致語意混淆，且讓「SUPER_ADMIN 直接寫租戶資料」變成預設行為。

---

## 2. 目標角色矩陣

| 角色 (role_id) | 範圍 | 來源 | 主要職責 |
|---|---|---|---|
| `ROLE_SUPER_ADMIN` | 平台 | 既有 | 租戶 CRUD、租戶 IdP 初始設定、平台密碼政策、跨租戶帳號 mapping、菜單管理、impersonation 入口 |
| `ROLE_ADMIN` (TENANT_ADMIN) | 單一租戶 | 既有 | 該租戶內最高權限：使用者、角色、部門、公告，以及在平台下限內微調密碼政策 |
| `ROLE_DEPT_ADMIN` | 部門 | 既有 | 限定部門範圍內的使用者管理 |
| `ROLE_USER` / `ROLE_VIEWER` | 一般 | 既有 | 自助、唯讀 |

> 命名說明：在文件中統一稱呼 `ROLE_ADMIN` 為 TENANT_ADMIN 以區分平台角色；DB / JWT / 程式碼裡仍維持 `ROLE_ADMIN`。
>
> **不引入新角色**：經 Q3 連動討論（2026-05-29），原計畫的 `ROLE_TENANT_OWNER` 不再需要——其唯一獨佔權限 `USER_RESTORE` 已於 § 3.1 業務決議下移除；其餘原本歸屬的 `PASSWORD_POLICY_MANAGE` 與 TENANT_ADMIN 共用，`TENANT_AUTH_CONFIG_MANAGE` 收歸 SUPER_ADMIN。

---

## 3. 新增權限碼

下列權限碼是把目前綁在 `hasRole('SUPER_ADMIN')` 上的能力重新分配。

| 權限碼 | 對應端點 | 預設給誰 |
|---|---|---|
| ~~`USER_RESTORE`~~ | _端點 A11 整條移除（見 § 4），不再需要此權限碼_ | — |
| `PASSWORD_POLICY_MANAGE` | `GET/PUT/DELETE /v1/auth/password-policy/tenant/**` | TENANT_OWNER + TENANT_ADMIN（兩者皆可在平台下限內微調） |
| ~~`TENANT_AUTH_CONFIG_MANAGE`~~ | _移除_：A10 完全收歸 SUPER_ADMIN，併入 `PLATFORM_TENANT_MANAGE` | — |
| `PLATFORM_TENANT_MANAGE` | `/v1/admin/tenants/**`、`/v1/auth/tenant-auth-config/**` | SUPER_ADMIN（僅此角色擁有） |
| `PLATFORM_PASSWORD_POLICY_MANAGE` | `/v1/platform/password-policy/**` | SUPER_ADMIN |
| `PLATFORM_USER_TENANT_MAPPING` | `/v1/admin/users/{userId}/tenant-roles/**`（GET/POST/DELETE） | SUPER_ADMIN |
| `PLATFORM_IMPERSONATE` | （Phase B）切換為「以 tenant X 操作」 | SUPER_ADMIN |

> 收斂後實際新增 5 個權限碼（原 7 個移除 `USER_RESTORE` 與 `TENANT_AUTH_CONFIG_MANAGE`）。

### 3.1 業務規則：軟刪除終態化

- `softDeleteUser` 為終態：帳號永久失效、不可登入、不可由任何角色復原。
- 若同一自然人離職後回鍋 → 建立新帳號（新 userId），舊帳號保留供 audit 追溯。
- 類比：員工離職後重新入職也是新員工編號，不重用舊編號。
- 影響：移除 `restoreUser` controller / service / 前端按鈕 / 相關測試（見 § 4 A11）。
- 範圍外：`disableUser`（在職但暫停，例：留職停薪、長假）與其反向啟用屬另一議題，本次不處理。

---

## 4. 端點遷移對照表

對應 Step 1 § A 的每一條：

| Step 1 # | 路徑 | 現況守衛 | 目標守衛 | 備註 |
|---|---|---|---|---|
| A1 | `/v1/admin/tenants/**`（SecurityConfig） | `hasRole("SUPER_ADMIN")` | `hasAuthority('PLATFORM_TENANT_MANAGE')` | 改用權限碼。SecurityConfig 仍可保留作為 defense-in-depth。 |
| A2–A5 | `TenantAdminController` 4 個方法 | class-level `hasRole('SUPER_ADMIN')` | class-level `hasAuthority('PLATFORM_TENANT_MANAGE')` | 同上 |
| A6 | `PlatformPasswordPolicyController` | class-level `hasRole('SUPER_ADMIN')` | class-level `hasAuthority('PLATFORM_PASSWORD_POLICY_MANAGE')` | |
| A7–A9 | `/v1/auth/password-policy/tenant/**` | `hasRole('ADMIN') or hasRole('SUPER_ADMIN')` | `hasAuthority('PASSWORD_POLICY_MANAGE')` | 預設給 TENANT_OWNER + TENANT_ADMIN；只能在平台下限內調參，已由 `PasswordPolicyService` 強制。SUPER_ADMIN 要操作 → 走 impersonation。 |
| A10 | `/v1/auth/tenant-auth-config/**` | class-level `hasRole('SUPER_ADMIN')` | `hasAuthority('PLATFORM_TENANT_MANAGE')` | **不下放**。IdP 不只是設定值，還牽涉外部整合 / 接管使用者目錄等配套，沿用「於建立租戶時由 SUPER_ADMIN 設定」流程，後續變更亦由平台執行。 |
| A11 | `PATCH /v1/admin/users/{userId}/restore` | `hasRole('SUPER_ADMIN')` | **整條移除** | 業務決議：軟刪除即終態，不可復原。見 § 3.1。 |
| A12–A14 | `/v1/admin/users/{userId}/tenant-roles/**` | `hasRole('SUPER_ADMIN')` | `hasAuthority('PLATFORM_USER_TENANT_MAPPING')` | 跨租戶 mapping，僅 SUPER_ADMIN |

對 § B（內部 bypass）：

| Step 1 # | 位置 | 變更 |
|---|---|---|
| B1 | `MenuService.getMyMenus` SUPER_ADMIN bypass | 維持「角色名判斷」，但語意改為「平台菜單 ∪ 當前租戶菜單」。詳見 Step 3。 |
| B2 | `RoleService.SUPER_ADMIN_ROLE_ID` | 保留常數；用於「禁止任何 UI 指派此角色」「禁止刪除此角色」「禁止移除此角色的權限」。 |
| B3 | `RoleService` line 258 角色 assign 限制 | Phase A 內檢視：應追加「TENANT_OWNER 也不可由非 SUPER_ADMIN 指派」保護。 |
| B4 | `TenantFilterAspect` SUPER_ADMIN 跳過 | **變更**：只有當 SUPER_ADMIN 處於 impersonation context 時才允許跳過；裸 SUPER_ADMIN 對租戶資源的請求一律 403（強制走 impersonation）。 |

對 § C（資料範圍 / UI）：

| Step 1 # | 變更 |
|---|---|
| C1 `AuditController.isAdmin` | 將判斷邏輯改為 `hasAuthority('AUDIT_LIST_ALL_TENANT_USERS')`（新權限），給 TENANT_OWNER + TENANT_ADMIN + DEPT_ADMIN。不再用 role 名判斷。 |
| C2 `authStore` 登入後選租戶 | 維持，但前端依據 `isSuperAdmin || isPlatformSupport` 顯示。 |
| C3 `AppSidebar` 平台選單 | 改為依「是否擁有任一 PLATFORM_* 權限碼」顯示，而非 isSuperAdmin。 |
| C4 `TenantRoleManager` 顯示 tenant 欄位 | 維持 isSuperAdmin 判斷（這是平台級操作 UI）。 |
| C5 `MenuManageView` canWrite | 改為 `hasAuthority('MENU_MANAGE')`，授給 SUPER_ADMIN。 |
| C6 `CreateUserView` 顯示 tenant 欄位 | 維持 isSuperAdmin（屬於跨租戶建帳號流程）。 |

---

## 5. Impersonation 機制（高層）

> 目的：讓 SUPER_ADMIN 在租戶資料上的任何寫操作都有「為誰操作、被誰操作」的審計線索，並避免「裸 SUPER_ADMIN token」無限制穿透。
> 細部設計留到 Phase A 之後（Step 5）。

- 入口：`POST /v1/platform/impersonate` `{ tenantId }`，需要 `PLATFORM_IMPERSONATE` 權限。
- 回傳：短 TTL（建議 ≤ 30 分鐘）impersonation token，內含 `actingTenantId` + `impersonatedBy=<superAdminUserId>` claim。
- 攜帶 impersonation token 的請求：
  - `TenantContext.getCurrentTenantId()` 回傳 `actingTenantId`。
  - 權限判定以「TENANT_ADMIN 在該 tenant 內」為準，不再額外擁有 SUPER_ADMIN 權限。
  - `TenantFilterAspect` 不跳過租戶過濾，正常走 tenant scope。
  - 每一次寫操作 audit log 自動帶 `impersonatedBy` 欄位。
- 退出：`POST /v1/platform/impersonate/exit` 或 token 過期。

---

## 6. 與既有設計的衝突 / 風險

1. **Token / Session 結構變動**（Phase B）：impersonation claim 需要進 JWT，要評估 token revoke / refresh 流程是否相容。
2. **Audit schema**（Phase B）：`audit_log`、`user_info_log` 需新增 `impersonated_by` 欄位（nullable）。
3. **前端側邊欄判斷**：C3 改用 PLATFORM_* 權限碼後，必須等 RBAC 同步（JWT 內已有 authorities）才能渲染，避免閃爍。
4. **既有 `hasRole('ADMIN') or hasRole('SUPER_ADMIN')` 端點**（A7–A9）：切換為 `hasAuthority('PASSWORD_POLICY_MANAGE')` 後，**裸 SUPER_ADMIN token 直接呼叫會 403**（並未授予該權限碼）。平台侧要操作請走 impersonation（Phase B）；Phase A 期間如需上線、且概率低，可接受由 SUPER_ADMIN 手動登入一個 TENANT_ADMIN 帳號作為過渡。
5. **A11 `restoreUser` 移除**：需同步刪除前端「還原」按鈕、後端 test、API 文件；如未來有誊誤刪的故障處理需求 → 走 DBA support ticket，不由 API 提供。
6. **A10 IdP 設定不下放**：`CreateTenantRequest` 需擴充「初始認證方式」欄位（預設 LOCAL），並在建立租戶同時寫入 `tenant_auth_config`。避免「租戶已建但無認證設定」狀態。

---

## 7. Phase A / B 拆分建議

**Phase A（不引入 impersonation，先做權限碼重新對接）**

- 新增 5 個權限碼（§ 3）
- 端點守衛改為權限碼（§ 4 表）
- A11 `restoreUser` 整條移除（controller / service / 前端 / 測試）
- A10 IdP 設定併入 SUPER_ADMIN 權限碼；擴充 `CreateTenantRequest` 含初始認證方式
- `PASSWORD_POLICY_MANAGE` 預設掃給 TENANT_ADMIN（軟遷移，不破現狀使用者）
- 過渡期：`TenantFilterAspect` SUPER_ADMIN bypass 維持
- 過渡期：`MenuService.getMyMenus` SUPER_ADMIN bypass 維持
- **無需新增角色 / 無需 seed migration**（因不引入 TENANT_OWNER）

**Phase B（impersonation + 收緊）**

- 引入 `POST /v1/platform/impersonate`
- `TenantFilterAspect` 改為「impersonation context 才跳過」
- `MenuService.getMyMenus` 改為「平台菜單 ∪ 當前租戶菜單」
- `audit_log` / `user_info_log` 新增 `impersonated_by`

---

## 8. 本步驟交付物

- 本文件（角色矩陣、權限碼、端點遷移對照、Phase 拆分）
- 不動程式碼

**確認進度**：
1. ✅ § 2 角色矩陣 — 確認「不引入 PLATFORM_SUPPORT、`ROLE_ADMIN` 不改名」（2026-05-29）；後續 Q3 連動決議：**亦不引入 `ROLE_TENANT_OWNER`**
2. ✅ § 3 權限碼命名 — 全表通過；保留「租戶端無前綴、平台端 `PLATFORM_` 前綴」慣例；`MANAGE` 不再細拆 VIEW/UPDATE（2026-05-29）；後續收斂為 5 個權限碼
3. ✅ § 4 端點遷移對照 — 補充決議（2026-05-29）：
   - A7–A9 密碼政策：`PASSWORD_POLICY_MANAGE` 授予 TENANT_ADMIN（場域可在平台下限內微調）
   - A10 IdP 設定：**不下放**，收歸 SUPER_ADMIN（併入 `PLATFORM_TENANT_MANAGE`）；建立租戶流程需擴充以指定初始認證方式
   - A11 `restoreUser`：**整條端點移除**，軟刪除終態化（見 § 3.1）
   - 連動：`ROLE_TENANT_OWNER` 失去存在意義，不引入
4. ✅ § 7 Phase A / B 拆分 — 照表通過（2026-05-29）。Phase A 不引入 impersonation；`TenantFilterAspect` 與 `MenuService.getMyMenus` 的 SUPER_ADMIN bypass 維持過渡。

全部確認完成，進入 Step 3：菜單分離與 RBAC seed 設計。
