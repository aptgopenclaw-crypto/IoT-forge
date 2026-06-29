# Dept 模組 Code Review & Security Review v2

> 本文件為 [dept-module-code-review.md](dept-module-code-review.md) 的後續複查與擴充。  
> 重點：(1) 驗證 v1 全部 11 項議題確實落實於程式碼 (2) 補上 v1 漏看的新問題 (3) 提出值得優化的功能建議。  
> 複查日期：2026-05-27。  
> 審查範圍：`backend/src/main/java/com/taipei/iot/dept/` 全部 controller / service / aspect / context / annotation / dto / entity / enum / repository + 4 個測試類；跨模組整合（`TenantFilterAspect`、`AuditEventType`、`UserTenantMappingRepository`、`SecurityConfig`）；前端 `views/admin/dept/DeptManageView.vue`、`components/DeptTreeSelector.vue`、`stores/deptStore.ts`、`api/dept/index.ts`、`types/dept.ts`；DB migration V5 / V5_1 / V14 / V17 / V18 / V21 / V22。

---

## 一、整體評價

| 維度       | v1 評分 | v2 評估    | 變化 |
| ---------- | ------- | ---------- | ---- |
| 安全性     | 8.5/10  | **9.0/10** | ⬆ 0.5 — v1 11/11 ✅；本輪僅發現 TOCTOU 邊界與 DataScope 死程式碼，皆非阻斷性 |
| 正確性     | 9.0/10  | **9.5/10** | ⬆ 0.5 — `existsByTenantIdAndDeptNameAndPid` 含租戶條件、刪除前完整檢查、`hierarchy_path` 一致性正確 |
| 效能       | 8.0/10  | **7.5/10** | ⬇ 0.5 — N-1：`hierarchy_path` 缺索引；N-5：部門樹無大小上限（萬級部門有 OOM 風險） |
| 可維護性   | 9.0/10  | **8.5/10** | ⬇ 0.5 — N-2：`@DataPermission` AOP 全套基礎建設無人使用（死程式碼） |
| 可觀測性   | 8.0/10  | **8.5/10** | ⬆ 0.5 — `@AuditEvent` 完整、`DataScopeEnum.fromString` 無效值有 warn log |
| **總分**   | **8.6/10** | **8.8/10** | ⬆ 0.2 |

**結論**：v1 全部 11 項議題（含安全、驗證、效能、設計決策）皆已於程式碼層級確認落實，模組整體品質為**生產就緒**水準。本輪新發現的議題多屬「預防性」或「邊界情況」（hierarchy_path 索引、AOP 死碼、TOCTOU 競態），無需阻斷上線；建議於下兩個 sprint 內陸續處理。

---

## 二、v1 議題複查

