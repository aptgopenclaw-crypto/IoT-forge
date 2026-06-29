# Dept 模組 Code Review & Security Review

> 審查日期：2026-05-20
> 審查範圍：`backend/src/main/java/com/taipei/iot/dept/` 全部子套件 + 前端相關檔案

---

## 模組結構總覽

```
dept/
├── annotation/
│   └── DataPermission.java             # 資料範圍註解（行級過濾）
├── aspect/
│   └── DataPermissionAspect.java       # AOP 切面，注入 DataScopeFilter 至 ThreadLocal
├── context/
│   ├── DataScopeContext.java           # ThreadLocal 持有 DataScopeFilter
│   └── DataScopeFilter.java           # 過濾器 Value Object（NONE/EXACT/HIERARCHY_PREFIX）
├── controller/
│   └── DeptController.java            # REST API（CRUD + options + scope-options）
├── dto/
│   ├── CreateDeptRequest.java          # 建立部門請求
│   ├── UpdateDeptRequest.java          # 更新部門請求
│   ├── DeptDto.java                    # 部門回應 DTO（含 children）
│   └── DeptOptionVO.java              # 下拉選項 VO
├── entity/
│   └── DeptInfoEntity.java            # JPA 實體（tenant_id + hierarchy_path）
├── enums/
│   └── DataScopeEnum.java             # ALL / THIS_LEVEL / THIS_LEVEL_AND_BELOW
├── repository/
│   └── DeptInfoRepository.java        # JPA Repository
└── service/
    ├── DeptService.java               # 部門 CRUD 業務邏輯
    └── DataScopeHelper.java           # 計算當前使用者可見部門清單
```

**前端檔案**：`types/dept.ts`、`api/dept/index.ts`、`stores/deptStore.ts`、`components/DeptTreeSelector.vue`、`views/admin/dept/DeptManageView.vue`

**測試覆蓋**：4 個測試檔案（`DeptServiceTest`、`DataScopeHelperTest`、`DataPermissionAspectTest`、`DeptControllerTest`）

---

## 總體評價

Dept 模組設計**良好**，核心亮點：

- **階層式部門結構**：`hierarchy_path`（如 `/1/2/`）支援高效的 prefix query，避免遞迴查詢
- **DataScope 行級安全**：`DataPermissionAspect` + ThreadLocal 實現 AOP 資料範圍限縮
- **完整租戶隔離**：`@Filter(tenantFilter)` + `TenantEntityListener` 雙保險
- **權限控管**：寫入操作均有 `@PreAuthorize` + `@AuditEvent`
- **刪除前檢查**：子部門存在性 + 使用者歸屬（含 soft-delete 判斷）
- **前端 Tree 組件**：`DeptTreeSelector.vue` 封裝 `el-tree-select`，支持 filterable、clearable

---

## 安全審查（Security Review）

### ✅ 防護到位的項目

| 防護類別 | 實作方式 | 評估 |
|----------|----------|------|
| 認證 | 所有 API 在 `/v1/auth/` 下，JWT Filter 保護 | ✅ |
| 權限 | `@PreAuthorize("hasAuthority('DEPT_CREATE/UPDATE/DELETE')")` | ✅ |
| 租戶隔離 | `@Filter(tenantFilter)` + `TenantEntityListener` 自動注入 | ✅ |
| DataScope 行級過濾 | `DataPermissionAspect` ThreadLocal + finally 清除 | ✅ |
| 重複名稱檢查 | `existsByTenantIdAndDeptNameAndPid()` 含 tenantId 條件 | ✅ |
| 刪除關聯檢查 | 子部門 + 使用者（含 soft-delete 判斷） | ✅ |
| ThreadLocal 清理 | `DataScopeContext.clear()` 在 finally 中 | ✅ |
| 空使用者防護 | `null UserInfo` → deptId = -1 不可能條件 | ✅ |
| 稽核軌跡 | 寫入操作加 `@AuditEvent` | ✅ |
| 輸入驗證 | `@NotBlank deptName`、`@NotNull deptId`、`@Valid` | ✅ |

---

### 需要注意的安全問題

#### 1. [高] `GET /v1/auth/dept/list` 和 `GET /v1/auth/dept/options` 缺少 DataScope 過濾

