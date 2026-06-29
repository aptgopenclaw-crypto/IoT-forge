# RBAC 模組 Code Review

> 審查日期：2026-05-19（第二次審查）
> 修正日期：2026-05-19（全部 15 個問題已修正）
> 審查範圍：`backend/src/main/java/com/taipei/iot/rbac/` 全部 23 個檔案 + 3 個測試檔 + 3 個 migration SQL + `frontend/src/api/rbac/` + `frontend/src/types/rbac.ts`

---

## 模組結構總覽

### Backend

```
rbac/
├── controller/
│   ├── MenuController.java          # 選單 CRUD + 樹狀結構 + 使用者選單
│   ├── PermissionController.java    # 權限清單查詢
│   └── RoleController.java          # 角色 CRUD + 權限指派 + 可指派角色
├── dto/
│   ├── request/
│   │   ├── AssignRolePermissionsRequest.java
│   │   ├── CreateMenuRequest.java
│   │   ├── CreateRoleRequest.java
│   │   ├── UpdateMenuRequest.java
│   │   └── UpdateRoleRequest.java
│   └── response/
│       ├── MenuDto.java             # 管理端選單（含 permissionCode）
│       ├── PermissionDto.java
│       ├── RoleDto.java
│       ├── RolePermissionListDto.java
│       └── UserMenuDto.java         # 使用者端選單（不含 permissionCode）
├── entity/
│   ├── MenuEntity.java              # 全域選單（無 tenant_id）
│   ├── PermissionEntity.java        # 全域權限定義
│   ├── RolePermissionEntity.java    # 角色-權限關聯（含 tenant_id 預留）
│   └── RolePermissionId.java        # 複合主鍵
├── repository/
│   ├── MenuRepository.java
│   ├── PermissionRepository.java
│   └── RolePermissionRepository.java
└── service/
    ├── MenuService.java             # 選單樹、使用者選單、Menu CRUD
    ├── PermissionService.java       # 權限清單、依角色查權限
    └── RoleService.java             # 角色 CRUD、權限指派、可指派角色過濾
```

### Frontend

```
frontend/src/
├── api/rbac/index.ts                  # API 呼叫封裝
├── types/rbac.ts                      # TypeScript 介面定義
├── views/admin/role/
│   └── RolePermissionView.vue         # 角色權限管理頁面
├── router/index.ts                    # 路由設定
└── locales/zh-TW.ts, zh-CN.ts, en.ts # i18n 字串
```

**跨模組相依**：
- `RoleEntity` / `RoleRepository` 在 `auth` 模組中定義，由 `rbac.RoleService` 共用
- `DataScopeEnum` / `SecurityContextUtils` 在 `dept` / `common` 模組
- 權限查詢 (`role_permissions`) 被 `AuthService.resolvePermissions()` 和 `MenuService.getMyMenus()` 兩邊使用

---

## 總體評價

模組分層清晰、DTO/Entity 分離乾淨、Repository 查詢方法命名遵循 Spring Data JPA 慣例。選單樹建構邏輯和權限過濾流程是整個模組的核心，實作正確且測試覆蓋良好。`RolePermissionEntity` 預留了 `tenant_id` 欄位支援未來的租戶級權限複寫，設計有前瞻性。

**主要改進空間**：~~RoleController 寫入操作缺少權限保護（嚴重）、`builtIn` 角色保護僅限 SUPER_ADMIN、`menuType` 缺少白名單驗證、`getMyMenus` 存在效能瓶頸。~~ （已全部修正）

---

## 優點

### 1. 選單樹建構邏輯清晰 (`MenuService.java`)

`buildMenuTree` 和 `buildUserMenuTree` 採用 `groupingBy(parentId)` + 遞迴組裝的方式，一次查詢全部選單後在記憶體中建樹。對於選單這類資料量小（通常 < 200 筆）的場景，這比 N+1 查詢逐層載入高效得多。`UserMenuDto` 刻意排除 `permissionCode` 欄位，避免將內部權限碼洩漏給前端一般使用者。

### 2. SUPER_ADMIN 選單全量繞過 (`MenuService.java`)

```java
if (roleIds.stream().anyMatch(r -> r.equals("ROLE_SUPER_ADMIN"))) {
    List<MenuEntity> allVisible = menuRepository.findAllByOrderBySortOrder().stream()
            .filter(m -> Boolean.TRUE.equals(m.getVisible()))
            .collect(Collectors.toList());
    return buildUserMenuTree(allVisible);
}
```

超級管理員直接看到所有可見選單，不需要透過 `role_permissions` 查詢。這與 `AuthServiceImpl` 中的 SUPER_ADMIN 權限處理邏輯一致。

### 3. Tenant-scoped 權限查詢 (`RolePermissionRepository.java`)

```java
@Query("SELECT rp FROM RolePermissionEntity rp WHERE rp.roleId IN :roleIds AND (rp.tenantId IS NULL OR rp.tenantId = :tenantId)")
List<RolePermissionEntity> findByRoleIdInAndTenantScope(...);
```