| # | v1 議題 | 狀態 | 證據 |
| - | ------- | ---- | ---- |
| 1 | `/list`、`/options` 不加 DataScope 過濾（部門樹為公開資訊）| ✅ 已決策保留 | [DeptController.java](../../../backend/src/main/java/com/taipei/iot/dept/controller/DeptController.java) `getDeptTree` / `getDeptOptions` 不套 scope；`getScopedDeptOptions` 才走 `DataScopeHelper` |
| 2 | `GET /{deptId}` 跨租戶安全 | ✅ 已確認 | [DeptInfoEntity.java](../../../backend/src/main/java/com/taipei/iot/dept/entity/DeptInfoEntity.java) `@Filter(name="tenantFilter", condition="tenant_id = :tenantId")`；[TenantFilterAspect.java](../../../backend/src/main/java/com/taipei/iot/tenant/TenantFilterAspect.java) `@Before` 拦截 `TenantScopedRepository` 子類自動啟用 filter，`DeptInfoRepository extends TenantScopedRepository` |
| 3 | `CreateDeptRequest.deptName` `@Size(max=100)` | ✅ 已修補 | [CreateDeptRequest.java](../../../backend/src/main/java/com/taipei/iot/dept/dto/CreateDeptRequest.java) `@NotBlank @Size(max=100)` |
| 4 | `UpdateDeptRequest.deptName` `@Size(max=100)` | ✅ 已修補 | [UpdateDeptRequest.java](../../../backend/src/main/java/com/taipei/iot/dept/dto/UpdateDeptRequest.java) `@Size(max=100)` |
| 5 | `UpdateDeptRequest.status` 值域限制 | ✅ 已修補 | [UpdateDeptRequest.java](../../../backend/src/main/java/com/taipei/iot/dept/dto/UpdateDeptRequest.java) `@Min(0) @Max(1) Short status` |
| 6 | `DataScopeHelper.getVisibleDeptIds()` 改 DB LIKE 查詢 | ✅ 已修補 | [DataScopeHelper.java](../../../backend/src/main/java/com/taipei/iot/dept/service/DataScopeHelper.java) 呼叫 `findByHierarchyPathStartingWith(prefix)`；[DeptInfoRepository.java](../../../backend/src/main/java/com/taipei/iot/dept/repository/DeptInfoRepository.java) `@Query("... WHERE d.hierarchyPath LIKE :prefix%")` |
| 7 | `deleteDept()` 改用精確 repository 方法 | ✅ 已修補 | [DeptService.java](../../../backend/src/main/java/com/taipei/iot/dept/service/DeptService.java) 改呼 `userTenantMappingRepository.findByTenantIdAndDeptIdAndEnabledTrue(tenantId, deptId)`，不再全量查 + in-memory filter |
| 8 | `createDept()` 兩次 save（hierarchy_path 需自身 ID）| ✅ 維持 | [DeptService.java](../../../backend/src/main/java/com/taipei/iot/dept/service/DeptService.java) 第一 save 取 ID → 設 `hierarchy_path` → 第二 save；設計合理 |
| 9 | `DeptDto` 加 `@JsonInclude(NON_NULL)` | ✅ 已修補 | [DeptDto.java](../../../backend/src/main/java/com/taipei/iot/dept/dto/DeptDto.java) `@JsonInclude(JsonInclude.Include.NON_NULL)` |
| 10 | `DataScopeEnum.fromString()` 無效值加 warn log | ✅ 已修補 | [DataScopeEnum.java](../../../backend/src/main/java/com/taipei/iot/dept/enums/DataScopeEnum.java) `catch (IllegalArgumentException) { log.warn("Unknown DataScope value: {}", value); return ALL; }` |
| 11 | 讀取端點不加 `@PreAuthorize`（由 menu 控制）| ✅ 已決策保留 | [DeptController.java](../../../backend/src/main/java/com/taipei/iot/dept/controller/DeptController.java) 讀取端點無 `@PreAuthorize`；寫入端點皆有 `@PreAuthorize("hasAuthority('DEPT_CREATE/UPDATE/DELETE')")`；[V18__remove_dept_list_from_dept_user.sql](../../../backend/src/main/resources/db/migration/V18__remove_dept_list_from_dept_user.sql) 已從 DEPT_USER 移除 DEPT_LIST 權限 |

> **小結**：v1 全部 11 項議題，**11/11 ✅ 已於程式碼層級驗證**。無回歸、無遺漏。

---

## 三、本輪新發現問題

### 🟠 中風險

#### N-1. `hierarchy_path` 欄位缺索引 — LIKE prefix 查詢退化為全表掃描 ✅ 已修復

- **狀態**：✅ 已修復 — 新增 `V55__dept__add_hierarchy_path_index.sql` migration，建立 `idx_dept_hierarchy_path (text_pattern_ops) WHERE status = 1` partial index，並新增 `HierarchyPathIndexTest` 4 案例驗證 migration 內容正確。

- **檔案**：[V55__dept__add_hierarchy_path_index.sql](../../../backend/src/main/resources/db/migration/V55__dept__add_hierarchy_path_index.sql)、[DeptInfoRepository.java](../../../backend/src/main/java/com/taipei/iot/dept/repository/DeptInfoRepository.java)
- **問題**：V5 建表時僅建立 `idx_dept_tenant_id` 與 `idx_dept_pid`，未對 `hierarchy_path` 建索引。v1 #6 將 `getVisibleDeptIds()` 從「記憶體 filter」改為 `WHERE hierarchy_path LIKE :prefix%`（DB 查詢）後，**這條查詢在大量資料下會變成 Seq Scan**：
  - 部門數 < 1,000：無感
  - 部門數 ~10,000：明顯延遲（>100ms）
  - 部門數 > 100,000：可能造成 API 超時 + DB CPU 飆高
- **建議修法**：新增 migration，PostgreSQL 因 `hierarchy_path LIKE 'x%'` 在預設 `text_pattern_ops` 之外的 collation 可能無法用一般 b-tree，最穩妥寫法：

  ```sql
  -- V55__dept__add_hierarchy_path_index.sql
  CREATE INDEX idx_dept_hierarchy_path
      ON dept_info (hierarchy_path text_pattern_ops)
      WHERE status = 1;
  ```

  > `text_pattern_ops` 確保 `LIKE prefix%` 可用索引；partial index `WHERE status = 1` 與 Repository 既有條件一致，索引更小。