```java
@GetMapping("/list")
public BaseResponse<List<DeptDto>> getDeptTree() {
    return BaseResponse.success(deptService.getDeptTree());
}

@GetMapping("/options")
public BaseResponse<List<DeptOptionVO>> getDeptOptions() {
    return BaseResponse.success(deptService.getDeptOptions());
}
```

**風險**：`getDeptTree()` 和 `getDeptOptions()` 只以 `status=1` 過濾，**未考慮 DataScope**。`THIS_LEVEL` 或 `THIS_LEVEL_AND_BELOW` 的使用者仍可看到整棵部門樹結構（所有部門名稱）。

- `/scope-options` 有正確限縮 → 但 `/list` 和 `/options` 沒有
- 部門名稱通常不是敏感資料（用於下拉選單等），但若業務要求「部門使用者不能看到其他部門」，則目前設計不符合

**建議**：
- 如果部門樹是「公開資訊」（多數系統如此）→ 加文件說明設計決策
- 如果需要限縮 → `/list` 加入 `@PreAuthorize("hasAuthority('DEPT_LIST')")` 且在 Service 中套用 DataScope

#### 2. [中等] `GET /v1/auth/dept/{deptId}` 無跨租戶/跨 DataScope 存取控制

```java
@GetMapping("/{deptId}")
public BaseResponse<DeptDto> getDeptById(@PathVariable Long deptId) {
    return BaseResponse.success(deptService.getDeptById(deptId));
}
```

**分析**：`findByDeptId()` 走 JPA，Hibernate `@Filter(tenantFilter)` 會自動加上 `tenant_id` 條件，所以**跨租戶**是安全的。但**跨 DataScope** 是否允許需要確認。

**風險**：如果 `@Filter` 在 `findByDeptId` 上有效（Session-level filter 需手動 enable），則安全。但 Spring Data JPA 的 `findBy` 方法是否受 Hibernate `@Filter` 影響，取決於 `TenantScopedRepository` 的實作方式。

**建議**：確認 `TenantScopedRepository` 在 JPA query 層確實啟用了 `@Filter`。如果是透過 `@Query` 或 native query 實作，需手動加條件。

#### 3. [中等] `CreateDeptRequest.deptName` 缺少長度與格式限制

```java
public class CreateDeptRequest {
    @NotBlank
    private String deptName;
    private Long pid;
    private Integer deptSort;
}
```

**風險**：
- `@NotBlank` 但**沒有 `@Size`** → 可傳入超長字串（DB 限制 100 字元，會觸發 DB error 而非優雅 400）
- 沒有 `@Pattern` → 可包含特殊字元（HTML/script），如果前端渲染時未跳脫會有 XSS 風險

**建議**：
```java
@NotBlank
@Size(max = 100)
@Pattern(regexp = "^[\\p{L}\\p{N}\\s\\-_()（）]+$", message = "部門名稱含不允許的字元")
private String deptName;
```

#### 4. [中等] `UpdateDeptRequest` 缺少 `deptName` 長度限制

與 #3 相同問題。`deptName` 在 Update 中是 optional，但若有值應驗證。

**建議**：加 `@Size(max = 100)` 和 `@Pattern`（可為 null 時搭配不加 `@NotBlank`）。

#### 5. [中等] `UpdateDeptRequest.status` 無值域限制

```java
private Short status;
```

**風險**：可傳入任意 Short 值（如 -1、99），DB 不會拒絕。

**建議**：加 `@Min(0) @Max(1)` 或使用 `@Pattern` 限制。

#### 6. [低] `DataScopeHelper.getVisibleDeptIds()` 查詢全部部門做記憶體過濾

```java
List<DeptInfoEntity> all = deptInfoRepository.findAllByStatusOrderByDeptSortAsc((short) 1);
return all.stream()
        .filter(d -> d.getHierarchyPath() != null && d.getHierarchyPath().startsWith(hierarchyPath))
        .collect(Collectors.toList());
```

**問題**：每次呼叫都查詢**全部啟用部門**再做 in-memory filter。部門數量多時有效能問題。

**建議**：新增 Repository 方法用 LIKE 查詢：
```java
@Query("SELECT d FROM DeptInfoEntity d WHERE d.hierarchyPath LIKE :prefix%")
List<DeptInfoEntity> findByHierarchyPathStartingWith(@Param("prefix") String prefix);
```