`(rp.tenantId IS NULL OR rp.tenantId = :tenantId)` 的設計同時支援全域權限定義 (`NULL`) 和未來租戶級權限複寫。

### 4. DataScope 驅動的可指派角色過濾 (`RoleService.java`)

`listAssignableRoles()` 根據目前使用者的 DataScope 決定可以指派哪些角色：ALL scope 的管理員可以指派所有角色、非 ALL scope 的管理員無法指派 scope=ALL 的角色（防止權限提升攻擊）。

### 5. 選單刪除有子節點保護 (`MenuService.java`)

```java
if (menuRepository.existsByParentId(menuId)) {
    throw new BusinessException(ErrorCode.MENU_HAS_CHILDREN);
}
```

防止孤兒節點產生。

### 6. `getMyMenus()` 自動補父層目錄 (`MenuService.java`)

即使使用者沒有 DIRECTORY 類型選單的對應權限，只要該目錄下有可見的子頁面，父層目錄就會被自動加入。避免前端樹狀選單出現「孤兒頁面」。

### 7. 選單 CRUD 多層權限保護

`SecurityConfig`: 所有 POST/PUT/DELETE/PATCH `/v1/auth/menus/**` 限制 `hasRole("SUPER_ADMIN")`，搭配 MenuController 上的 `@AuditEvent` 做審計記錄。

### 8. SUPER_ADMIN 角色不可修改

`updateRole()`、`toggleEnabled()`、`assignPermissions()` 三個方法都檢查 `SUPER_ADMIN_ROLE_ID`，防止修改最高權限角色。

### 9. 前端 API 使用 `encodeURIComponent`

```typescript
export const updateRole = (roleId: string, payload: UpdateRoleRequest) =>
  axiosIns.put<...>(`/auth/roles/${encodeURIComponent(roleId)}`, payload)
```

正確處理 URL 中的特殊字元。

### 10. 測試覆蓋良好

| 測試類別 | 覆蓋場景 |
|----------|----------|
| `MenuControllerTest` | SUPER_ADMIN 建立選單 200、ADMIN 被拒 403、一般使用者取自己的選單 |
| `MenuServiceTest` | 選單樹建構、admin/viewer 角色選單過濾、建立/刪除/切換可見性、子節點保護、找不到報錯 |
| `RoleServiceTest` | 列出角色、建立/更新/切換啟用、權限指派取代邏輯、重複代碼報錯、找不到角色 |

---

## 需要改進的問題

> ✅ 以下所有問題已於 2026-05-19 修正完成。

### 1. ✅ [嚴重] `RoleController` 寫入操作缺少權限保護 — 任何已認證使用者可建立角色/指派權限

**修正方式**: `RoleController` 加入 `@PreAuthorize`：`createRole` 限 SUPER_ADMIN，`updateRole`/`toggleEnabled`/`assignPermissions` 限 ADMIN + SUPER_ADMIN。

### 2. ✅ [高] `assignPermissions()` 未限制呼叫者不能指派超越自己的權限

**修正方式**: `assignPermissions()` 加入呼叫者權限 superset 檢查。非 SUPER_ADMIN 呼叫者只能指派自己已擁有的權限。

### 3. ✅ [高] `CreateMenuRequest` / `UpdateMenuRequest` 缺少 `menuType` 白名單驗證

**修正方式**: 兩個 DTO 的 `menuType` 欄位加入 `@Pattern(regexp = "DIRECTORY|PAGE|BUTTON")`。

### 4. ✅ [中等] `updateRole()` / `toggleEnabled()` 僅保護 SUPER_ADMIN，未保護其他 `builtIn` 角色

**修正方式**: 改用 `Boolean.TRUE.equals(entity.getBuiltIn())` 檢查，保護所有內建角色。

### 5. ✅ [中等] `RolePermissionId` 複合主鍵不含 `tenantId` — 設計歧義

**修正方式**: 保留目前 PK 設計 `(roleId, permissionId)`，在 `RolePermissionId` 加入完整 Javadoc 說明設計決策與未來擴展方向。

### 6. ✅ [中等] `deleteByRoleId` 缺少 `@Modifying` + `@Query` — 效能問題

**修正方式**: 改為 `@Modifying @Query("DELETE FROM RolePermissionEntity rp WHERE rp.roleId = :roleId")`，單次 bulk DELETE。

### 7. ✅ [中等] `getMyMenus()` 每次呼叫做兩次全表掃描

**修正方式**: `MenuService` 加入 `volatile List<MenuEntity> allMenusCache` 記憶體快取，`createMenu`/`updateMenu`/`deleteMenu`/`toggleVisible` 後自動 `invalidateMenuCache()`。

### 8. ✅ [中等] `assignPermissions()` 靜默忽略無效的 permission ID

**修正方式**: 寫入前以 `permissionRepository.findAllById()` 驗證所有 ID 存在，無效 ID 拋出 `VALIDATION_ERROR`。

### 9. ✅ [中等] `updateMenu` 缺少 `@AuditEvent`

