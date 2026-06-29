# Phase B：Impersonation + getMyMenus 改寫 + 審計追蹤

> Phase A 已完成（2026-05-29）。Phase B 已完成（2026-05-29）：1072 backend tests + 244 frontend tests pass。

---

## 1. 現況分析

### 1.1 TenantContext（目前）

```java
public final class TenantContext {
    private static final ThreadLocal<String> CURRENT_TENANT = new ThreadLocal<>();
    private static final String SYSTEM_TENANT_MARKER = "SYSTEM";

    // 方法：getCurrentTenantId / setCurrentTenantId / clear
    //       setSystemContext / isSystemContext
    //       runInSystemContext(Runnable) / runInSystemContext(Supplier<T>)
}
```

- 只有一個 `ThreadLocal<String>`，值為 tenant ID 或 `"SYSTEM"`
- **無 impersonation 概念**：不知道「誰在代操」

### 1.2 JwtAuthenticationFilter 的 tenant 設定邏輯

```java
if (tenantId != null) {
    TenantContext.setCurrentTenantId(tenantId);   // 包含 SUPER_ADMIN select-tenant 後
} else if (roles.contains("SUPER_ADMIN")) {
    TenantContext.setSystemContext();              // SUPER_ADMIN 未選 tenant
}
```

### 1.3 TenantFilterAspect

```java
@Before("execution(* com.taipei.iot..*.repository..*Repository.*(..))")
public void enableTenantFilter(JoinPoint jp) {
    if (!(jp.getThis() instanceof TenantScopedRepository)) return;
    if (TenantContext.isSystemContext()) return;   // ← SUPER_ADMIN bypass 入口
    // ... enable Hibernate filter with tenantId
}
```

- `isSystemContext()` 時完全 bypass，不套 tenant filter
- SUPER_ADMIN 選定 tenant 後已走 `setCurrentTenantId()` → filter 正常套用 ✓

### 1.4 MenuService.getMyMenus()

```java
if (roleIds.stream().anyMatch(r -> r.equals("ROLE_SUPER_ADMIN"))) {
    // 回傳所有 visible 菜單，忽略 scope/permission
    return buildUserMenuTree(menuRepository.findAllByOrderBySortOrder()
        .stream().filter(m -> Boolean.TRUE.equals(m.getVisible())).collect(...));
}
```

- SUPER_ADMIN 看到所有 scope 的菜單，包括不屬於當前 tenant 的項目

### 1.5 審計表現狀

| 表名 | impersonated_by | 說明 |
|------|:---:|------|
| `user_event_log` | ❌ | API 操作日誌 |
| `user_info_log` | ❌ | 使用者異動日誌 |
| `rev_info`（Envers） | ❌ | 版本控制 |

---

## 2. 設計目標

1. SUPER_ADMIN 選定 tenant 後進入 **impersonation 模式**：tenant filter 正常套用，audit 記錄代操者
2. `getMyMenus` 依 `menus.scope` 回傳正確菜單（不再 bypass all）
3. 所有代操操作留下 `impersonated_by` 審計欄位
4. 非 SUPER_ADMIN 永遠不可能進入 systemContext（防呆）

---

## 3. 詳細設計

### 3.1 TenantContext 擴展

```java
public final class TenantContext {
    private static final ThreadLocal<String> CURRENT_TENANT = new ThreadLocal<>();
    private static final ThreadLocal<String> IMPERSONATOR = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> TRUSTED_SYSTEM_CONTEXT = new ThreadLocal<>();
    private static final String SYSTEM_TENANT_MARKER = "SYSTEM";

    // Impersonator
    public static void setImpersonator(String userId) { IMPERSONATOR.set(userId); }
    public static String getImpersonator()            { return IMPERSONATOR.get(); }
    public static boolean isImpersonating()           { return IMPERSONATOR.get() != null; }

    // Trusted system context：區分 runInSystemContext（程式碼合法呼叫）vs 直接 setSystemContext
    public static boolean isTrustedSystemContext()    { return Boolean.TRUE.equals(TRUSTED_SYSTEM_CONTEXT.get()); }

    public static void clear() {
        CURRENT_TENANT.remove();
        IMPERSONATOR.remove();
        TRUSTED_SYSTEM_CONTEXT.remove();
    }

    // runInSystemContext: save/restore tenant + impersonator + trusted flag
    public static <T> T runInSystemContext(Supplier<T> action) {
        String previousTenant = CURRENT_TENANT.get();
        String previousImpersonator = IMPERSONATOR.get();
        Boolean previousTrusted = TRUSTED_SYSTEM_CONTEXT.get();
        try {
            setSystemContext();
            TRUSTED_SYSTEM_CONTEXT.set(true);   // 標記為程式碼層級的合法呼叫
            IMPERSONATOR.remove();              // SYSTEM 操作不算代操
            return action.get();
        } finally {
            if (previousTenant != null) CURRENT_TENANT.set(previousTenant);
            else CURRENT_TENANT.remove();
            if (previousImpersonator != null) IMPERSONATOR.set(previousImpersonator);
            else IMPERSONATOR.remove();
            if (previousTrusted != null) TRUSTED_SYSTEM_CONTEXT.set(previousTrusted);
            else TRUSTED_SYSTEM_CONTEXT.remove();
        }
    }
}
```

