# Step 3 — 菜單分離設計

> 前置：[02-role-redesign.md](./02-role-redesign.md)（已全部確認）
>
> 目的：在 Phase B 時把 `MenuService.getMyMenus` 的 SUPER_ADMIN bypass 從「回傳所有菜單」改為「回傳平台菜單 ∪ 當前租戶菜單」。本步驟先做設計與 DB schema / seed 的準備工作（可在 Phase A 一起上線，不必等 Phase B）。

---

## 1. 現況盤點

### 1.1 菜單樹（依現有 seed）

| menu_id | 名稱 | 類型 | permission_code | 屬性（建議分類） |
|---|---|---|---|---|
| 1 | User Management | DIRECTORY | — | TENANT |
| 2 | User List | PAGE | `USER_LIST` | TENANT |
| 3 | Create User | PAGE | `USER_CREATE` | TENANT |
| 10 | System Management | DIRECTORY | — | **混合**（目前同時放租戶設定與平台設定） |
| 11 | Menu Management | PAGE | `MENU_LIST` | PLATFORM |
| 12 | Department Management | PAGE | `DEPT_LIST` | TENANT |
| 13 | Role Management | PAGE | `ROLE_LIST` | TENANT |
| 20 | Audit Center | DIRECTORY | — | TENANT |
| 21 | Audit History | PAGE | `AUDIT_LIST` | TENANT |
| 22 | Audit Statistics | PAGE | `AUDIT_STATS` | TENANT |
| 23 | Login Logs | PAGE | `AUDIT_LIST` | TENANT |
| 30 | Monitoring Center | DIRECTORY | — | TENANT |
| 31 | Log Summary | PAGE | `LOG_SUMMARY_VIEW` | TENANT |
| 32 | 系統設定 | PAGE | `SYSTEM_SETTINGS_VIEW` | TENANT |
| 33 | 公告管理 | PAGE | `ANNOUNCEMENT_VIEW` | TENANT |
| 34 | 公告欄 | PAGE | `NULL` | PUBLIC |
| 35 | 密碼策略 | PAGE | `PASSWORD_POLICY_TENANT_VIEW` | TENANT |
| 36 | 平台密碼策略 | PAGE | `PASSWORD_POLICY_PLATFORM_VIEW` | PLATFORM |
| 37 | 認證方式設定 | PAGE | `AUTH_CONFIG_VIEW` | PLATFORM（依 Step 2 A10 決議：不下放） |
| _（未建）_ | 租戶管理 | — | 前端 [AppSidebar.vue#L103](frontend/src/components/AppSidebar.vue#L103) 硬編碼 `/admin/system/tenants`，未進 DB | PLATFORM |

### 1.2 現有 `getMyMenus` 行為（[MenuService.java#L40-L82](backend/src/main/java/com/taipei/iot/rbac/service/MenuService.java#L40)）

- SUPER_ADMIN → 回傳「所有 visible 菜單」（含租戶設定、公告管理…，不分 scope）
- 一般使用者 → permittedMenus（依角色權限碼） ∪ publicMenus（permission_code IS NULL） ∪ 有可見子節點的 DIRECTORY

**問題**：
- SUPER_ADMIN 在租戶 context（impersonation）下會看到其他租戶的設定入口
- 「平台菜單」與「租戶菜單」沒有任何 schema 上的標記，全靠人工命名與 permission_code 慣例
- 「租戶管理」一個重要 PLATFORM 入口竟然不在 DB 裡，前端硬編碼

---

## 2. 設計選項：如何標記菜單 scope

| 選項 | 做法 | 優點 | 缺點 |
|---|---|---|---|
| (A) **新增 `scope` 欄位** | `menus.scope ENUM('PLATFORM','TENANT','PUBLIC')`，預設 TENANT | 顯式、SQL 可直接查、未來易擴充（例如加 'SHARED'） | 一次 schema migration |
| (B) **約定 permission_code 前綴** | `PLATFORM_*` permission_code 的菜單 = PLATFORM 菜單 | 不動 schema | 約定脆弱；公告欄等 `permission_code IS NULL` 菜單無法表達 PLATFORM；菜單管理 (`MENU_LIST`) 也不會自動成為 PLATFORM |
| (C) **硬編碼 menu_id 清單** | `MenuService` 內維護 `Set<Long> PLATFORM_MENU_IDS = Set.of(11, 36, 37, ...)` | 立刻可行 | 反模式；每新增平台菜單都要改 Java 代碼 |

**我的建議：(A)**。理由：
- 與 Step 2 § 3 引入 `PLATFORM_*` 權限碼是同一個概念，乾脆把「分類」做成資料模型
- 菜單管理頁面只要新增 scope dropdown 即可
- Step 1 § B1 提到的 MenuService 重寫只需要一條 `findByScopeAndVisibleTrue("PLATFORM")` 查詢

---

## 3. 目標菜單樹

維持現有 `parent_id` 階層，但加上 `scope` 欄位後重新分類：

```
[PLATFORM 區]
├─ 租戶管理         /admin/system/tenants      (新 seed, scope=PLATFORM)
├─ 菜單管理         menu_id=11                 (改 scope=PLATFORM)
├─ 平台密碼策略     menu_id=36                 (改 scope=PLATFORM)
└─ 認證方式設定     menu_id=37                 (改 scope=PLATFORM；改父節點到平台目錄)

[TENANT 區]
├─ User Management (1)
├─ System Management (10) — 移除子節點 11/36/37 後剩：12/13/32/33/35
├─ Audit Center (20)
└─ Monitoring Center (30)

[PUBLIC 區]
└─ 公告欄 (34, scope=PUBLIC, permission_code=NULL)
```

**結構變更**：
1. menu_id=11 (Menu Management)、36 (平台密碼策略)、37 (認證方式設定) → 從 parent_id=10 移出，掛到新的 PLATFORM directory 下（例如 menu_id=100, name="平台管理"）
2. 新增 menu_id 給「租戶管理」（取代前端硬編碼）
3. menu_id=10 (System Management) 內容變純：只剩租戶級的設定（部門、角色、系統設定、公告管理、密碼策略）

---

## 4. `getMyMenus` 重寫（Phase B）

```java
@Transactional(readOnly = true)
public List<UserMenuDto> getMyMenus(List<String> roleIds, String tenantId) {
    boolean isSuperAdmin = roleIds.contains("ROLE_SUPER_ADMIN");

    List<MenuEntity> platformMenus = isSuperAdmin
        ? menuRepository.findByScopeAndVisibleTrue("PLATFORM")
        : List.of();

    List<String> permissionCodes = isSuperAdmin
        ? List.of()  // 在 platform mode 不需要租戶權限
        : permissionRepository.findCodesByRoleIdsAndTenant(roleIds, tenantId);

    List<MenuEntity> tenantMenus = permissionCodes.isEmpty()
        ? List.of()
        : menuRepository.findByPermissionCodeInAndVisibleTrueAndScope(permissionCodes, "TENANT");

    List<MenuEntity> publicMenus = menuRepository.findByScopeAndVisibleTrue("PUBLIC");

    List<MenuEntity> result = new ArrayList<>();
    result.addAll(platformMenus);
    result.addAll(tenantMenus);
    result.addAll(publicMenus);

    // 補上有可見子節點的 DIRECTORY 父節點（同現有邏輯）
    appendParentDirectories(result);

    return buildUserMenuTree(result);
}
```

- SUPER_ADMIN 在 **非 impersonation** 模式下：只看到 PLATFORM + PUBLIC
- SUPER_ADMIN 在 **impersonation** 模式下：身分視為 TENANT_ADMIN @ 目標租戶 → 走一般使用者路徑，看到 TENANT + PUBLIC（不含 PLATFORM）
- 一般使用者：TENANT + PUBLIC（同現況）

---

## 5. DB Migration 規劃（建議在 Phase A 就上）

新增一支 migration（例：`V53__rbac__menu_scope.sql`）：

```sql
-- 1. 新增 scope 欄位
ALTER TABLE menus ADD COLUMN scope VARCHAR(20) NOT NULL DEFAULT 'TENANT';
ALTER TABLE menus ADD CONSTRAINT menus_scope_check
  CHECK (scope IN ('PLATFORM', 'TENANT', 'PUBLIC'));

-- 2. 重新分類既有菜單
UPDATE menus SET scope = 'PLATFORM' WHERE menu_id IN (11, 36, 37);
UPDATE menus SET scope = 'PUBLIC' WHERE menu_id = 34;
-- 其餘維持預設 TENANT

-- 3. 新增「平台管理」目錄
INSERT INTO menus (menu_id, parent_id, name, menu_type, route_path, icon, sort_order, visible, scope)
OVERRIDING SYSTEM VALUE VALUES
(100, NULL, '平台管理', 'DIRECTORY', '/platform', 'Setting', 5, true, 'PLATFORM')
ON CONFLICT (menu_id) DO NOTHING;

-- 4. 把現有 PLATFORM 葉節點搬到「平台管理」下
UPDATE menus SET parent_id = 100 WHERE menu_id IN (11, 36, 37);

-- 5. 新增「租戶管理」菜單（取代前端硬編碼）
INSERT INTO menus (menu_id, parent_id, name, menu_type, route_name, route_path, component, permission_code, icon, sort_order, visible, scope)
OVERRIDING SYSTEM VALUE VALUES
(101, 100, '租戶管理', 'PAGE', 'TenantManage', '/admin/system/tenants',
 'views/admin/tenant/TenantManageView.vue', 'PLATFORM_TENANT_MANAGE',
 'OfficeBuilding', 10, true, 'PLATFORM')
ON CONFLICT (menu_id) DO NOTHING;

SELECT setval('menus_menu_id_seq', GREATEST((SELECT MAX(menu_id) FROM menus), 101));
```

> 對應 Phase A 也要：
> - `MenuEntity` 加 `scope` 欄位
> - `MenuRepository` 加 `findByScopeAndVisibleTrue(String scope)`
> - 菜單管理頁加 scope 下拉
> - 前端 [AppSidebar.vue](frontend/src/components/AppSidebar.vue) 移除 isSuperAdmin 的硬編碼「租戶管理」項（改由後端菜單回傳）

---

## 6. Phase 拆分

**Phase A 加碼項目（不影響 Step 2 既定範圍）**

- DB migration：新增 `scope` 欄位、重分類、新增「平台管理」目錄、新增「租戶管理」菜單 seed
- `MenuEntity` / `MenuRepository` / `MenuDto` 加 `scope`
- 菜單管理 UI 加 scope 欄位
- 前端 `AppSidebar` 移除硬編碼租戶管理項
- **`getMyMenus` 邏輯不變**（SUPER_ADMIN 仍回傳所有 visible 菜單；只是現在「所有 visible 菜單」已經包含 scope 資訊）

**Phase B**

- 重寫 `getMyMenus` 為 § 4 的版本（平台菜單 ∪ 當前租戶菜單）
- 與 impersonation 一起上線

---

## 7. 待決問題

1. ✅ **Q1：scope 標記方式** — 採 (A) 新增 `scope` 欄位（2026-05-29）。
2. ✅ **Q2：菜單樹結構變更** — 採 (a)（2026-05-29）：新增「平台管理 (100)」目錄收編 11/36/37；「租戶管理 (101)」改成 DB seed，前端 [AppSidebar.vue](frontend/src/components/AppSidebar.vue) 硬編碼移除。
3. ✅ **Q3：menu_id=37 路由改名** — 採 (a)（2026-05-29）：路由從 `/admin/security/auth-config` 改為 `/platform/auth-config`，與其他 PLATFORM 菜單路由對齊。
4. ✅ **Q4：Phase 拆分** — 採 (a)（2026-05-29）：DB schema / seed / UI / 前端硬編碼清理放 Phase A；`getMyMenus` 重寫、`TenantFilterAspect` 收緊、impersonation 機制、`audit_log` schema 變動統一留 Phase B。

---

## 8. Step 3 確認完成總結

本文件四個決議均已確認，結合 Step 2 結論，Phase A 完整增刪清單見 [README.md](./README.md) 「Phase A 執行清單」段落。