- **優先級**：🟠 中（單一場景目前資料量小，但屬「資料量成長就會痛」型問題，建議搭 N-5 一同處理）

---

#### N-2. `@DataPermission` AOP 全套基礎建設無任何呼叫端 — 死程式碼 ✅ 已處理

- **狀態**：✅ 已處理（方案 A）— 四個檔案（`DataPermissionAspect`、`DataPermission`、`DataScopeContext`、`DataScopeFilter`）皆已新增 class-level Javadoc 註明「預留框架，目前各模組採 DataScopeHelper 手動控制」，並新增 `DataPermissionReservedFrameworkTest` 5 案例驗證。

- **檔案**：[DataPermission.java](../../../backend/src/main/java/com/taipei/iot/dept/annotation/DataPermission.java)、[DataPermissionAspect.java](../../../backend/src/main/java/com/taipei/iot/dept/aspect/DataPermissionAspect.java)、[DataScopeContext.java](../../../backend/src/main/java/com/taipei/iot/dept/context/DataScopeContext.java)、[DataScopeFilter.java](../../../backend/src/main/java/com/taipei/iot/dept/context/DataScopeFilter.java)
- **問題**：
  - annotation + aspect + ThreadLocal context + Value Object 四個檔案完整實作（含 finally 清理、null UserInfo 防護），測試也有 7 個案例專門驗 aspect。
  - 但在整個 codebase grep `@DataPermission` 找不到任何 service 方法套用此註解。
  - DeptService 中唯一真正用到 data scope 的是 `getScopedDeptOptions()`，其作法是**直接呼叫** `dataScopeHelper.getVisibleDeptIds(user)`，並未經 `DataScopeContext.get()` 流程。
- **影響**：
  - 程式碼可讀性：開發者看到完整 AOP 設施會誤以為「dept 模組有資料範圍 ThreadLocal 機制」，但實際從未啟動。
  - 測試 7 個案例占用 CI 時間卻無實質保護。
  - 若將來真要全域套用 row-level scope，現有手寫 `getVisibleDeptIds()` 與 ThreadLocal 機制並存反成混亂。
- **建議修法**：擇一處理
  1. **方案 A（推薦短期）**：在 `DataPermissionAspect` class 上加 Javadoc，明示「此 AOP 為未來統一資料範圍框架預留，目前 dept 模組採手動 `DataScopeHelper.getVisibleDeptIds()` 控制」，並引用 ADR 編號。
  2. **方案 B（長期）**：等其他模組（user / audit）也需要 row-level scope 時，統一改造為「Service 方法掛 `@DataPermission` → AOP 填 context → Repository 從 context 讀 dept ids 加入查詢條件」。
  3. **方案 C（拆除）**：若確認沒有未來需求，連同測試一併移除（不推薦，因有效能優勢的 ThreadLocal AOP 是常見企業模式，保留為將來預留較合理）。
- **優先級**：🟠 中（不影響功能，但影響「程式碼意圖」可讀性）

---

#### N-3. `createDept` 重名檢查存在 TOCTOU race — 高並發下回傳 500 而非 400 ✅ 已修復

- **狀態**：✅ 已修復 — `DeptService.createDept()` 將 `deptInfoRepository.save()` 包在 try-catch 中，捕獲 `DataIntegrityViolationException` 轉拋為 `BusinessException(DEPT_ALREADY_EXISTS)`，並新增 `DeptServiceTest.createDept_concurrentDuplicate_shouldThrowBusinessException` 1 案例驗證。

- **檔案**：[DeptService.java](../../../backend/src/main/java/com/taipei/iot/dept/service/DeptService.java)（`createDept` 約第 76-110 行）
- **問題**：

  ```java
  if (deptInfoRepository.existsByTenantIdAndDeptNameAndPid(tenantId, name, pid)) {
      throw new BusinessException(ErrorCode.DEPT_ALREADY_EXISTS);
  }
  // ... 中間有其他邏輯 ...
  DeptInfoEntity saved = deptInfoRepository.save(entity); // ← 兩個 request 同時到此
  ```

  - 兩個 admin 同時 POST 同名部門 + 同 pid，雙方 exists 都回 false，皆進入 save。
  - DB 端有 `UNIQUE(tenant_id, dept_name, pid)` 約束會擋下第二筆，但會拋 `DataIntegrityViolationException`，目前 `GlobalExceptionHandler` 預設將其轉為 500。