### 3.2 JwtAuthenticationFilter 調整

```java
if (tenantId != null) {
    TenantContext.setCurrentTenantId(tenantId);
    if (roles.contains("SUPER_ADMIN")) {
        TenantContext.setImpersonator(userId);  // NEW: 標記這是代操
    }
} else if (roles.contains("SUPER_ADMIN")) {
    TenantContext.setSystemContext();
}
```

- SUPER_ADMIN + 有 tenantId → 正常 tenant context + impersonator 標記
- SUPER_ADMIN + 無 tenantId → systemContext（平台管理模式）
- 一般使用者 → 只設 tenantId，無 impersonator

### 3.3 TenantFilterAspect 收緊（實質防呆 + trusted context）

```java
@Before("execution(* com.taipei.iot..*.repository..*Repository.*(..))")
public void enableTenantFilter(JoinPoint jp) {
    if (!(jp.getThis() instanceof TenantScopedRepository)) return;

    if (TenantContext.isSystemContext()) {
        // [Phase B] 安全交叉檢查
        // - trusted（經由 runInSystemContext 進入）：程式碼層級的合法 cross-tenant 操作，直接放行
        //   例：登入流程查 tenant mappings、@RunInSystemTenantContext 排程任務
        // - 非 trusted（直接 setSystemContext）：僅限 SUPER_ADMIN 或無 Authentication（排程初始設定）
        if (!TenantContext.isTrustedSystemContext()) {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated()) {
                boolean isSuperAdmin = auth.getAuthorities().stream()
                        .anyMatch(a -> "ROLE_SUPER_ADMIN".equals(a.getAuthority()));
                if (!isSuperAdmin) {
                    throw new IllegalStateException(
                        "SystemContext detected for non-SUPER_ADMIN authentication. "
                        + "Possible security bypass attempt.");
                }
            }
        }
        return;
    }

    String tenantId = TenantContext.getCurrentTenantId();
    if (tenantId == null) {
        throw new IllegalStateException(
            "TenantContext is not set. All repository operations require a tenant context.");
    }
    Session session = entityManager.unwrap(Session.class);
    session.enableFilter("tenantFilter").setParameter("tenantId", tenantId);
}
```

**三層判定邏輯**：

| 條件 | 行為 | 場景 |
|------|------|------|
| trusted（`runInSystemContext`）| 直接放行 | 登入查 tenant mappings、`@RunInSystemTenantContext` 排程/async |
| 非 trusted + SUPER_ADMIN auth | 放行 | `JwtAuthenticationFilter` 直接 `setSystemContext()`（SUPER_ADMIN 未選 tenant） |
| 非 trusted + 非 SUPER_ADMIN auth | 拋 `IllegalStateException` | 安全繞過攻擊 |
| 非 trusted + auth == null | 放行 | 排程初始設定（無 HTTP 請求） |

> **⚠️ 實作教訓（Hotfix）**：初版只用 auth 是否為 SUPER_ADMIN 判斷，缺少 trusted flag。
> 登入流程 `AuthServiceImpl.login()` 先認證使用者（SecurityContext 已有非 SUPER_ADMIN 的 Authentication），
> 再呼叫 `runInSystemContext` 查跨租戶 tenant mappings → 被誤判為安全繞過，導致所有帳號無法登入。
> 修正：加入 `TRUSTED_SYSTEM_CONTEXT` ThreadLocal，`runInSystemContext` 進入時標記 trusted，
> `TenantFilterAspect` 對 trusted 路徑不做 SUPER_ADMIN 檢查。

### 3.4 getMyMenus 改寫（scope-aware，保留 DIRECTORY 父節點邏輯）

