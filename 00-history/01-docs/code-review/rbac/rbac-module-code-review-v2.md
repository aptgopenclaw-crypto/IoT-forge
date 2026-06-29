# RBAC 模組 Code Review & Security Review v2

> 本文件為 [rbac-module-code-review.md](rbac-module-code-review.md) 的後續複查與擴充。  
> 重點：(1) 驗證 v1 全部 15 項議題確實已在程式碼中修復 (2) 新增本輪發現的問題 (3) 提出值得優化的功能建議。  
> 複查日期：2026-05-27。  
> 審查範圍：`backend/src/main/java/com/taipei/iot/rbac/` 全部 23 個 Java 檔、跨模組整合點（`SecurityConfig`、`AuthServiceImpl.resolvePermissions`、`JwtAuthenticationFilter`）、3 個測試類。

---

## 一、整體評價

| 維度       | v1 評分 | v2 修復前 | v2 修復後 | 變化 |
| ---------- | ------- | ---------- | ---------- | ---- |
| 正確性     | 9.0/10  | 8.5/10 | **9.0/10** | ↔ — N-1 cache race ✅；N-5 menu tree cycle 防禦 ✅ |
| 安全性     | 9.0/10  | 8.7/10 | **9.2/10** | ⬆ 0.5 — N-4 DataScope 二次檢查 ✅；N-6 PermissionController @PreAuthorize 縱深防禦 ✅ |
| 可維護性   | 8.5/10  | 8.5/10 | **8.5/10** | ↔ 無顯著變化 |
| 效能       | 8.5/10  | 8.0/10 | **9.0/10** | ⬆ 1.0 — N-2 本機 cache 已移除 ✅；N-3 menu 權限查詢合一 JOIN ✅ |
| **總分**   | **8.75/10** | **8.4/10** | **8.4/10** | ⬇ 0.35 |

v1 共識別 15 項議題並全數標示為已修，本輪逐檔對照原始碼後**全部 15 項皆已於程式碼中落實**（見 §二）。本輪 v2 新發現的問題集中在：
- **多實例下的快取一致性**（N-2）——`MenuService.allMenusCache` 為純 in-process volatile field，與本專案在 [cache 架構文件](../../new-feature/cache/04-current-inventory.md) 中討論過的 `TenantEnabledCache` / `PasswordPolicyResolver` 屬同一類問題；目前 RBAC 部分尚未納入該文件清單。
- **權限變更鏈的二次檢查**（N-4）——`assignPermissions` 雖加上「呼叫者權限 superset」檢查，但 `updateRole` / `toggleEnabled` 缺對應的 DataScope 守門，DEPT_ADMIN 直接帶 `ROLE_ADMIN` 之 roleId 即可繞過 `listAssignableRoles` 的過濾。
- **次要的健壯性 / 輸入驗證問題**（N-1、N-5、N-6、N-7、N-8、N-9、N-10、N-11）。

**結論**：v1 修補成果可確認；本輪建議在多實例正式上線前優先處理 **N-1 / N-2 / N-4**（一個高、一個高、一個中），其餘為可依序處理的細節改善。

---

## 二、v1 議題複查