- **影響**：實務發生機率低（admin 同時建同名同層機率小），但 UX 不佳。
- **建議修法**：在 `createDept` 外層 catch 並轉為業務例外：

  ```java
  try {
      // existing check + save logic
  } catch (DataIntegrityViolationException e) {
      throw new BusinessException(ErrorCode.DEPT_ALREADY_EXISTS);
  }
  ```

  或在 `GlobalExceptionHandler` 集中處理 `DataIntegrityViolationException` → 409 Conflict。
- **優先級**：🟠 中

---

### 🟡 低風險 / 建議

#### N-4. `updateDept()` 不支援修改 `pid`（無法移動部門至他父） ✅ 維持現狀（設計決策）

- **檔案**：[UpdateDeptRequest.java](../../../backend/src/main/java/com/taipei/iot/dept/dto/UpdateDeptRequest.java)、[DeptService.java](../../../backend/src/main/java/com/taipei/iot/dept/service/DeptService.java)
- **問題**：DTO 僅含 `deptName` / `deptSort` / `status`，未開放 `pid`。組織重整必須走「停用舊部門 → 建新部門 → 重新分配人員」流程。
- **決策：維持現狀，不支援 pid 修改**。理由：
  1. **頻率低**：部門架構調整在實務上為低頻操作，通常伴隨組織重整，由最高管理者決策。
  2. **DataScope 影響大**：修改 `pid` 必須級聯更新該部門及所有後代的 `hierarchy_path`，否則 `DataScopeHelper.getVisibleDeptIds()`（基於 `hierarchy_path LIKE prefix%`）會產生錯誤結果 — 原父管理者仍可看到已移出部門，新父管理者看不到。
  3. **工程量大**：需同時實作自循環/後代循環檢查、事務內級聯更新、已登入使用者權限快取失效處理。
  4. **替代方案可行**：「停用舊部門 → 建新部門 → 重新分配人員」流程雖繁瑣但明確且可稽核。
- **優先級**：🟡 低（維持現狀，不動作）

#### N-5. 若日後支援 pid 修改，需加入周期防護 ✅ 維持現狀（與 N-4 綁定）

- **檔案**：[DeptService.java](../../../backend/src/main/java/com/taipei/iot/dept/service/DeptService.java) `updateDept()`
- **問題**：目前不開放 pid 修改所以無風險，但若 N-4 採用，必須加：

  ```java
  if (newPid.equals(entity.getDeptId())) throw new BusinessException(DEPT_CYCLE_DETECTED);
  var descendants = deptInfoRepository.findByHierarchyPathStartingWith(entity.getHierarchyPath());
  if (descendants.stream().anyMatch(d -> d.getDeptId().equals(newPid))) {
      throw new BusinessException(DEPT_CYCLE_DETECTED);
  }
  ```
- **決策**：與 N-4 同步維持現狀。若未來確實有部門移動需求，再一併實作 pid 修改 + 循環防護 + hierarchy_path 級聯更新。
- **優先級**：🟡 低（預防性，與 N-4 綁定）

#### N-6. `getDeptTree()` / `getDeptOptions()` 一次性載入全租戶部門 — 缺大小上限 ✅ 維持現狀（設計決策）

- **檔案**：[DeptService.java](../../../backend/src/main/java/com/taipei/iot/dept/service/DeptService.java) `getDeptTree` / `buildTree`
- **問題**：無分頁、無 hard limit；若單一租戶被灣入 100,000 筆 dept 會有 OOM 風險。
- **決策：維持現狀，不加 hard limit / lazy load**。理由：
  1. **實務發生機率極低**：典型企業租戶 100~500 部門，即便大型組織也不超過 2,000，遠低於問題閾值。
  2. **管理介面有權限控制**：建部門需 `DEPT_CREATE` 權限，惡意灣入不易發生。
  3. **過度設計風險**：lazy load 增加前後端複雜度，目前收益不符成本。
- **優先級**：🟡 低（維持現狀，不動作）

#### N-7. 前端 `DeptManageView` 名稱顯示無 v-html，但建議集中 sanitize 工具 ✅ 維持現狀（無須修復）