**修正方式**: 新增 `AuditEventType.UPDATE_MENU`，標註在 `MenuController.updateMenu()` 上。

### 10. ✅ [中等] RoleController 寫入操作缺少審計事件

**修正方式**: 新增 `CREATE_ROLE`、`UPDATE_ROLE`、`TOGGLE_ROLE_ENABLED`、`ASSIGN_ROLE_PERMISSIONS` 四個事件類型，各方法加入 `@AuditEvent`。

### 11. ✅ [低] `isRoleAssignable()` 效能不佳

**修正方式**: 改為直接 `findById` 單筆查詢 + DataScope 判斷，不再載入全部角色。

### 12. ✅ [低] `createRole()` 的 roleId 碰撞風險

**修正方式**: 改用完整 32 字元 UUID（移除 `.substring(0, 12)`）。

### 13. ✅ [低] `CreateRoleRequest.dataScope` 缺少白名單驗證

**修正方式**: `CreateRoleRequest` 與 `UpdateRoleRequest` 都加入 `@Pattern(regexp = "ALL|THIS_LEVEL|THIS_LEVEL_AND_BELOW")`。

### 14. ✅ [低] `getRolePermissions()` 兩個多載方法有重複程式碼

**修正方式**: 無 tenant 版本委派給有 tenant 版本：`getRolePermissions(roleId)` → `getRolePermissions(roleId, null)`，內部依 `tenantId != null` 分流查詢。

### 15. ✅ [低] `listRoles()` 過濾 SUPER_ADMIN 但 `listAssignableRoles()` 沒有

**修正方式**: 加入 `.filter(role -> !SUPER_ADMIN_ROLE_ID.equals(role.getRoleId()))`。

---

## 安全性總結

| 面向 | 狀態 | 說明 |
|------|------|------|
| 選單 CRUD 權限 | ✅ 良好 | SecurityConfig + SUPER_ADMIN 限定 |
| 選單讀取權限 | ✅ 良好 | `/my` 需 authenticated、`/tree` 需 ADMIN+ |
| 角色讀取權限 | ✅ 良好 | GET 限 ADMIN/SUPER_ADMIN/DEPT_ADMIN |
| 角色寫入權限 | ✅ 已修正 | `@PreAuthorize` 限制 + SecurityConfig 規則 |
| 權限指派控制 | ✅ 已修正 | 呼叫者權限 superset 驗證 + permission ID 存在性驗證 |
| builtIn 角色保護 | ✅ 已修正 | 所有 builtIn 角色皆不可修改 |
| 輸入驗證 | ✅ 已修正 | menuType + dataScope 都有 `@Pattern` 白名單 |
| 審計追蹤 | ✅ 已修正 | Role 4 個寫入操作 + Menu update 都有 `@AuditEvent` |

---

## 建議修復優先順序

> ✅ 全部已修正完成。

| 優先級 | 項目 | 問題編號 | 狀態 |
|--------|------|----------|------|
| **P0** | RoleController 加入權限保護 | #1 | ✅ 已修正 |
| **P1** | assignPermissions 加入呼叫者權限檢查 | #2 | ✅ 已修正 |
| **P1** | menuType 加白名單驗證 | #3 | ✅ 已修正 |
| **P1** | builtIn 角色全面保護 | #4 | ✅ 已修正 |
| **P2** | RolePermissionId 加註解說明設計 | #5 | ✅ 已修正 |
| **P2** | deleteByRoleId 改用 bulk delete | #6 | ✅ 已修正 |
| **P2** | getMyMenus 加入快取 | #7 | ✅ 已修正 |
| **P2** | assignPermissions 驗證 permission ID | #8 | ✅ 已修正 |
| **P2** | updateMenu / Role 操作加入審計 | #9, #10 | ✅ 已修正 |
| **P3** | isRoleAssignable 效能優化 | #11 | ✅ 已修正 |
| **P3** | createRole UUID 長度 | #12 | ✅ 已修正 |
| **P3** | dataScope 白名單驗證 | #13 | ✅ 已修正 |
| **P4** | getRolePermissions 消除重複 | #14 | ✅ 已修正 |
| **P4** | listAssignableRoles 過濾 SUPER_ADMIN | #15 | ✅ 已修正 |

---

## 架構總評（修正後）

| 維度 | 評分 | 說明 |
|------|------|------|
| 正確性 | **9/10** | 選單樹建構、權限過濾、SUPER_ADMIN 繞過邏輯正確。builtIn 全面保護、menuType/dataScope 白名單驗證完善。 |
| 安全性 | **9/10** | RoleController `@PreAuthorize` + 呼叫者權限 superset 檢查 + permission ID 驗證 + 全面審計追蹤。 |
| 可維護性 | **8.5/10** | 分層清晰、DTO 命名一致、Repository 方法遵循 Spring Data 慣例。重複程式碼已重構、設計決策有文檔。 |
| 效能 | **8.5/10** | 選單全域快取、bulk delete、isRoleAssignable 單筆查詢。整體效能瓶頸已消除。 |