#### 7. [低] `deleteDept()` 中查詢所有 mapping 做記憶體過濾

```java
List<String> blockingUserIds = userTenantMappingRepository.findByTenantIdAndEnabledTrue(entity.getTenantId())
        .stream()
        .filter(m -> deptId.equals(m.getDeptId()))
        .map(m -> m.getUserId())
        .collect(Collectors.toList());
```

**問題**：查詢**該租戶所有** enabled mapping，再 filter 出特定 deptId。資料量大時低效。

**建議**：新增 `existsByTenantIdAndDeptIdAndEnabledTrue(String tenantId, Long deptId)` 或 `findByTenantIdAndDeptIdAndEnabledTrue()`。

#### 8. [低] `createDept()` 兩次 save — hierarchy_path 需要自身 ID

```java
DeptInfoEntity saved = deptInfoRepository.save(entity);
saved.setHierarchyPath(parentPath + saved.getDeptId() + "/");
saved = deptInfoRepository.save(saved);
```

**評估**：這是因為 `hierarchy_path` 包含自己的 `dept_id`（GeneratedValue），必須先 save 取得 ID 再更新。設計上合理，但可考慮以下替代：
- 使用 `@PostPersist` 回調
- 或使用 UUID 作為 dept 識別符（不需要先 save）

**建議**：目前方案可接受，不需強制修改。

---

## 程式碼品質審查（Code Review）

### ✅ 優點

#### 1. `DataPermissionAspect` — AOP 資料範圍控制設計乾淨

```java
@Around("@annotation(dataPermission)")
public Object enforce(ProceedingJoinPoint pjp, DataPermission dataPermission) throws Throwable {
    // ...
    try {
        return pjp.proceed();
    } finally {
        DataScopeContext.clear();  // ← 確保 ThreadLocal 一定清除
    }
}
```

- **ThreadLocal 清理在 finally 中**：即使業務拋例外也不會汙染其他請求
- **null UserInfo → -1 不可能條件**：嚴格模式，無使用者資訊時不回傳任何資料
- `DataScopeFilter` 作為不可變 Value Object，搭配 Builder pattern

#### 2. `hierarchy_path` 設計 — 空間換時間

- `/1/2/3/` 格式支援 `LIKE '/1/%'` 查詢所有子代
- 避免遞迴 CTE 或多次 DB round-trip
- 建立時自動計算（parent path + self ID + "/"）

#### 3. 刪除前完整性檢查

```java
if (deptInfoRepository.existsByPid(deptId)) {
    throw DEPT_HAS_CHILDREN;
}
// ... check active users ...
userTenantMappingRepository.clearDeptIdByTenantIdAndDeptId(...);
deptInfoRepository.delete(entity);
```

- 先檢查子部門 → 再檢查使用者 → 清除引用 → 刪除
- 使用者檢查排除 soft-deleted 使用者（不因已刪除帳號阻擋操作）

#### 4. `DataScopeEnum.fromString()` 防禦性設計

```java
public static DataScopeEnum fromString(String value) {
    if (value == null) return ALL;
    try { return valueOf(value); }
    catch (IllegalArgumentException e) { return ALL; }
}
```

無效值降級為 ALL（最寬鬆），不會因為 enum 不匹配導致 500。

#### 5. 前端 `deptStore` — 扁平化 Map 快速查詢

```typescript
function flattenToMap(nodes: DeptOptionVO[]): Map<number, string> { ... }
```

遞迴遍歷樹形結構建立 `deptId → deptName` 的 flat Map，`getDeptName()` O(1) 查詢。

#### 6. 測試覆蓋完整

- `DeptServiceTest`：14 個場景含 CRUD + 刪除含使用者/子部門情境
- `DataScopeHelperTest`：8 個場景含 ALL/THIS_LEVEL/THIS_LEVEL_AND_BELOW/null
- `DataPermissionAspectTest`：7 個場景含 exception 時的 ThreadLocal 清理
- `DeptControllerTest`：8 個場景含 auth/authz/404

---

### 需要改進的問題