- **檔案**：[DeptManageView.vue](../../../frontend/src/views/admin/dept/DeptManageView.vue)、[DeptTreeSelector.vue](../../../frontend/src/components/DeptTreeSelector.vue)
- **問題**：目前皆走 mustache `{{ deptName }}` 與 `el-tree-select` 內建插值，無 XSS。但未來若改用 custom slot + v-html 顯示自訂 icon / 標籤，無集中 sanitize 工具會易踩坑。
- **決策：維持現狀，不引入 sanitize 工具**。理由：目前無 `v-html` 使用場景，XSS 風險不存在；待日後確有富文字需求時再引入 `DOMPurify` 即可。
- **優先級**：🟡 低（資訊性，無須動作）

---

## 四、安全性總結

| 面向 | 評估 | 摘要 |
|------|------|------|
| 認證 | ✅ | 全 `/v1/auth/dept/**` 路徑落入 JWT filter |
| 授權 | ✅ | 寫入 `@PreAuthorize` 完整；讀取由 menu 權限控制（v1 已決策）|
| 多租戶隔離 | ✅ | `@Filter(tenantFilter)` + `TenantFilterAspect` 雙重保險；`findByDeptId` 受同一機制保護 |
| 資料範圍 | ⚠ | `getScopedDeptOptions()` 正確；`/list` / `/options` 為設計上公開（v1 已決策）|
| 輸入驗證 | ✅ | `deptName` `@Size(100)`、`status` `@Min/@Max`、`@NotBlank` 完整 |
| Audit | ✅ | `CREATE_DEPT` / `UPDATE_DEPT` / `DELETE_DEPT` 三事件正確掛 |
| 競態條件 | ✅ | N-3 TOCTOU 已修復；DB UNIQUE 兜底 + catch 轉 BusinessException |
| XSS | ✅ | 無 v-html |
| ThreadLocal 衛生 | ✅ | `DataPermissionAspect` `finally { clear() }`（即便目前無人用，AOP 本身正確）|

---

## 五、值得優化的功能建議

| # | 功能 | 價值 | 概要 |
| - | ---- | ---- | ---- |
| F-1 | ~~**支援部門移動（改 `pid`）**~~ ❌ 不實作 | 營運 ★★★ | 經評估決定不支援：低頻操作、衝擊 DataScope hierarchy_path 可見性、工程量大。替代方案：停用舊部門 → 建新部門 → 重分配人員 |
| F-2 | **部門樹 lazy load / virtual scroll** | 效能 ★★ | `el-tree` 啟用 `lazy` + 後端新增 `GET /dept/children?pid=X`；應對 N-6 萬級部門 |
| F-3 | **部門名稱模糊搜尋** | UX ★★ | `GET /dept/list?keyword=xxx`；Service 加 `LIKE %xxx%` + DataScope 過濾 |
| F-4 | **部門成員人數統計（`DeptDto.userCount`）** | UX ★★ | 一次 LEFT JOIN aggregate 取得 enabled user count；列表頁顯示「業務部 (12)」|
| F-5 | **部門匯出 / 匯入 Excel** | 維運 ★ | EasyExcel；多層級保留階層欄；匯入時驗 pid 存在且同租戶 |
| F-6 | **批次操作（批次啟用 / 停用 / 刪除）** | 維運 ★ | POST `/dept/batch-update`；每筆走相同 delete pre-check |
| F-7 | **`@DataPermission` 全域統一化（呼應 N-2）** | 架構 ★★ | 等 user / audit / 其他列表也要 row-level 時，dept 同步改造 |
| F-8 | **部門變更歷史 / 樹結構 snapshot** | 稽核 ★★ | 在 audit_log 之外，獨立 `dept_change_log(deptId, fieldName, oldVal, newVal, changedAt)` 方便組織重整回溯 |

---

## 六、修復路線圖建議

### Sprint 1 — 立即（資料量成長前先補）
1. **N-1** — ✅ 新增 `idx_dept_hierarchy_path (text_pattern_ops) WHERE status = 1` migration（V55）。
2. **N-3** — ✅ `createDept` catch `DataIntegrityViolationException` 轉 `DEPT_ALREADY_EXISTS`。
3. **N-2** — ✅ 在 `DataPermissionAspect` 等四個檔案加 Javadoc 註明「為未來統一資料範圍框架預留」。

### Sprint 2 — 預防 / UX
4. **N-6** — ✅ 維持現狀（實務不會超過典型企業部門數上限）。
5. **F-3** — 部門模糊搜尋。