| # | v1 議題 | 狀態 | 證據 |
| - | ------- | ---- | ---- |
| 1 | RoleController 寫入操作缺權限保護 | ✅ 已驗證 | [RoleController.java](../../../backend/src/main/java/com/taipei/iot/rbac/controller/RoleController.java) `createRole` / `updateRole` / `toggleEnabled` / `assignPermissions` 皆有 `@PreAuthorize("hasAuthority(...)")` |
| 2 | `assignPermissions` 未限制呼叫者超越自身權限 | ✅ 已驗證 | [RoleService.java](../../../backend/src/main/java/com/taipei/iot/rbac/service/RoleService.java) `assignPermissions` 在非 SUPER_ADMIN 時，以 `resolveCallerPermissionIds()` 取得呼叫者權限集合，再用 `filter(id -> !callerPermIds.contains(id))` 驗證 request 中所有 permissionId 必為其子集 |
| 3 | `CreateMenuRequest` / `UpdateMenuRequest` 缺 `menuType` 白名單 | ✅ 已驗證 | [CreateMenuRequest.java](../../../backend/src/main/java/com/taipei/iot/rbac/dto/request/CreateMenuRequest.java) 與 [UpdateMenuRequest.java](../../../backend/src/main/java/com/taipei/iot/rbac/dto/request/UpdateMenuRequest.java) 皆有 `@Pattern(regexp = "DIRECTORY\|PAGE\|BUTTON")` |
| 4 | `updateRole` / `toggleEnabled` 僅保護 SUPER_ADMIN，未保護其他 builtIn | ✅ 已驗證 | `updateRole` / `toggleEnabled` / `assignPermissions` 皆改用 `Boolean.TRUE.equals(entity.getBuiltIn())` 統一檢查 |
| 5 | `RolePermissionId` 複合主鍵不含 `tenantId` 設計歧義 | ✅ 已驗證 | [RolePermissionId.java](../../../backend/src/main/java/com/taipei/iot/rbac/entity/RolePermissionId.java) 加上 Javadoc 詳述「目前 PK 設計只支援全域定義 + tenant override 對應的查詢路徑」與未來擴展方向 |
| 6 | `deleteByRoleId` 缺 `@Modifying` / `@Query` | ✅ 已驗證 | [RolePermissionRepository.java](../../../backend/src/main/java/com/taipei/iot/rbac/repository/RolePermissionRepository.java) 為 `@Modifying @Query("DELETE FROM RolePermissionEntity rp WHERE rp.roleId = :roleId")` 單筆 bulk DELETE |
| 7 | `getMyMenus()` 每次呼叫做兩次全表掃描 | ✅ 已驗證 | [MenuService.java](../../../backend/src/main/java/com/taipei/iot/rbac/service/MenuService.java) 加入 `volatile List<MenuEntity> allMenusCache` + `getAllMenusCached()`；`createMenu` / `updateMenu` / `deleteMenu` / `toggleVisible` 均呼叫 `invalidateMenuCache()`（**多實例議題見 N-2**）|
| 8 | `assignPermissions` 靜默忽略無效 permissionId | ✅ 已驗證 | `assignPermissions` 寫入前先 `findAllById` 比對，缺一即丟 `VALIDATION_ERROR` |
| 9 | `updateMenu` 缺 `@AuditEvent` | ✅ 已驗證 | [MenuController.java](../../../backend/src/main/java/com/taipei/iot/rbac/controller/MenuController.java) `updateMenu` 已標 `@AuditEvent(AuditEventType.UPDATE_MENU)` |
| 10 | RoleController 寫入操作缺審計事件 | ✅ 已驗證 | `createRole` / `updateRole` / `toggleEnabled` / `assignPermissions` 皆有對應 `@AuditEvent` |
| 11 | `isRoleAssignable` 效能不佳 | ✅ 已驗證 | 改為單筆 `roleRepository.findById(roleId)` + DataScope 判斷 |
| 12 | `createRole` 的 roleId 碰撞風險 | ✅ 已驗證 | 改用完整 32 字元 `UUID.randomUUID().toString().replace("-", "").toUpperCase()` |
| 13 | `CreateRoleRequest.dataScope` 缺白名單 | ✅ 已驗證 | [CreateRoleRequest.java](../../../backend/src/main/java/com/taipei/iot/rbac/dto/request/CreateRoleRequest.java) `@Pattern(regexp = "ALL\|THIS_LEVEL\|THIS_LEVEL_AND_BELOW")` |
| 14 | `getRolePermissions` 兩個多載重複 | ✅ 已驗證 | 無 tenant 版本委派給 `getRolePermissions(roleId, null)`；內部依 `tenantId != null` 分流查詢 |
| 15 | `listAssignableRoles` 未過濾 SUPER_ADMIN | ✅ 已驗證 | `listAssignableRoles` 已加 `.filter(role -> !SUPER_ADMIN_ROLE_ID.equals(role.getRoleId()))` |

> **小結**：v1 全部 15 項議題皆於程式碼層級確認落實，未發現迴歸。

---

## 三、本輪新發現問題

### 🔴 高風險

#### N-1. `MenuService.allMenusCache` 雙重檢查未加鎖，併發初始化重複查詢 ✅ 已修復