```java
public List<UserMenuDto> getMyMenus(List<String> roleIds, String tenantId) {
    boolean isSuperAdmin = roleIds.contains("ROLE_SUPER_ADMIN");
    boolean inTenantContext = tenantId != null;

    List<String> allowedScopes;
    if (isSuperAdmin && !inTenantContext) {
        allowedScopes = List.of("PLATFORM", "PUBLIC");           // 平台管理模式
    } else {
        allowedScopes = List.of("TENANT", "PUBLIC");             // 代操模式 & 一般使用者
    }

    List<MenuEntity> candidates = menuRepository.findByScopeInAndVisibleTrue(allowedScopes);

    List<MenuEntity> result;
    if (isSuperAdmin) {
        // SUPER_ADMIN auto-grant 所有 permissions，scope 過濾後直接全收
        result = new ArrayList<>(candidates);
    } else {
        // 一般使用者：permission 過濾 (PAGE) + public PAGE
        List<String> permCodes = permissionRepository.findCodesByRoleIdsAndTenant(roleIds, tenantId);
        result = candidates.stream()
            .filter(m -> !"DIRECTORY".equals(m.getMenuType()))   // PAGE only first
            .filter(m -> m.getPermissionCode() == null || permCodes.contains(m.getPermissionCode()))
            .collect(Collectors.toCollection(ArrayList::new));

        // 補上有 visible child 的 DIRECTORY 父節點（保留現行邏輯）
        Set<Long> resultIds = result.stream().map(MenuEntity::getMenuId).collect(Collectors.toSet());
        for (MenuEntity m : candidates) {
            if ("DIRECTORY".equals(m.getMenuType())
                    && !resultIds.contains(m.getMenuId())
                    && hasVisibleChild(m.getMenuId(), result)) {
                result.add(m);
            }
        }
    }
    return buildUserMenuTree(result);
}
```

### 3.5 V60 Migration：審計表加 impersonated_by

**語意明確化**：
- `NULL` = 一般使用者正常操作（多數情況）
- `NOT NULL` = SUPER_ADMIN 在 tenant context 下執行（值 = SUPER_ADMIN 的 userId，與 `user_id` 相同）
- 查詢範例：`WHERE tenant_id = 'X' AND impersonated_by IS NOT NULL` → 撈出租戶 X 內所有平台代操紀錄，無需 JOIN users

```sql
-- V60__audit__add_impersonated_by.sql
ALTER TABLE user_event_log ADD COLUMN IF NOT EXISTS impersonated_by VARCHAR(50);
ALTER TABLE user_info_log  ADD COLUMN IF NOT EXISTS impersonated_by VARCHAR(50);

COMMENT ON COLUMN user_event_log.impersonated_by IS
    'NULL=一般操作；NOT NULL=SUPER_ADMIN 在租戶 context 下執行，值為 SUPER_ADMIN userId';
COMMENT ON COLUMN user_info_log.impersonated_by IS
    'NULL=一般操作；NOT NULL=SUPER_ADMIN 在租戶 context 下執行，值為 SUPER_ADMIN userId';

-- 查詢便利：建立部分索引（只索引非 NULL 行）
CREATE INDEX IF NOT EXISTS idx_user_event_log_impersonated
    ON user_event_log(tenant_id, impersonated_by) WHERE impersonated_by IS NOT NULL;
```

### 3.6 審計寫入注入 impersonated_by

**BaseLoggerAspect**（寫入 `user_event_log`）：
```java
if (TenantContext.isImpersonating()) {
    eventLog.setImpersonatedBy(TenantContext.getImpersonator());
}
```

**UserAuditService**（寫入 `user_info_log`）：
```java
if (TenantContext.isImpersonating()) {
    infoLog.setImpersonatedBy(TenantContext.getImpersonator());
}
```

### 3.7 前端適配

- `AppSidebar.vue`：已由 API 驅動，無需改動
- **審計歷史頁**：表格新增「代操者」欄位（`impersonatedBy` 有值時顯示「平台代操」標記 + userId）
- ~~Router guard~~：取消。`authStore.ts` 已強制 SUPER_ADMIN 登入後自動 select tenant（`lastTenantId` 或 `tenants[0]`），「未選 tenant」狀態實務上不會發生 → 改為單元測試確認此 invariant 不被破壞

---

## 4. 執行任務清單