### Sprint 3+ — 功能擴充
6. **~~F-1 + N-4 + N-5~~** — ❌ 部門移動（改 pid）：經評估決定**不實作**。部門架構調整為低頻操作，且修改 pid 會衝擊 `hierarchy_path` 基礎的 DataScope 可見性，工程量與風險不符收益。替代方案為「停用舊部門 → 建新部門 → 重分配人員」。
7. **F-4** — 成員人數欄位。
8. **F-7** — 視其他模組需求決定是否啟用 `@DataPermission` AOP。

---

## 七、附錄：本次複查涵蓋的檔案

### Backend
- [DeptController.java](../../../backend/src/main/java/com/taipei/iot/dept/controller/DeptController.java)
- [DeptService.java](../../../backend/src/main/java/com/taipei/iot/dept/service/DeptService.java)
- [DataScopeHelper.java](../../../backend/src/main/java/com/taipei/iot/dept/service/DataScopeHelper.java)
- [DataPermissionAspect.java](../../../backend/src/main/java/com/taipei/iot/dept/aspect/DataPermissionAspect.java)
- [DataPermission.java](../../../backend/src/main/java/com/taipei/iot/dept/annotation/DataPermission.java)
- [DataScopeContext.java](../../../backend/src/main/java/com/taipei/iot/dept/context/DataScopeContext.java)
- [DataScopeFilter.java](../../../backend/src/main/java/com/taipei/iot/dept/context/DataScopeFilter.java)
- [DataScopeEnum.java](../../../backend/src/main/java/com/taipei/iot/dept/enums/DataScopeEnum.java)
- [DeptInfoEntity.java](../../../backend/src/main/java/com/taipei/iot/dept/entity/DeptInfoEntity.java)
- [DeptInfoRepository.java](../../../backend/src/main/java/com/taipei/iot/dept/repository/DeptInfoRepository.java)
- [CreateDeptRequest.java](../../../backend/src/main/java/com/taipei/iot/dept/dto/CreateDeptRequest.java)
- [UpdateDeptRequest.java](../../../backend/src/main/java/com/taipei/iot/dept/dto/UpdateDeptRequest.java)
- [DeptDto.java](../../../backend/src/main/java/com/taipei/iot/dept/dto/DeptDto.java)
- [DeptOptionVO.java](../../../backend/src/main/java/com/taipei/iot/dept/dto/DeptOptionVO.java)

### 測試
- `backend/src/test/java/com/taipei/iot/dept/service/DeptServiceTest.java`（14+ 場景）
- `backend/src/test/java/com/taipei/iot/dept/service/DataScopeHelperTest.java`（8+ 場景）
- `backend/src/test/java/com/taipei/iot/dept/aspect/DataPermissionAspectTest.java`（7 場景）
- `backend/src/test/java/com/taipei/iot/dept/controller/DeptControllerTest.java`（8+ 場景）

### DB Migration
- [V5__dept__create_tables.sql](../../../backend/src/main/resources/db/migration/V5__dept__create_tables.sql)
- `V5_1__dept__seed_data.sql`
- `V14__add_dept_delete_to_dept_admin.sql`
- `V17__remove_user_list_from_dept_user.sql`
- [V18__remove_dept_list_from_dept_user.sql](../../../backend/src/main/resources/db/migration/V18__remove_dept_list_from_dept_user.sql)
- `V21__audit__backfill_dept_id.sql`
- `V22__login_log__add_display_name_dept_id.sql`

### 跨模組整合
- [TenantFilterAspect.java](../../../backend/src/main/java/com/taipei/iot/tenant/TenantFilterAspect.java)
- `audit/enums/AuditEventType.java`（`CREATE_DEPT` / `UPDATE_DEPT` / `DELETE_DEPT`）
- `auth/repository/UserTenantMappingRepository.java`（`findByTenantIdAndDeptIdAndEnabledTrue`、`clearDeptIdByTenantIdAndDeptId`）

### 前端
- [DeptManageView.vue](../../../frontend/src/views/admin/dept/DeptManageView.vue)
- [DeptTreeSelector.vue](../../../frontend/src/components/DeptTreeSelector.vue)
- [deptStore.ts](../../../frontend/src/stores/deptStore.ts)
- [api/dept/index.ts](../../../frontend/src/api/dept/index.ts)
- `frontend/src/types/dept.ts`