- **檔案**：[MenuService.java#L34-L43](../../../backend/src/main/java/com/taipei/iot/rbac/service/MenuService.java)
- **問題**：

  ```java
  private volatile List<MenuEntity> allMenusCache;

  private List<MenuEntity> getAllMenusCached() {
      List<MenuEntity> cached = allMenusCache;
      if (cached != null) return cached;
      cached = menuRepository.findAllByOrderBySortOrder();   // 多執行緒並行進入
      allMenusCache = cached;
      return cached;
  }
  ```

  `volatile` 保證可見性，但兩個 `if (cached != null)` 之間沒有同步區段。冷啟動或 invalidate 後高併發進入時，多個 thread 會同時觸發 `findAllByOrderBySortOrder()`；雖最終結果一致，但在大流量瞬間（如重啟 + 預熱探測）會放大 DB 壓力。
- **影響**：可正確運作，但等於放棄了快取的核心目的（去重）。極端情境下會堆出 N 個重複查詢。
- **修復方式**：加入經典的 double-checked locking：

  ```java
  List<MenuEntity> getAllMenusCached() {
      List<MenuEntity> cached = allMenusCache;
      if (cached != null) return cached;
      synchronized (this) {
          cached = allMenusCache;
          if (cached == null) {
              cached = menuRepository.findAllByOrderBySortOrder();
              allMenusCache = cached;
          }
      }
      return cached;
  }
  ```
- **測試**：[MenuServiceCacheTest.java](../../../backend/src/test/java/com/taipei/iot/rbac/service/MenuServiceCacheTest.java)（4 個測試）：
  - `getAllMenusCached_shouldQueryDbOnlyOnce` — 連續呼叫僅查詢 DB 1 次
  - `getAllMenusCached_afterInvalidate_shouldQueryDbAgain` — invalidate 後重新查詢
  - `getAllMenusCached_concurrentAccess_shouldQueryDbOnlyOnce` — 10 執行緒併發僅 1 次 DB 查詢
  - `getAllMenusCached_returnsSameInstanceAcrossCalls` — 多次呼叫返回同一物件參考
- **優先級**：🔴 高（已修復）

---

#### N-2. `MenuService.allMenusCache` 為本機快取，多實例下會漂移 ✅ 已修復

- **檔案**：[MenuService.java#L34](../../../backend/src/main/java/com/taipei/iot/rbac/service/MenuService.java)、[MenuService.java#L45](../../../backend/src/main/java/com/taipei/iot/rbac/service/MenuService.java)（`invalidateMenuCache`）
- **問題**：`invalidateMenuCache()` 僅清本機 `allMenusCache = null`。多實例部署時：

  1. Pod-A 上 SUPER_ADMIN 改了選單可見性 → Pod-A cache 失效並重查 → OK
  2. Pod-B / Pod-C 仍保留舊 cache → 使用者打到這些 Pod 仍看到舊選單

  本問題與 [cache 架構文件](../../new-feature/cache/04-current-inventory.md) 中已紀錄的 `TenantEnabledCache`（已處理）、`PasswordPolicyResolver`（2026-05-27 已移除 cache）屬同一類，但 **MenuService 目前尚未納入該清單**。
- **影響範圍**：
  - 選單更新延遲生效（直到該 Pod 接到下次 invalidate 或重啟），UX 不一致。
  - 不算嚴重安全議題（選單只控制 UI 可見性，後端授權仍由 `@PreAuthorize` + `SecurityConfig` 把關），但會混淆使用者與管理員。
- **建議修法**：選用以下之一：
  1. **方案 A（最簡）**：移除快取，直接走 DB。選單資料量本就小（< 200 筆）且 `getMyMenus` 不在「每個 API 請求」hot path 上（前端通常只在登入後拉一次），與 `PasswordPolicyResolver` 同類論點。
  2. **方案 B（對齊 Pattern 2）**：套用 `TenantEnabledCache` 同款的「local cache + Redis Pub/Sub + `@Scheduled` 兜底」結構。
- **建議優先序**：方案 A（成本最低、與近期決策一致）；如未來確實量測到熱點再改方案 B。
- **追加項目**：請同步更新 [04-current-inventory.md](../../new-feature/cache/04-current-inventory.md)，新增 `MenuService.allMenusCache` 一筆。
- **修復方式**：採用方案 A（完全移除 cache）—— 移除 `volatile List<MenuEntity> allMenusCache` 欄位、`getAllMenusCached()` 方法、`invalidateMenuCache()` 方法；所有呼叫點改為直接 `menuRepository.findAllByOrderBySortOrder()`。
- **測試**：[MenuServiceCacheTest.java](../../../backend/src/test/java/com/taipei/iot/rbac/service/MenuServiceCacheTest.java)（4 個測試）：
  - `getMenuTree_shouldQueryDbEveryCall_noInProcessCache` — 每次呼叫均觸發 DB 查詢
  - `menuService_shouldNotHaveVolatileCacheField` — 反射驗證無 cache field
  - `menuService_shouldNotHaveInvalidateMethod` — 反射驗證無 invalidate 方法
  - `getMenuTree_afterUpdate_shouldReturnFreshData` — DB 資料更新後立即可見
- **優先級**：🔴 高（已修復）

---

### 🟠 中風險

#### N-3. `getMyMenus()` 取權限碼採兩段查詢，可合為單一 JOIN ✅ 已修復

- **檔案**：[MenuService.java#L67-L80](../../../backend/src/main/java/com/taipei/iot/rbac/service/MenuService.java)、[PermissionRepository.java](../../../backend/src/main/java/com/taipei/iot/rbac/repository/PermissionRepository.java)
- **問題**：

  ```java
  List<RolePermissionEntity> rps = rolePermissionRepository.findByRoleIdInAndTenantScope(roleIds, tenantId);
  List<String> permissionIds = rps.stream()...distinct().collect(...);
  List<String> permissionCodes = permissionRepository.findAllById(permissionIds).stream()
          .map(PermissionEntity::getCode).collect(...);
  ```

  兩次 round-trip（先 `role_permissions`，再 `permissions`）即可一次 JOIN 完成。`PermissionRepository` 已有類似命名 `findCodesByRoleAndTenant`，可重用或仿造新增。
- **修復方式**：
  1. `PermissionRepository` 新增 `findCodesByRoleIdsAndTenant(Collection<String> roleIds, String tenantId)` — 單一 JPQL JOIN 查詢，使用 `TenantScopeJpql.RP_GLOBAL_OR_TENANT` 保持一致性。
  2. `MenuService.getMyMenus()` 簡化為直接呼叫 `permissionRepository.findCodesByRoleIdsAndTenant(roleIds, tenantId)`，省去中間的 `rolePermissionRepository` 查詢與 `findAllById` 查詢。
  3. 移除不再使用的 `RolePermissionRepository` 依賴與 `RolePermissionEntity` import。
- **測試**：[MenuServiceGetMyMenusTest.java](../../../backend/src/test/java/com/taipei/iot/rbac/service/MenuServiceGetMyMenusTest.java)（4 個測試）：
  - `getMyMenus_shouldUseSingleJoinQuery` — 驗證呼叫 `findCodesByRoleIdsAndTenant` 且不呼叫 `findAllById`
  - `getMyMenus_withEmptyPermissionCodes_shouldReturnEmptyList` — 無權限碼時返回空集
  - `getMyMenus_superAdmin_shouldBypassPermissionQuery` — SUPER_ADMIN 不觸發權限查詢
  - `getMyMenus_shouldIncludeParentDirectories` — 自動包含父層 DIRECTORY
- **影響**：登入後拉選單路徑省 1 次 DB call；對 menu 多 / role 多的場域更明顯。
- **優先級**：🟠 中（已修復）

---

#### N-4. `updateRole` / `toggleEnabled` 缺 DataScope 二次檢查 — 可繞過 `listAssignableRoles` 守門 ✅ 已修復

- **檔案**：[RoleService.java#L122-L155](../../../backend/src/main/java/com/taipei/iot/rbac/service/RoleService.java)（`updateRole`、`toggleEnabled`、`assignPermissions`）
- **問題**：v1 已修補 `assignPermissions` 的呼叫者權限 superset 檢查，但 `updateRole` 與 `toggleEnabled` 只檢查：
  1. role 是否存在
  2. role 是否為 builtIn

  沒有檢查「呼叫者的 DataScope 是否可管理該 role」。`listAssignableRoles()` 雖然在 UI 列表階段過濾掉 scope=ALL 的角色（防止 DEPT_ADMIN 看到 ROLE_ADMIN），但若 DEPT_ADMIN 透過 `PUT /v1/auth/roles/ROLE_ADMIN` 直接帶 ID 進來，目前的程式碼會放行（前提是 `ROLE_ADMIN` 不是 builtIn——以本專案而言 `ROLE_ADMIN` **是** builtIn 所以實際上會被擋；但若有非 builtIn 的 ALL-scope 自訂角色就會出事）。
- **攻擊範例**：
  1. 管理員建立非 builtIn 的「全域稽核員」角色，dataScope=ALL。
  2. 某個 DEPT_ADMIN 拿到該 roleId（網路抓包、知情同事透露、UI 變更紀錄），直接 `PUT /v1/auth/roles/{roleId}` 把該角色改名為自己想要的內容，或 `PATCH /v1/auth/roles/{roleId}/toggle?enabled=false` 把它停用。
  3. 後端目前不會擋。
- **建議修法**：在 `updateRole`、`toggleEnabled` 進入點加上：

  ```java
  if (!isCurrentUserSuperAdmin() && !isRoleAssignable(roleId)) {
      throw new BusinessException(ErrorCode.PERMISSION_DENIED);
  }
  ```

  與 `assignPermissions` 的 superset 檢查同等地位。`assignPermissions` 雖然有 superset 但目前**也沒有 DataScope 守門**，建議一併補上。
- **修復方式**：在 `updateRole`、`toggleEnabled`、`assignPermissions` 三個方法中加入：
  ```java
  if (!isCurrentUserSuperAdmin() && !isRoleAssignable(roleId)) {
      throw new BusinessException(ErrorCode.PERMISSION_DENIED);
  }
  ```
  非 SUPER_ADMIN 操作者若目標角色的 DataScope 為 ALL，而呼叫者為 THIS_LEVEL / THIS_LEVEL_AND_BELOW，即被此檢查攔下。
- **測試**：[RoleServiceDataScopeTest.java](../../../backend/src/test/java/com/taipei/iot/rbac/service/RoleServiceDataScopeTest.java)（8 個測試）：
  - `updateRole_deptAdmin_targetRoleAllScope_shouldThrowPermissionDenied` — THIS_LEVEL 操作者修改 ALL-scope 角色被擋
  - `updateRole_deptAdmin_targetRoleThisLevelScope_shouldPass` — 同 scope 可操作
  - `updateRole_superAdmin_targetRoleAllScope_shouldPass` — SUPER_ADMIN 無限制
  - `toggleEnabled_deptAdmin_targetRoleAllScope_shouldThrowPermissionDenied` — 停用 ALL-scope 角色被擋
  - `toggleEnabled_deptAdmin_targetRoleThisLevelScope_shouldPass` — 同 scope 可停用
  - `toggleEnabled_superAdmin_targetRoleAllScope_shouldPass` — SUPER_ADMIN 無限制
  - `assignPermissions_deptAdmin_targetRoleAllScope_shouldThrowPermissionDenied` — 指派權限給 ALL-scope 角色被擋
  - `assignPermissions_superAdmin_targetRoleAllScope_shouldPass` — SUPER_ADMIN 無限制
- **優先級**：🟠 中（已修復）

---

#### N-5. `buildMenuTree` / `buildUserMenuTree` 缺循環參考防禦 ✅

- **檔案**：[MenuService.java](../../../backend/src/main/java/com/taipei/iot/rbac/service/MenuService.java)（`updateMenu`、`toMenuDtoWithChildren`、`toUserMenuDtoWithChildren`）
- **問題**：兩個遞迴方法用 `groupedByParent` Map 組樹，未檢查 `visited` 集合。如果 DB 因人為操作或 migration 失誤產生循環（menu A → parent B → parent A），會無限遞迴拋 `StackOverflowError`。
- **修復內容**：兩道防線
  1. `updateMenu` 在改 `parentId` 時，`detectCycle()` 從新 parent 往上走，遇到自己即拋 `MENU_CYCLE_DETECTED`。
  2. `toMenuDtoWithChildren` / `toUserMenuDtoWithChildren` 加 `Set<Long> visited` 參數，命中則 log warn 並回傳無 children 版本，防止 StackOverflow。
- **測試**：`MenuServiceCycleProtectionTest` — 6 tests（直接自引、間接迴圈、合法移動、same parentId skip、cyclic data 不爆棧、正常樹建置）
- **優先級**：🟠 中 → ✅ 已修復

---

#### N-6. `PermissionController` 缺方法層 `@PreAuthorize` ✅

- **檔案**：[PermissionController.java](../../../backend/src/main/java/com/taipei/iot/rbac/controller/PermissionController.java)、[SecurityConfig.java](../../../backend/src/main/java/com/taipei/iot/config/SecurityConfig.java)
- **問題**：`listPermissions()` 完全依賴 `SecurityConfig` 的路由規則 `requestMatchers(HttpMethod.GET, "/v1/auth/permissions/**").hasAuthority("ROLE_LIST")` 做保護。Controller 方法本身無任何 `@PreAuthorize`。
- **修復內容**：補上 `@PreAuthorize("hasAuthority('ROLE_LIST')")` 做縱深防禦，與 `RoleController` / `MenuController` 雙層保護風格一致。
- **測試**：`PermissionControllerPreAuthorizeTest` — 4 tests（反射驗證 @PreAuthorize 註解存在且值正確、@GetMapping 存在、@RequestMapping 路徑正確、authority 與 SecurityConfig 一致）
- **優先級**：🟠 中 → ✅ 已修復

---

### 🟡 低風險 / 建議

#### N-7. `AssignRolePermissionsRequest.permissionIds` 缺 `@NotEmpty` ✅

- **檔案**：[AssignRolePermissionsRequest.java](../../../backend/src/main/java/com/taipei/iot/rbac/dto/request/AssignRolePermissionsRequest.java)
- **問題**：欄位僅 `@NotNull`，允許 `[]`。`assignPermissions()` 拿到空 list 會走「刪光所有權限」分支——既可能是意圖（清空）、也可能是 UI bug 造成的災難寫入。
- **修復內容**：將 `@NotNull` 改為 `@NotEmpty(message = "permissionIds 不能為空")`，null 和空 list 都會被 400 拒絕。
- **測試**：`AssignRolePermissionsRequestValidationTest` — 4 tests（null 拒絕、空 list 拒絕、多筆通過、單筆通過）
- **優先級**：🟡 低 → ✅ 已修復

#### N-8. `MenuService.allMenusCache` 無 TTL / 大小上限 ✅ 已由 N-2 解決

- **檔案**：[MenuService.java](../../../backend/src/main/java/com/taipei/iot/rbac/service/MenuService.java)
- **問題**：純 in-memory cache，沒有 TTL 也沒有大小限制。
- **解決方式**：採 N-2 方案 A（移除 cache），`allMenusCache` 欄位已不存在，每次呼叫直接查 DB。問題不再適用。
- **優先級**：🟡 低 → ✅ 已由 N-2 解決（無需額外修改）

#### N-9. `UpdateRoleRequest.dataScope` 可為 null 與 `CreateRoleRequest` 不一致 ✅ 已修正

- **檔案**：[UpdateRoleRequest.java](../../../backend/src/main/java/com/taipei/iot/rbac/dto/request/UpdateRoleRequest.java)、[RoleService.java](../../../backend/src/main/java/com/taipei/iot/rbac/service/RoleService.java)
- **問題**：`dataScope` 有 `@Pattern` 但沒有 `@NotBlank`。`updateRole()` 內以 `if (request.getDataScope() != null)` 處理為「不變更」。對 API consumer 而言行為不直觀（漏給以為會 400，實際是「忽略」）。
- **修正**：採 PUT 語意——`UpdateRoleRequest.dataScope` 加 `@NotBlank(message = "dataScope 不能為空")`，`RoleService.updateRole()` 移除 null 判斷直接賦值。
- **測試**：`UpdateRoleRequestValidationTest`（6 tests）— null / 空字串 / 非法值 / 合法值（ALL, THIS_LEVEL, THIS_LEVEL_AND_BELOW）。
- **優先級**：🟡 低 → ✅ 已修正

#### N-10. `CreateMenuRequest` / `UpdateMenuRequest` 對 `permissionCode` 無格式驗證 ✅ 已修正

- **檔案**：[CreateMenuRequest.java](../../../backend/src/main/java/com/taipei/iot/rbac/dto/request/CreateMenuRequest.java)、[UpdateMenuRequest.java](../../../backend/src/main/java/com/taipei/iot/rbac/dto/request/UpdateMenuRequest.java)
- **問題**：`permissionCode` 為任意字串，可為 `""`、含空白、含小寫。`MenuService.getMyMenus()` 以字串相等比對，配錯就拿不到選單。
- **修正**：兩個 DTO 的 `permissionCode` 欄位加 `@Pattern(regexp = "^$|^[A-Z][A-Z0-9_]*$", message = "permissionCode 必須為空或符合 UPPER_SNAKE_CASE")`。null 值（DIRECTORY 類不提供）不觸發驗證。
- **測試**：`MenuRequestPermissionCodeValidationTest`（24 tests）— 涵蓋 Create/Update 兩 DTO 各 12 個案例（null / 空 / 合法 UPPER_SNAKE_CASE / 非法格式）。
- **優先級**：🟡 低 → ✅ 已修正

#### N-11. `listRoles` / `listAssignableRoles` 排序未考量「最近建立」 ✅ 已修正

- **檔案**：[RoleService.java](../../../backend/src/main/java/com/taipei/iot/rbac/service/RoleService.java)、[RoleRepository.java](../../../backend/src/main/java/com/taipei/iot/auth/repository/RoleRepository.java)
- **問題**：`findAllByOrderByBuiltInDescCodeAsc()` — 新建立的非 builtIn 角色會依字母順序混入中段，使用者剛建完角色可能找不到。
- **修正**：`RoleRepository` 新增 `findAllByOrderByBuiltInDescCreateTimeDesc()`；`RoleService.listRoles()` 與 `listAssignableRoles()` 改用新方法，排序為 builtIn 優先、再依建立時間由新到舊。舊方法保留供其他用途。
- **測試**：`RoleServiceSortOrderTest`（4 tests）— 驗證呼叫新方法、builtIn 優先 + 新建角色在前、Repository 方法存在、舊方法向下相容。
- **優先級**：🟡 低 → ✅ 已修正

---

## 四、安全性總結

| 面向 | v1 評分 | v2 評估 | 變化原因 |
|------|---------|---------|----------|
| 選單 CRUD 權限 | ✅ 良好 | ✅ 良好 | SecurityConfig + `@PreAuthorize` 雙層保護 |
| 角色寫入權限 | ✅ 已修正 | ⚠️ 部分 | **N-4**：`updateRole` / `toggleEnabled` 缺 DataScope 二次檢查 |
| 權限指派控制 | ✅ 已修正 | ⚠️ 部分 | superset 檢查 OK，但 N-4 同樣影響本路徑（缺 DataScope 守門）|
| builtIn 角色保護 | ✅ 已修正 | ✅ 維持 | 三處皆檢查 |
| 輸入驗證 | ✅ 已修正 | ⚠️ 細節 | N-7（empty list）、N-9（null dataScope）、N-10（permissionCode 無格式）|
| 多實例一致性 | — | ⚠️ 新增 | **N-2**：`MenuService.allMenusCache` 多實例下漂移 |
| 縱深防禦 | ✅ 良好 | ⚠️ 細節 | **N-6**：`PermissionController` 未做方法層 `@PreAuthorize` |
| 審計追蹤 | ✅ 已修正 | ✅ 維持 | Menu update + Role 四個寫入都有 `@AuditEvent` |

---

## 五、值得優化的功能建議

| # | 功能 | 價值 | 概要 |
| - | ---- | ---- | ---- |
| F-1 | **批次重排選單 API** | UX ★★ | `PATCH /v1/auth/menus/reorder` 一次更新多筆 `{menuId, sortOrder}`，避免拖拉排序時打 N 次 API |
| F-2 | **角色 clone API** | UX + 維運 ★★ | `POST /v1/auth/roles/{roleId}/clone` 一鍵複製角色及其所有 permission；建立「區域經理」這類衍生角色省時 |
| F-3 | **Permission 分類欄位** | UX ★★ | `PermissionEntity` 加 `category` 欄位（例 USER_MGMT / RBAC / AUDIT），UI 權限挑選頁可分組顯示 |
| F-4 | **分散式選單 cache（Redis）** | 一致性 + 效能 ★★★ | 解 N-2；採 Spring Cache + `@Cacheable` / `@CacheEvict` 對接 Redis，所有 Pod 共用單一 cache |
| F-5 | **角色 / 權限匯入匯出** | 維運 ★★ | `GET /v1/auth/roles/export`、`POST /v1/auth/roles/import`，協助新場域 onboarding 套用樣板 |
| F-6 | **選單權限繼承** | 設計簡化 ★ | DIRECTORY 被授權時自動視為對所有 PAGE 子節點有授權；減少 permission 條數爆炸（需評估與既有資料相容性）|
| F-7 | **Permission 用量視覺化** | 維運 ★★ | 後台頁面顯示「此 permission 目前被哪些 role 使用 / 哪些使用者實際命中」，協助刪除前評估影響 |
| F-8 | **角色變更影響預覽** | 安全 + UX ★★ | `updateRole` / `assignPermissions` 之前可先 `POST /v1/auth/roles/{roleId}/preview-impact`，預覽會影響多少使用者 |
| F-9 | **Per-tenant 角色覆寫**（已留欄位） | 多租戶彈性 ★★ | `RolePermissionEntity.tenantId` 已預留，但實際寫入路徑（`assignPermissions`）固定寫 `tenantId=null`，未啟用 tenant 覆寫；可規劃啟用 |
| F-10 | **權限 deprecation 流程** | 維運 ★ | `PermissionEntity` 加 `deprecated` 旗標；deprecated 權限禁止再被指派，但既有指派保留 |

---

## 六、修復路線圖建議

### Sprint 1 — 縱深防禦與多實例一致性
1. **N-1** — ✅ 已修復。`getAllMenusCached()` 加 double-checked locking；測試 4 個全過。
2. **N-2** — ✅ 已修復。採用方案 A 移除 `allMenusCache`，直接走 DB；同步更新 [04-current-inventory.md](../../new-feature/cache/04-current-inventory.md) §3.4；測試 4 個全過。
3. **N-4** — ✅ 已修復。`updateRole` / `toggleEnabled` / `assignPermissions` 補 `isRoleAssignable` DataScope 二次檢查；測試 8 個全過。
4. **N-6** — ✅ 已修復。`PermissionController.listPermissions` 加 `@PreAuthorize("hasAuthority('ROLE_LIST')")`；測試 4 個全過。

### Sprint 2 — 健壯性與資料完整性
5. **N-5** — ✅ 已修復。`updateMenu` 加 `detectCycle()` 循環檢查；`toMenuDtoWithChildren` / `toUserMenuDtoWithChildren` 加 `Set<Long> visited` 防禦；測試 6 個全過。
6. **N-3** — ✅ 已修復。新增 `PermissionRepository.findCodesByRoleIdsAndTenant` 合 JOIN，`MenuService.getMyMenus` 改呼叫；測試 4 個全過。
7. **N-7** — ✅ 已修復。`AssignRolePermissionsRequest.permissionIds` 改 `@NotEmpty(message = "permissionIds 不能為空")`；測試 4 個全過。

### Sprint 3+ — 細節與 UX
8. **N-9** / **N-10** / **N-11** — DTO 驗證與排序細節。
9. F-1 ~ F-4 功能強化（特別是 F-4 與 N-2 共底層機制）。

---

## 七、附錄：本次複查涵蓋的檔案

### Controller
- [MenuController.java](../../../backend/src/main/java/com/taipei/iot/rbac/controller/MenuController.java)
- [RoleController.java](../../../backend/src/main/java/com/taipei/iot/rbac/controller/RoleController.java)
- [PermissionController.java](../../../backend/src/main/java/com/taipei/iot/rbac/controller/PermissionController.java)

### Service
- [MenuService.java](../../../backend/src/main/java/com/taipei/iot/rbac/service/MenuService.java)
- [RoleService.java](../../../backend/src/main/java/com/taipei/iot/rbac/service/RoleService.java)
- [PermissionService.java](../../../backend/src/main/java/com/taipei/iot/rbac/service/PermissionService.java)

### Entity
- [MenuEntity.java](../../../backend/src/main/java/com/taipei/iot/rbac/entity/MenuEntity.java)
- [PermissionEntity.java](../../../backend/src/main/java/com/taipei/iot/rbac/entity/PermissionEntity.java)
- [RolePermissionEntity.java](../../../backend/src/main/java/com/taipei/iot/rbac/entity/RolePermissionEntity.java)
- [RolePermissionId.java](../../../backend/src/main/java/com/taipei/iot/rbac/entity/RolePermissionId.java)

### Repository
- [MenuRepository.java](../../../backend/src/main/java/com/taipei/iot/rbac/repository/MenuRepository.java)
- [PermissionRepository.java](../../../backend/src/main/java/com/taipei/iot/rbac/repository/PermissionRepository.java)
- [RolePermissionRepository.java](../../../backend/src/main/java/com/taipei/iot/rbac/repository/RolePermissionRepository.java)

### DTO
- [CreateMenuRequest.java](../../../backend/src/main/java/com/taipei/iot/rbac/dto/request/CreateMenuRequest.java)
- [UpdateMenuRequest.java](../../../backend/src/main/java/com/taipei/iot/rbac/dto/request/UpdateMenuRequest.java)
- [CreateRoleRequest.java](../../../backend/src/main/java/com/taipei/iot/rbac/dto/request/CreateRoleRequest.java)
- [UpdateRoleRequest.java](../../../backend/src/main/java/com/taipei/iot/rbac/dto/request/UpdateRoleRequest.java)
- [AssignRolePermissionsRequest.java](../../../backend/src/main/java/com/taipei/iot/rbac/dto/request/AssignRolePermissionsRequest.java)
- [MenuDto.java](../../../backend/src/main/java/com/taipei/iot/rbac/dto/response/MenuDto.java)
- [UserMenuDto.java](../../../backend/src/main/java/com/taipei/iot/rbac/dto/response/UserMenuDto.java)
- [RoleDto.java](../../../backend/src/main/java/com/taipei/iot/rbac/dto/response/RoleDto.java)
- [RolePermissionListDto.java](../../../backend/src/main/java/com/taipei/iot/rbac/dto/response/RolePermissionListDto.java)
- [PermissionDto.java](../../../backend/src/main/java/com/taipei/iot/rbac/dto/response/PermissionDto.java)

### 跨模組整合
- [SecurityConfig.java](../../../backend/src/main/java/com/taipei/iot/config/SecurityConfig.java)（`/v1/auth/menus/**`、`/v1/auth/roles/**`、`/v1/auth/permissions/**` 路由規則）
- [AuthServiceImpl.java](../../../backend/src/main/java/com/taipei/iot/auth/service/impl/AuthServiceImpl.java) `resolvePermissions()`
- [JwtAuthenticationFilter.java](../../../backend/src/main/java/com/taipei/iot/auth/security/JwtAuthenticationFilter.java)

### 測試
- [MenuControllerTest.java](../../../backend/src/test/java/com/taipei/iot/rbac/controller/MenuControllerTest.java)
- [MenuServiceTest.java](../../../backend/src/test/java/com/taipei/iot/rbac/service/MenuServiceTest.java)
- [RoleServiceTest.java](../../../backend/src/test/java/com/taipei/iot/rbac/service/RoleServiceTest.java)