| # | Task | 依賴 | 預估影響範圍 | 狀態 |
|---|------|------|-------------|------|
| 1 | TenantContext 加 IMPERSONATOR + TRUSTED_SYSTEM_CONTEXT + runInSystemContext 一併處理 | — | `TenantContext.java` | ✅ |
| 2 | JwtAuthenticationFilter 設定 impersonator | T1 | `JwtAuthenticationFilter.java` | ✅ |
| 3 | TenantFilterAspect 加 SecurityContext 交叉驗證 + trusted context 判斷 | T1 | `TenantFilterAspect.java` | ✅ |
| 4 | V60 migration：audit 表加欄位 + 部分索引 | — | 新 migration file | ✅ |
| 5 | Entity/DTO 加 impersonatedBy 欄位 | T4 | `UserEventLogEntity`, `UserInfoLogEntity`, DTOs | ✅ |
| 6 | BaseLoggerAspect + AuditAsyncWriter 注入 impersonated_by | T1, T5 | `BaseLoggerAspect.java`, `AuditAsyncWriter.java` | ✅ |
| 7 | UserAuditService 注入 impersonated_by | T1, T5 | `UserAuditService.java` | ✅ |
| 8 | getMyMenus scope-aware 改寫（保留 DIRECTORY 邏輯） | — | `MenuService.java`, `MenuRepository.java` | ✅ |
| 9 | 前端審計頁顯示代操者欄 + i18n | T4, T5 | `AuditHistoryView.vue`, `AuditTable.vue`, DTO types, locales | ✅ |
| ~~10~~ | ~~router guard~~ → 取消 | — | — | — |
| 11 | 後端測試（含 runInSystemContext 巢狀、trusted context、scope-aware menus） | T1–T8 | 新增 + 修改 test files | ✅ |
| 12 | 前端測試 | T9 | vitest | ✅ |

### 依賴圖

```
T1 ──┬──► T2 ──► T3
     │
     ├──► T6 ◄── T5 ◄── T4 (可平行 T1)
     │
     └──► T7 ◄── T5
     
T8 (獨立)

T9 ◄── T5
T10 ◄── T8

T11 ◄── T1–T8
T12 ◄── T9, T10
```

### 可平行啟動
- **T1 + T4 + T8** 三者互不依賴

---

## 5. 風險與決策點

| # | 議題 | 建議 |
|---|------|------|
| 1 | SUPER_ADMIN 代操租戶後，審計的 `user_id` 填誰？ | 填 SUPER_ADMIN 本人（action_user_id），`impersonated_by` 也填本人 → 明確代操。或可考慮 `action_user_id` 填目標 tenant 的虛擬身份？建議維持填本人 + impersonated_by 作為標記。 |
| 2 | Envers `rev_info` 是否也加 `impersonated_by`？ | 低優先。Envers 表主要用於資料回溯，非稽核展示。Phase C 再議。 |
| 3 | 前端未選 tenant 時，PLATFORM scope API 如何路由？ | 平台管理 API（`/v1/admin/tenants`）不需要 tenantId，直接在 systemContext 執行。已有的 `/platform/*` 路由均為平台級 API。 |
| 4 | `select-tenant` API 是否需要改動？ | 不需要。現有 `POST /auth/select-tenant` 已正確地在新 JWT 中寫入 `tenantId`，Phase B 只在 filter 層讀取並標記 impersonator。 |

---

## 6. Phase A → Phase B 銜接確認

Phase A 已完成的基礎設施：
- ✅ `menus.scope` 欄位 + CHECK + index
- ✅ `MenuRepository.findByScopeAndVisibleTrue(String scope)`
- ✅ `MenuEntity.scope` / `MenuDto.scope` / `UserMenuDto.scope`
- ✅ PLATFORM/TENANT/PUBLIC 分類已 seed（V59）
- ✅ 權限碼 `PLATFORM_IMPERSONATE` 已預埋（V58，目前未使用）
- ✅ AppSidebar 已改為 API 驅動

Phase B 已完成：
- ✅ `MenuRepository.findByScopeInAndVisibleTrue(List<String> scopes)`
- ✅ `TenantContext.impersonator` + `TRUSTED_SYSTEM_CONTEXT` 基礎設施
- ✅ V60 migration（`impersonated_by` 欄位 + partial index）
- ✅ 審計層全鏈路注入（`BaseLoggerAspect` → `AuditAsyncWriter` 13th param → Entity）
- ✅ `TenantFilterAspect` trusted context 判斷（修復登入流程誤判問題）
- ✅ 前端 `AuditHistoryView` / `AuditTable` 代操者欄位 + i18n（en/zh-TW/zh-CN）
- ✅ 後端 1072 tests pass / 前端 244 tests pass