#### 9. [中等] `DeptDto.children` 欄位在非樹形回應中仍包含（null 序列化）

`DeptDto` 的 `children` 在 `getDeptById()` 時為 null，如果 `BaseResponse` 設定了 `@JsonInclude(NON_NULL)` 則不影響。但 `DeptDto` 本身未加此註解。

**建議**：在 `DeptDto` 加 `@JsonInclude(JsonInclude.Include.NON_NULL)` 避免回傳無意義的 `"children": null`。

#### 10. [低] `DataScopeEnum.fromString()` 無效值降級為 ALL

**風險**：如果使用者 JWT 中的 `dataScope` 被竄改為無效值（如 `"BYPASS"`），會降級為 ALL（最寬鬆權限）。

**設計權衡**：
- 降級為 ALL = 可用性優先（不阻斷服務）
- 如果改為嚴格模式（無效值 → 拒絕）= 安全優先

**建議**：因為 `dataScope` 來自 JWT（已驗簽），正常情況下不會出現無效值。保持現狀可接受，但建議加 warn 日誌記錄異常值。

#### 11. [低] `DeptController` 的讀取端點無權限控制

```java
@GetMapping("/list")       // 無 @PreAuthorize
@GetMapping("/options")    // 無 @PreAuthorize
@GetMapping("/scope-options")  // 無 @PreAuthorize
@GetMapping("/{deptId}")   // 無 @PreAuthorize
```

**評估**：讀取部門資訊通常是**通用權限**（所有登入使用者都需要看部門下拉選單），所以不加 `@PreAuthorize` 是合理的。但 `/list` 端點如果僅供「部門管理」頁面使用，可加 `hasAuthority('DEPT_LIST')`。

**建議**：視業務需求決定。目前 migration V18 已從 DEPT_USER 移除了 `DEPT_LIST` 權限，前端應該透過 menu 權限控制頁面可見性。

---

## 架構總評

| 維度 | 評分 | 說明 |
|------|------|------|
| 安全性 | **8.5/10** | 租戶隔離完整、權限控管到位、DataScope AOP 設計好。但輸入驗證不夠嚴格（deptName 無 Size/Pattern），查詢端點缺少 DataScope 過濾（如果業務需要）。 |
| 正確性 | **9/10** | 樹形建構邏輯正確、hierarchy_path 一致、刪除前檢查完整。兩次 save 雖非理想但正確。 |
| 效能 | **8/10** | `getVisibleDeptIds()` 和 `deleteDept()` 都走全量查詢 + 記憶體過濾。部門數量小時可忽略，但大量部門時需優化。 |
| 可維護性 | **9/10** | 分層清晰（Controller→Service→Repository）、DTOs 獨立、DataScope 機制可複用。 |
| 可測試性 | **9.5/10** | 4 個測試檔案覆蓋完整，含 ThreadLocal 清理驗證、mockStatic 正確使用。 |

---

## 建議優先級摘要

| 優先級 | 項目 | 類型 | 狀態 |
|--------|------|------|------|
| **P2** | `CreateDeptRequest.deptName` 加 `@Size(max=100)` | Security / Validation | ✅ 已修正 |
| **P2** | `UpdateDeptRequest.deptName` 加 `@Size(max=100)` | Security / Validation | ✅ 已修正 |
| **P2** | `UpdateDeptRequest.status` 加值域限制 | Security / Validation | ✅ 已修正 |
| **P3** | 決策：`/list` 和 `/options` 不需 DataScope 過濾（部門樹為公開資訊） | Architecture | ✅ 已決策 |
| **P3** | 確認 `findByDeptId()` 受 `@Filter(tenantFilter)` 保護 | Security | ✅ 已確認安全（TenantFilterAspect AOP 自動啟用） |
| **P4** | `DataScopeHelper.getVisibleDeptIds()` 改用 DB LIKE 查詢 | Performance | ✅ 已修正 |
| **P4** | `deleteDept()` 改用精確 Repository 方法取代全量查詢 | Performance | ✅ 已修正 |
| **P4** | `DeptDto` 加 `@JsonInclude(NON_NULL)` | Maintainability | ✅ 已修正 |
| **P5** | `DataScopeEnum.fromString()` 無效值加 warn 日誌 | Observability | ✅ 已修正 |
