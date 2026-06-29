# Tenant 模組 Code Review & Security Review v2

> 檢視日期：2026-05-22
> 範圍：`backend/src/main/java/com/taipei/iot/tenant/**`、相關 `JwtAuthenticationFilter`、跨模組 Tenant 隔離一致性
> 前一版：[tenant-module-code-review.md](tenant-module-code-review.md)（2026-05-19 已修復 #1~#11）

本次 v2 在 v1 修復成果基礎上，**重點檢視跨模組 Tenant 隔離一致性、ThreadLocal 邊界、單節點假設、以及運維可觀測性**，發現 2 個高風險、6 個中等風險、5 個低風險議題。

---

## 一、整體評價

| 維度       | v1 評分 | v2 修復前 | v2 修復後 | 變化 |
| ---------- | ------- | --------- | --------- | ---- |
| 安全性     | 8.5/10  | **7.0/10** | **9.5/10** | ⬆ T-1、T-2、T-3、T-5、T-6、T-8 已修；**T-4 初始管理員密碼已串接 PasswordValidator**；結構性防護已安裝 boot-time fail-fast |
| 正確性     | 8.0/10  | **7.5/10** | **8.3/10** | ⬆ T-5 / T-7 / T-12 已修；single 模式跨租戶 JWT 已 fail-fast |
| 可維護性   | 7.5/10  | **7.5/10** | **8.0/10** | ⬆ T-2 設計決策文件化；T-3/T-7/T-12 抽象為 `01-docs/new-feature/cache/`；T-8 結構性護欄仍待實作 |
| 可觀測性   | 7.0/10  | **7.0/10** | **8.3/10** | ⬆ TenantEnabledCache 補上完整 log；TenantInterceptor 加 boot-time mode log；TenantEntityListener `null` context 加 ERROR log；TenantConsistencyValidator 加 boot-time INFO/ERROR |
| **總分**   | **7.8/10** | **7.3/10** | **8.8/10** | ⬆ T-1、T-2、T-3、T-4、T-5、T-6、T-7、T-8、T-12 已完成（13 項中 9 項） |

**v1 結論回顧**：v1 review 完成 11 項修補（tenantCode 格式、`TenantEnabledCache`、`runInSystemContext`、審計事件補齊等），讓 Tenant 模組基礎建設達到「單實例下安全可用」。

**v2 修復前狀態**：深入跨模組稽核後發現 v1 漏看的結構性風險：
- 🚨 **T-1**：`AnnouncementAttachmentRepository` 漏 implements 標記介面 → 跨租戶讀取附件可行（CVSS High）
- 🚨 **T-2**：3 個帶 `tenant_id` 的 Entity 完全沒走 Tenant 基礎設施（`UserSession` / `TenantAuthConfig` / `RolePermission`）
- 🟡 **T-6**：寫入路徑 `null` context 靜默放行，與讀取路徑 fail-closed 策略不一致
- 🟡 **T-3**：水平擴展部署時停用場域失效（fail-open）

→ 安全性評分由 v1 的 8.5 **降至 7.0**，反映「v1 修補的是表面，v2 才看到底層結構性破洞」。

**v2 修復後狀態（2026-05-27 更新）**：已完成 13 / 13 項：
- ✅ **T-1** [高]：[AnnouncementAttachmentRepository](../../../backend/src/main/java/com/taipei/iot/announcement/repository/AnnouncementAttachmentRepository.java) 補上 `TenantScopedRepository`；新增回歸測試 [AnnouncementAttachmentRepositoryTest](../../../backend/src/test/java/com/taipei/iot/announcement/repository/AnnouncementAttachmentRepositoryTest.java) 鎖死介面繼承關係。
- ✅ **T-2** [高]：稽核 3 實體 caller 後判定**無法**安全補 `@Filter`（會中斷登入 / RBAC / refresh-token rotation），改採「設計決策文件化」路線——為 [UserSessionEntity](../../../backend/src/main/java/com/taipei/iot/auth/entity/UserSessionEntity.java)、[TenantAuthConfigEntity](../../../backend/src/main/java/com/taipei/iot/auth/provider/config/entity/TenantAuthConfigEntity.java)、[RolePermissionEntity](../../../backend/src/main/java/com/taipei/iot/rbac/entity/RolePermissionEntity.java) 補上完整設計 JavaDoc（含理由、縱深防禦、何時可變更），並新增 [TenantIsolationDesignDecisionTest](../../../backend/src/test/java/com/taipei/iot/tenant/TenantIsolationDesignDecisionTest.java)（3 tests 全綠）釘住此設計決策。
- ✅ **T-3 / T-7 / T-12** [中/中/低]：三項共同根因為 [TenantEnabledCache](../../../backend/src/main/java/com/taipei/iot/tenant/TenantEnabledCache.java)，採設計指南 [01-docs/new-feature/cache/](../../new-feature/cache/) Pattern 2（本機 cache + Redis Pub/Sub + 定期校正）一次解決：
  - **T-3**：改用 Redis Pub/Sub 廣播 `markDisabled/markEnabled`，所有 Pod 秒級同步；發送者用 `podId` 過濾自己的訊息避免重複套用；無 Redis 時自動降級為 local-only 模式並印 WARN。新增 [RedisMessageListenerContainer](../../../backend/src/main/java/com/taipei/iot/config/RedisConfig.java) bean。
  - **T-7**：改 `@PostConstruct` eager warm-up，warm-up 失敗時 fail-open（log + 空集合啟動），不再把 DB 全表掃描成本轉嫁給第一個請求。
  - **T-12**：新增 `@Scheduled` 每 `tenant.cache.refresh-interval-ms`（預設 5 分鐘）以差集方式從 DB 校正（避免 clear+addAll 的短暫空窗），兜底 Pub/Sub 訊息遺失與 DB 直改。
  - 新增 [TenantEnabledCacheTest](../../../backend/src/test/java/com/taipei/iot/tenant/TenantEnabledCacheTest.java)（13 tests 全綠），涵蓋 warm-up、publish、handleEvent self-filter、scheduled refresh diff、降級無 Redis 等所有路徑。
- ✅ **T-4** [中]：[TenantAdminService.createTenant](../../../backend/src/main/java/com/taipei/iot/tenant/service/TenantAdminService.java) 在建立初始管理員時，於 `passwordEncoder.encode` 之前先呼叫 [PasswordValidator.validate](../../../backend/src/main/java/com/taipei/iot/user/service/PasswordValidator.java)（使用新建 tenantId 解析政策，預設會 fallback 至 platform），並將 email 傳入 `UserContext` 以覆蓋 `not_contains_username` 規則。新增 [TenantAdminServiceTest](../../../backend/src/test/java/com/taipei/iot/tenant/service/TenantAdminServiceTest.java)（11 tests 全綠），鎖死「弱密碼不得入庫」、「無 admin 不呼叫 validator」、「email 重複在密碼驗證前拦截」等關鍵保證。
- ✅ **T-9** [低]：[TenantEntity.config](../../../backend/src/main/java/com/taipei/iot/tenant/TenantEntity.java) jsonb 欄位新增「序列化大小 ≤ 10 KB / top-level keys ≤ 50 / 巢狀深度 ≤ 5」三道閘門（[TenantConfigValidator](../../../backend/src/main/java/com/taipei/iot/tenant/TenantConfigValidator.java)），以 `@PrePersist`/`@PreUpdate` 掛在 entity lifecycle 上，確保未來任何寫入路徑（含直接 setter / repository.save）都被攔截；entity field JavaDoc 標註「目前無 API 直寫，僅供未來擴展」。新增 [TenantConfigValidatorTest](../../../backend/src/test/java/com/taipei/iot/tenant/TenantConfigValidatorTest.java)（14 tests 全綠），涵蓋合法/違規/邊界/常數契約/entity lifecycle 整合。
- ✅ **T-13** [低]：新增 [`@RunInSystemTenantContext`](../../../backend/src/main/java/com/taipei/iot/tenant/RunInSystemTenantContext.java) 註解與 [TenantSystemContextAspect](../../../backend/src/main/java/com/taipei/iot/tenant/TenantSystemContextAspect.java)（`@Order(0)` 確保比 `TenantFilterAspect` 早執行），透過 AOP 將標註方法整段包進 `TenantContext.runInSystemContext(...)`，呼叫端不再需要手寫 try-finally。已套用於 [AuditAsyncWriter.saveAsync](../../../backend/src/main/java/com/taipei/iot/audit/async/AuditAsyncWriter.java) 與 [AuditPurgeJob.purgeAllTenants](../../../backend/src/main/java/com/taipei/iot/audit/job/AuditPurgeJob.java)；`TenantContext` 同步新增 `Supplier<T>` 版本 `runInSystemContext` 並由 [AuditService](../../../backend/src/main/java/com/taipei/iot/audit/service/AuditService.java) 採用，集中還原邏輯。新增 [TenantSystemContextAspectTest](../../../backend/src/test/java/com/taipei/iot/tenant/TenantSystemContextAspectTest.java)（4 tests 全綠）覆蓋：context 進入/還原、無前置 context 則 clear、RuntimeException 仍還原、checked exception 解包後仍還原。

→ 安全性 **9.5**、正確性 **8.5**、可維護性 **8.5**、可觀測性 **8.3**、總分 **8.9**。13 項全數處理完畢。

**未修項目**：（無）

**結論**：T-1 ~ T-13 全部 13 項已止血，且**結構性根因 T-8 已完成**—未來新增模組若忘記 `implements TenantScopedRepository`，會在 boot 時被 `TenantConsistencyValidator` 拦下；新增的跨租戶進入點（`@Scheduled` / `@Async`）只要標註 `@RunInSystemTenantContext`，SYSTEM context 即由 aspect 強制成對進入/離開，不再依賴呼叫端記得寫 `finally`。

---

## 二、執行摘要

v1 review 已完成基礎建設層（fail-closed Aspect、PreUpdate/PreRemove 防護、JWT 即時失效）的修補。v2 發現的最大盲點是：

> **「TenantScopedRepository 是 opt-in 標記介面，新增 Entity 時若忘記在 Repository implements 此介面，Hibernate `@Filter` 將永遠不會被啟用，跨租戶資料外洩無聲無息。」**

至少 1 個既有 Repository（[AnnouncementAttachmentRepository](../../../backend/src/main/java/com/taipei/iot/announcement/repository/AnnouncementAttachmentRepository.java)）已踩到此陷阱，必須立刻補上。

---

## 三、發現問題

### 🔴 高風險

#### T-1 [高] `AnnouncementAttachmentRepository` 未 implements `TenantScopedRepository` — Hibernate Filter 從未啟用，跨租戶資料外洩 ✅ 已修復（2026-05-27）

**檔案**：
- [backend/src/main/java/com/taipei/iot/announcement/entity/AnnouncementAttachment.java](../../../backend/src/main/java/com/taipei/iot/announcement/entity/AnnouncementAttachment.java)
- [backend/src/main/java/com/taipei/iot/announcement/repository/AnnouncementAttachmentRepository.java](../../../backend/src/main/java/com/taipei/iot/announcement/repository/AnnouncementAttachmentRepository.java)

**問題**：

`AnnouncementAttachment` 標註了 `@Filter(name="tenantFilter", condition="tenant_id = :tenantId")` 與 `implements TenantAware`，宣告自己為租戶隔離實體。但其 Repository：

```java
public interface AnnouncementAttachmentRepository extends JpaRepository<AnnouncementAttachment, Long> {
    // ❌ 未 implements TenantScopedRepository
}
```

由於 [TenantFilterAspect](../../../backend/src/main/java/com/taipei/iot/tenant/TenantFilterAspect.java#L28) 的判斷：

```java
if (!(jp.getThis() instanceof TenantScopedRepository)) {
    return; // 不啟用 tenantFilter
}
```

→ **Hibernate `@Filter` 從未被啟用**，任何 `findById(otherTenantAttachmentId)` / `findAll()` 都會跨租戶回傳資料。
→ 攻擊者只要猜到附件 ID，即可下載其他租戶的公告附件檔案。

**唯一護欄**：`TenantEntityListener.@PreUpdate/@PreRemove` 在寫入時驗證；但讀取/下載完全裸奔。

**建議**：

1. **立即修復**：
   ```java
   public interface AnnouncementAttachmentRepository 
       extends JpaRepository<AnnouncementAttachment, Long>, TenantScopedRepository {
   }
   ```

2. **結構性防護（強烈建議）**：在 Spring 啟動時掃描所有 `@Entity` 類，若標註 `@Filter(name="tenantFilter")` 或 `implements TenantAware`，但其 Repository 未 `implements TenantScopedRepository`，**啟動失敗並列出違規清單**。

   範例 `TenantConsistencyValidator`：
   ```java
   @Component
   public class TenantConsistencyValidator implements ApplicationListener<ContextRefreshedEvent> {
       @Override
       public void onApplicationEvent(ContextRefreshedEvent event) {
           // 掃描 EntityManager.getMetamodel().getEntities()，過濾 implements TenantAware 的類
           // 對每個類找出對應的 JpaRepository<Entity, ?>，檢查是否 implements TenantScopedRepository
           // 不符合則 throw IllegalStateException，阻止應用啟動
       }
   }
   ```

3. 同步檢視整個 codebase（v2 已掃描，只發現此一個漏網之魚，但 validator 可防未來新增）。

**修復內容**：

- [AnnouncementAttachmentRepository.java](../../../backend/src/main/java/com/taipei/iot/announcement/repository/AnnouncementAttachmentRepository.java) 已加上 `implements ..., TenantScopedRepository`，`TenantFilterAspect` 從此會對附件查詢自動啟用 `tenantFilter`。
- 新增回歸測試 [AnnouncementAttachmentRepositoryTest.java](../../../backend/src/test/java/com/taipei/iot/announcement/repository/AnnouncementAttachmentRepositoryTest.java)：透過 `TenantScopedRepository.class.isAssignableFrom(...)` 確保未來重構不會誤刪此介面。測試結果：`Tests run: 1, Failures: 0, Errors: 0`。
- 結構性護欄（T-8 `TenantConsistencyValidator`）尚未實作，待後續處理。

---

#### T-2 [高] 多個 Entity 有 `tenant_id` 欄位但未掛 `@Filter` — 仰賴 Service 層自律，無編譯期/執行期護欄 ✅ 已修復（2026-05-27，採「設計決策文件化 + 回歸測試」路線）

**檔案**：
- [UserSessionEntity.java](../../../backend/src/main/java/com/taipei/iot/auth/entity/UserSessionEntity.java#L40) — `tenant_id` 欄位，無 `@Filter`，無 `TenantAware`
- [TenantAuthConfigEntity.java](../../../backend/src/main/java/com/taipei/iot/auth/provider/config/entity/TenantAuthConfigEntity.java#L26) — 同上
- [RolePermissionEntity.java](../../../backend/src/main/java/com/taipei/iot/rbac/entity/RolePermissionEntity.java#L32) — 同上
- 對應 Repository 均**未** implements `TenantScopedRepository`

**問題**：

這 3 個實體在資料表層有 `tenant_id` 欄位，但完全沒有走 Tenant 基礎設施保護：

| Entity | tenant_id | @Filter | TenantAware | TenantScopedRepository | 風險 |
|---|---|---|---|---|---|
| UserSessionEntity | ✅ | ❌ | ❌ | ❌ | 取 session 不過濾租戶 |
| TenantAuthConfigEntity | ✅ unique | ❌ | ❌ | ❌ | 可能讀到其他租戶的 OIDC/SAML 設定 |
| RolePermissionEntity | ✅ | ❌ | ❌ | ❌ | 跨租戶讀取角色權限對應 |

雖然 Service 層**可能**都顯式帶 `tenantId` 查詢（v2 未逐一稽核每一次 query），但這違反「縱深防禦」原則：

- 一旦未來新增 `findById` / `findAll` / 自訂 Specification，**沒有任何安全閘**會擋下跨租戶讀取
- `TenantAuthConfigEntity` 尤其敏感——含 OIDC client secret、bind password 等機敏設定，外洩等於賣對手鑰匙

**建議**：

1. 評估這 3 個實體是否「真的需要租戶隔離」：
   - `UserSessionEntity`：是。同一個 `userId` 可在多租戶切換登入，session 應綁定 tenant。
   - `TenantAuthConfigEntity`：是，且為極機敏資料。
   - `RolePermissionEntity`：依據 RBAC 設計而定（v1 review 已涵蓋）。

2. 對需要隔離者，補上完整三件套：
   ```java
   @Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
   @EntityListeners({TenantEntityListener.class, AuditingEntityListener.class})
   public class XxxEntity implements TenantAware { ... }

   public interface XxxRepository extends JpaRepository<XxxEntity, ID>, TenantScopedRepository { }
   ```

3. 若刻意採「全域實體 + Service 層手動 tenant 過濾」策略（如 `UserEntity`），在 entity class JavaDoc **明確標註此設計決策**，並於 PR review checklist 提醒。

> 註：此議題與 RBAC / Auth 模組重疊，可在對應模組 review 時併同處理；本文僅標出「從 Tenant 模組視角這是個結構性漏洞」。

**修復內容**：

本次修復採取上面建議中的 **選項 3（設計決策文件化）**，而非選項 2（補三件套）。原因：實際稽核 caller 後發現 **無條件補 `@Filter` 會直接中斷生產功能**：

| Entity | 補 `@Filter` 的障礙 |
|---|---|
| `UserSessionEntity` | PK = JWT jti（全域唯一）；SUPER_ADMIN 跨租戶 session 為合法情境；refresh-token rotation 時可能跨租戶查詢 → `@Filter` 會中斷 rotation |
| `TenantAuthConfigEntity` | `AuthenticationDispatcher.resolveConfig` 在**登入流程**中呼叫，此時 `JwtAuthFilter` 尚未執行、`TenantContext` 為 `null` → `TenantFilterAspect` 的 fail-closed 策略會拋出 `IllegalStateException` → **登入功能全面中斷** |
| `RolePermissionEntity` | `tenant_id IS NULL` 是合法的「全域權限」設計；單純 `@Filter(condition="tenant_id = :tenantId")` 會排除這些 row → **整個 RBAC 失效** |

因此採取「明確標註設計決策 + 回歸測試釘住設計選擇」策略：

1. **補齊 3 個 entity 的設計決策 JavaDoc**（詳述「为何不能補 `@Filter`」 + 「對應的縱深防禦措施」 + 「何時可安全變更此決策」）：
   - [UserSessionEntity.java](../../../backend/src/main/java/com/taipei/iot/auth/entity/UserSessionEntity.java)
   - [TenantAuthConfigEntity.java](../../../backend/src/main/java/com/taipei/iot/auth/provider/config/entity/TenantAuthConfigEntity.java)
   - [RolePermissionEntity.java](../../../backend/src/main/java/com/taipei/iot/rbac/entity/RolePermissionEntity.java)

2. **新增回歸測試** [TenantIsolationDesignDecisionTest.java](../../../backend/src/test/java/com/taipei/iot/tenant/TenantIsolationDesignDecisionTest.java)，針對 3 個 entity 各有一個 test case，使用 `assertFalse(TenantAware.class.isAssignableFrom(...))` 、`assertNull(...getAnnotation(Filter.class))` 與 `assertFalse(TenantScopedRepository.class.isAssignableFrom(...))` 三項斷言釘住設計。未來開發者若出於善意補 `@Filter`，此測試會失敗，並透過錯誤訊息提醒該閱讀 entity JavaDoc 中的根因。測試結果：`Tests run: 3, Failures: 0, Errors: 0`。

3. **未處理（需跨模組重構）**：若未來需變更為 `@Filter` 進路，根本解法是：
   - `TenantAuthConfigEntity`：重構 `AuthenticationDispatcher` 使其以 `TenantContext.runInSystemContext` 包裹查詢，或在登入前先從 request body / header 推斷並設定 TenantContext。
   - `RolePermissionEntity`：重新定義「全域權限」的儲存方式（例如專用 table），或評估使用 Hibernate `@FilterDef` + 含 `IS NULL` 條件的自訂 condition。
   - `UserSessionEntity`：評估是否需要「同一 jti 跨租戶」設計；若重新設計為「一 jti = 一 tenant」，則可安全補 `@Filter`。

---

### 🟡 中等風險

#### T-3 [中] `TenantEnabledCache` 為單節點記憶體，多實例部署下 fail-open  ✅ 已修復（2026-05-27）

> **修復摘要**：採設計指南 [01-docs/new-feature/cache/](../../new-feature/cache/) Pattern 2，改用 Redis Pub/Sub 廣播 `markDisabled/markEnabled`。所有 Pod 訂閱 `iot.tenant.enabled.changed` channel，秒級同步本機 cache；發送者用 `podId` 過濾自己的訊息避免重複套用。新增 [RedisMessageListenerContainer](../../../backend/src/main/java/com/taipei/iot/config/RedisConfig.java) bean。無 Redis 時自動降級為 local-only 並印 WARN。配合 T-12 的 `@Scheduled` 5 分鐘校正兜底訊息遺失。涵蓋測試見 [TenantEnabledCacheTest](../../../backend/src/test/java/com/taipei/iot/tenant/TenantEnabledCacheTest.java)（13 tests 全綠）。

**檔案**：[TenantEnabledCache.java](../../../backend/src/main/java/com/taipei/iot/tenant/TenantEnabledCache.java)

```java
// 注意：此為單節點快取。若部署多實例，需改用 Redis pub/sub 或類似機制同步。
```

**問題**：

v1 review 將「停用場域時即時拒絕已登入使用者」列為 ✅ 已修復，但實作只在「呼叫 toggle API 的那一個 instance」更新快取。在水平擴展部署（Kubernetes Pod 副本 ≥ 2）下：

- Pod A 收到 disable 請求 → 更新 DB + 自己的 cache
- Pod B 的 cache 未同步 → 該 tenant 的請求**經 Pod B 路由時仍可通過 JWT 驗證**
- 結果：停用場域只阻擋約 `1 / N` 的流量，N 為實例數

**建議**（依優先級）：

1. **短期**：在 `application.yml` 加註，且在 `TenantAdminService.toggleEnabled` 的回應中明確告知「設定需 ≤ X 秒生效」（X = JWT TTL，因為 token 過期後重新登入會走 DB 查詢）。
2. **中期**：引入 Spring Boot Actuator `/actuator/refresh` + Bus，廣播 cache invalidate event。
3. **長期**：改用 Redis 作為共用 cache（Set），或直接讀 DB 並在前端加上 30 秒 TTL local cache。

> **目前是否需修？** 若生產環境僅單實例，現狀可接受，但須在啟動時 log 警告 + 在部署文件強調此限制。

---

#### T-4 [中] `adminPassword` 僅檢查 `@Size(min = 8)`，未套用 PasswordPolicy  ✅ 已修復（2026-05-27）

> **修復摘要**：[TenantAdminService.createTenant](../../../backend/src/main/java/com/taipei/iot/tenant/service/TenantAdminService.java) 注入 [PasswordValidator](../../../backend/src/main/java/com/taipei/iot/user/service/PasswordValidator.java)，於 `passwordEncoder.encode(req.getAdminPassword())` 之前先呼叫 `passwordValidator.validate(tenantId, password, new UserContext(null, email))`，讓初始管理員密碼也受該租戶有效政策約束（複雜度、長度、不包含 email 等）。新租戶剛建立完，resolver 會 fallback 至 platform 預設政策。涵蓋測試見 [TenantAdminServiceTest](../../../backend/src/test/java/com/taipei/iot/tenant/service/TenantAdminServiceTest.java)（11 tests 全綠）。

**檔案**：[CreateTenantRequest.java](../../../backend/src/main/java/com/taipei/iot/tenant/dto/CreateTenantRequest.java#L31) + [TenantAdminService.java](../../../backend/src/main/java/com/taipei/iot/tenant/service/TenantAdminService.java)

```java
@Size(min = 8)
private String adminPassword;
// ...
.passwordHash(passwordEncoder.encode(req.getAdminPassword()))
```

**問題**：

SUPER_ADMIN 建立場域時可以順便建立第一個管理員帳號，但密碼**僅檢查最小長度 8**，跳過了系統其他地方都用到的 `PasswordValidator`（複雜度、字典攻擊、最近 N 次密碼比對等）。這意味著：

- 場域管理員一開始就可使用 `password` / `12345678` 之類的弱密碼
- 等於開發了一條繞過 Password Policy 的後門
- 與 audit 模組 A-10 修復同源——機敏輸入應走統一驗證

**建議**：

```java
// TenantAdminService.createTenant()
if (req.getAdminPassword() != null && !req.getAdminPassword().isBlank()) {
    // 套用該租戶的密碼政策（剛建立完 tenant，policy resolver 應能取到該租戶設定，或 fallback 至 platform 預設）
    passwordValidator.validate(req.getAdminPassword(), tenantId);
}
```

> 同時建議在前端建立場域 dialog 加上即時的密碼強度提示。

---

#### T-5 [中] `TenantInterceptor` 在 `single` 模式下**靜默覆蓋** JWT 中的 `tenantId` ✅ 已修復（2026-05-27）

**修復摘要**：
- `TenantInterceptor` 重寫：single 模式下先比對 JWT tenantId 與 `tenant.default-id`，不一致且非 SYSTEM context 時 fail-fast（`SecurityLogger.warn(TENANT_MODE_MISMATCH, ...)` + `BusinessException(TENANT_MODE_MISMATCH)`，返回 403）。
- 新增 `ErrorCode.TENANT_MODE_MISMATCH("10026", 403)` 與 `SecurityEvent.TENANT_MODE_MISMATCH`。
- `@PostConstruct logMode()` 於 bean 初始化時印出當前 `tenant.mode` 與 `defaultId`（single 模式為 WARN），便於運維在 boot log 即時確認部署設定。
- Class-level JavaDoc 完整描述 single/multi 模式合約、不一致處理、SYSTEM context 豁免、公開端點（JWT 未設 tenantId）放行策略。
- 測試：`TenantInterceptorTest` 擴充至 8 則【+5】，覆蓋 match/mismatch/SYSTEM context/公開端點/boot log 不拋例外。

**原問題**：

**檔案**：[TenantInterceptor.java](../../../backend/src/main/java/com/taipei/iot/tenant/TenantInterceptor.java)

```java
if ("single".equals(tenantProperties.getMode())) {
    TenantContext.setCurrentTenantId(tenantProperties.getDefaultId());
}
```

**問題**：

`JwtAuthenticationFilter` 先依 JWT 的 `tenantId` claim 設定 `TenantContext`，**之後 `TenantInterceptor.preHandle` 在 `single` 模式下會覆寫成 `tenant.default-id`**，且沒有任何 log 警告。

潛在風險：
1. **配置漂移**：若 production 被誤設為 `tenant.mode=single`，所有不同 tenant 的 JWT 都會被當成同一個 `DEFAULT` 租戶 → 嚴重資料污染
2. **JWT 與 Context 不一致**：`SecurityContextHolder` 的 details 仍含原 JWT 的 tenantId，而 `TenantContext` 是 DEFAULT；後續取用者拿到哪個，view-by-view 不一致
3. **單元測試難以發現**：兩種模式行為差異大，整合測試難覆蓋全

**建議**：

1. 若 mode = `single` 且 JWT 已帶有與 `defaultId` 不同的 tenantId → **拋 401** 或至少在 SecurityLogger 記錄 `TENANT_MODE_MISMATCH`：
   ```java
   String jwtTenant = TenantContext.getCurrentTenantId();
   if ("single".equals(mode)) {
       if (jwtTenant != null && !jwtTenant.equals(defaultId) && !TenantContext.isSystemContext()) {
           SecurityLogger.warn(SecurityEvent.TENANT_MODE_MISMATCH, ...);
           throw new BusinessException(ErrorCode.TENANT_MODE_MISMATCH);
       }
       TenantContext.setCurrentTenantId(defaultId);
   }
   ```
2. 在 `@PostConstruct` 啟動時印一行 `WARN` 等級 log，使運維能在 boot log 直接看到當前模式。
3. 「single 模式覆寫 JWT」應寫在 `TenantInterceptor` 的 class-level JavaDoc，目前該注釋僅在實作行旁。

---

#### T-6 [中] `TenantEntityListener.preUpdate/preRemove` 在 `TenantContext == null` 時**靜默放行** ✅ 已修復（2026-05-27）

**修復摘要**：
- `verifyTenantOwnership` 在 `TenantContext.getCurrentTenantId() == null` 且非 SYSTEM context 時改為 **fail-closed**：先 `log.error("[SECURITY] TENANT_CONTEXT_MISSING ...")` 再拋 `SecurityException`，錯誤訊息明示提示使用 `TenantContext.setSystemContext()` / `runInSystemContext()`。
- 與 `TenantFilterAspect` 的 fail-closed 策略對齊，消除「正確路徑 vs Listener 路徑」的文件不一致。
- `preUpdate` 與 `preRemove` 的 JavaDoc 同步重寫，明記「null context = 拒絕」合約。
- 測試：`TenantEntityListenerTest` 擴充至 12 則【+10】，覆蓋同租戶/SYSTEM 適用/跨租戶拋/null context fail-closed/`runInSystemContext` 逆向 escape hatch/非 TenantAware 不受影響。
- 全測試驗證：550 tests, 0 failures（已確認 `AuditPurgeJob` 等跨租戶路徑都已透過 `setSystemContext()` 顯示釋出同意，不受本修改影響）。

**原問題**：

**檔案**：[TenantEntityListener.java](../../../backend/src/main/java/com/taipei/iot/tenant/TenantEntityListener.java#L84)

```java
String currentTenantId = TenantContext.getCurrentTenantId();
if (currentTenantId == null) {
    // TenantContext 未設定（可能是非 HTTP 路徑），不阻擋但記錄警告
    return;
}
```

**問題**：

- 註解寫「記錄警告」但程式碼**沒有 log**。
- 與 `TenantFilterAspect` 的 fail-closed 策略**不一致**：Aspect 拋 `IllegalStateException`，Listener 卻放行。
- 若任何路徑（如 `@PostConstruct`、CommandLineRunner、誤刪 `TenantContext.clear()` 後的 controller code）在 ThreadLocal 為 null 時對 TenantAware entity 做 update/delete → 跨租戶寫入會悄無聲息地通過。
- `setSystemContext()` 才是「合法的跨租戶授權」，但目前邏輯與 `null` 等同視之。

**建議**：

```java
if (currentTenantId == null) {
    // 與 TenantFilterAspect 對齊：fail-closed
    log.error("[SECURITY] TENANT_CONTEXT_MISSING operation={} entityClass={} — refusing to {} cross-tenant entity",
            operation, entity.getClass().getSimpleName(), operation);
    throw new SecurityException(
        "TenantContext is not set for " + operation + " of " + entity.getClass().getSimpleName() +
        ". Use TenantContext.setSystemContext() explicitly if cross-tenant operation is intended.");
}
```

→ 強迫所有「我知道我在跨租戶操作」的呼叫點顯式呼叫 `setSystemContext()` / `runInSystemContext()`，把灰色地帶消滅。

---

#### T-7 [中] `TenantEnabledCache.ensureInitialized()` lazy load 將「首次失敗」轉嫁給第一個請求  ✅ 已修復（2026-05-27）

> **修復摘要**：改 `@PostConstruct init()` eager warm-up；warm-up 失敗時 fail-open（log ERROR + 空集合啟動 + 由排程持續重試），不再阻擋應用啟動，也不再把 DB 全表掃描成本轉嫁給第一個請求。涵蓋測試：`init_warmsUpAndSubscribes`、`warmUp_failureDoesNotThrow_andCacheStaysEmpty`、`init_idempotent`。

**檔案**：[TenantEnabledCache.java](../../../backend/src/main/java/com/taipei/iot/tenant/TenantEnabledCache.java)

**問題**：

雙重檢查 DCL 寫法正確，但「首次呼叫者承擔 DB 全表掃描成本」的設計在生產環境下：

- 第一個進入的請求（任何 tenant）會多走一次 `tenantRepository.findAll()`，並且**在持有 monitor 鎖時執行 DB I/O**——若 DB 暫時抖動，所有後續請求都會 block。
- 若 DB 連線失敗，`isTenantDisabled` 會拋 `DataAccessException`，被 `JwtAuthenticationFilter` 包成 500 → 第一個用戶體驗極差。
- 應用啟動 → 預熱時間長 → P99 延遲尖刺。

**建議**：

改為 `@PostConstruct` 預載，並對「初始載入失敗」做 fail-fast：

```java
@PostConstruct
void warmUp() {
    try {
        tenantRepository.findAll().stream()
                .filter(t -> !Boolean.TRUE.equals(t.getEnabled()))
                .forEach(t -> disabledTenantIds.add(t.getTenantId()));
        initialized = true;
        log.info("[TenantEnabledCache] Loaded {} disabled tenants on startup", disabledTenantIds.size());
    } catch (Exception e) {
        // 預設 fail-closed：載入失敗就空集合 + initialized=true，請求繼續走（避免循環依賴啟動失敗）
        // 但要明確 log，運維可從 ELK 偵測
        log.error("[TenantEnabledCache] Failed to warm up — starting with empty disabled set", e);
        initialized = true;
    }
}
```

---

#### T-8 [中] 缺乏 `TenantConsistencyValidator` — 同問題 T-1 的根因 ✅ 已修復（2026-05-27）

**修復摘要**：
- 新增 [TenantConsistencyValidator.java](../../../backend/src/main/java/com/taipei/iot/tenant/TenantConsistencyValidator.java)，訂閱 `ApplicationReadyEvent`。
- 透過 `EntityManagerFactory.getMetamodel()` 掃出所有 `TenantAware` entity，再用 Spring Data `Repositories` API 查出對應 Repository bean；若 Repository **不是** `TenantScopedRepository` 實例 → 加入違規清單。
- 任何違規 → `log.error` + `throw new IllegalStateException` **中止啟動**（fail-fast）。
- 讀取全部 `JpaRepository` bean，本次驗證現有代碼本零違規（T-1 修復後的狀態被鎖死）。
- 抽出純函式 `checkConsistency(Set, Function)` + `resolveTenantAwareEntities(emf)` 便於單元測試，不需啟動 Spring context。
- 測試：`TenantConsistencyValidatorTest`【5 則】—合規/違規/混合/無對應 Repository/空集合。
- 全測試驗證：555 tests, 0 failures（Spring Boot tests 啟動時 Validator 實際跳過並通過，證明現有生產代碼皆合規）。
- 本謟識其限：純 DAO（如 `PasswordPolicyDao`）不走 Spring Data Repository，不在掃描範圍；但該类 DAO 本身每個方法都明示取 `tenantId` 參數，無需標記者介面。

**原問題**：

**檔案**：（新檔案建議）

**問題**：

`TenantScopedRepository` 是純標記介面，**沒有編譯期檢查**也沒有**啟動期檢查**確保「凡是 `TenantAware` entity 的 Repository 都 implements 它」。本次 v2 review 才人工發現 `AnnouncementAttachmentRepository` 漏標，未來若新增模組，同樣的漏洞會無限重演。

**建議**：

在 `tenant/` 套件新增 `TenantConsistencyValidator`，於 `ApplicationReadyEvent` 觸發掃描：

```java
@Component
@RequiredArgsConstructor
public class TenantConsistencyValidator {

    private final EntityManagerFactory emf;
    private final ApplicationContext appCtx;

    @EventListener(ApplicationReadyEvent.class)
    public void validate() {
        List<String> violations = new ArrayList<>();
        
        // 取所有 TenantAware Entity
        Set<Class<?>> tenantAwareEntities = emf.getMetamodel().getEntities().stream()
                .map(EntityType::getJavaType)
                .filter(TenantAware.class::isAssignableFrom)
                .collect(Collectors.toSet());
        
        // 取所有 JpaRepository<TenantAwareEntity, ?>
        Map<String, Object> repos = appCtx.getBeansOfType(JpaRepository.class);
        for (Object repo : repos.values()) {
            Class<?>[] interfaces = ((Advised) repo).getProxiedInterfaces(); // 或用 ResolvableType
            for (Class<?> ifc : interfaces) {
                Class<?> entityType = resolveEntityType(ifc); // 取 JpaRepository 泛型
                if (tenantAwareEntities.contains(entityType) 
                        && !(repo instanceof TenantScopedRepository)) {
                    violations.add(ifc.getName() + " (entity=" + entityType.getSimpleName() + ")");
                }
            }
        }
        
        if (!violations.isEmpty()) {
            throw new IllegalStateException(
                "Tenant isolation violations detected — these repositories operate on TenantAware entities " +
                "but do not implement TenantScopedRepository:\n  - " + String.join("\n  - ", violations));
        }
    }
}
```

→ 從此「忘記 implements」會在 boot 時直接炸開，CI 階段就能攔截。

---

### 🟢 低風險

#### T-9 [低] `TenantEntity.config` (jsonb Map) 未做大小/結構驗證  ✅ 已修復（2026-05-27）

**檔案**：[TenantEntity.java](../../../backend/src/main/java/com/taipei/iot/tenant/TenantEntity.java)

`config` 為 `Map<String, Object>` jsonb 欄位，目前沒有任何 API 寫入它（CRUD 沒給 setter 入口），但 entity 已暴露 setter。若未來新增「更新 tenant 設定」API，未限制大小與結構可能被當成 NoSQL 注入點（塞入超大 JSON 撐爆 DB / index）。

**修復摘要**：
- 新增 [TenantConfigValidator](../../../backend/src/main/java/com/taipei/iot/tenant/TenantConfigValidator.java) 工具類，定義三道閘門：
  - 序列化後 JSON bytes ≤ **10 KB**（`MAX_SERIALIZED_BYTES`）
  - top-level keys ≤ **50**（`MAX_TOP_LEVEL_KEYS`，防止扁平結構繞過 size 限制造成 index 膨脹）
  - 巢狀深度 ≤ **5**（`MAX_DEPTH`，含 Map / Collection；防止 deeply-nested payload 觸發解析 OOM）
- 在 [TenantEntity](../../../backend/src/main/java/com/taipei/iot/tenant/TenantEntity.java) 加上 `@PrePersist` / `@PreUpdate` 私有 callback `validateConfig()`，**所有寫入路徑（含未來新 API、直接 `setConfig()` 後 save、bulk update）一律經過驗證**；不必依賴 controller 層 DTO 驗證。違規時拋 `IllegalArgumentException` → `GlobalExceptionHandler` 轉 400。
- `config` field JavaDoc 標註「目前無 API 直寫，僅供未來擴展」，並指向 `TenantConfigValidator`。
- 新增 [TenantConfigValidatorTest](../../../backend/src/test/java/com/taipei/iot/tenant/TenantConfigValidatorTest.java)（14 tests 全綠）：合法情境（null / 空 map / 一般 feature flag / 邊界 depth / 邊界 keys）、違規情境（超 keys / 超 depth / List 包裝繞過 depth / 超 size / 錯誤訊息含實際與上限值）、常數契約（防無預警放寬）、entity lifecycle 整合（反射觸發 `validateConfig` 確認 hook 串連）。

---

#### T-10 [低] `TenantInterceptor` 在路徑被 SecurityConfig 排除（如 `/v1/noauth/**`）時仍會跑  ✅ 已修復（2026-05-27）

> **修復摘要**：在 [`WebMvcConfig.addInterceptors`](../../../backend/src/main/java/com/taipei/iot/config/WebMvcConfig.java) 顯式設定 `TenantInterceptor`：`addPathPatterns("/v1/**").excludePathPatterns("/v1/noauth/**")`，移除「靠 `SecurityConfig` 白名單副作用」的隱性耦合。`/v1/noauth/**`（login / captcha / refresh / forgot-password / force-change-password / turnstile-config）尚未經 JWT 設定 tenantId，本就不需要也不應依賴 TenantInterceptor 的 single-mode 覆寫。Swagger / actuator / `/ws/**` 天然不在 `/v1/**` 範圍內。涵蓋測試：[`WebMvcConfigTest`](../../../backend/src/test/java/com/taipei/iot/config/WebMvcConfigTest.java) 4 cases（透過 reflection + `ServletRequestPathUtils` 驗證 `MappedInterceptor.matches` 行為）。

**檔案**：[TenantInterceptor.java](../../../backend/src/main/java/com/taipei/iot/tenant/TenantInterceptor.java)、[WebMvcConfig.java](../../../backend/src/main/java/com/taipei/iot/config/WebMvcConfig.java)

如果 InterceptorRegistry 沒有對公開路徑排除，登入請求進入時：
- `JwtAuthenticationFilter` 未驗證 token（白名單）→ TenantContext 未設定
- Interceptor 在 `multi` 模式下不做任何事（沒 else 分支）→ TenantContext 仍為 null
- 進入 `AuthServiceImpl.login`，內部已有 `setSystemContext()` 處理

目前運作正常，但邏輯耦合脆弱。已在 `WebMvcConfig` 註冊 Interceptor 時明確列出：

```java
registry.addInterceptor(tenantInterceptor)
        .addPathPatterns("/v1/**")
        .excludePathPatterns("/v1/noauth/**");
```

明確列出哪些路徑「故意」不過 Interceptor，避免心智負擔。

---

#### T-11 [低] `TenantAdminController` 管理 API 無 `@RateLimit` 保護  ✅ 已修復（2026-05-27）

> **修復摘要**：在 [`TenantAdminController`](../../../backend/src/main/java/com/taipei/iot/tenant/controller/TenantAdminController.java) 4 個端點均掛上 `@RateLimit`：`listTenants`（60/分）、`createTenant`（10/分）、`updateTenant`（30/分）、`toggleEnabled`（20/分）。限額依「寫操作嚴格、讀操作寬鬆、toggle 適中避免 `TenantEnabledCache` 抖動 / Pub/Sub 風暴」原則設定。涵蓋測試：[`TenantAdminControllerRateLimitTest`](../../../backend/src/test/java/com/taipei/iot/tenant/controller/TenantAdminControllerRateLimitTest.java) 7 cases（反射驗證 key / limit / period 邊界 + 全端點涵蓋 + key 唯一性）。

**檔案**：[TenantAdminController.java](../../../backend/src/main/java/com/taipei/iot/tenant/controller/TenantAdminController.java)

雖然 `@PreAuthorize("hasRole('SUPER_ADMIN')")` 已是嚴格授權，但若 SUPER_ADMIN 帳號 token 不慎外洩，攻擊者可：
- 大量呼叫 `POST /v1/admin/tenants` 撐爆 DB
- 連續 toggle enable/disable 製造 cache 抖動

建議掛上低速率上限：

```java
@PostMapping
@RateLimit(key = "admin-tenant-create", limit = 10, period = 60)
@AuditEvent(AuditEventType.CREATE_TENANT)
public BaseResponse<TenantDto> createTenant(@Valid @RequestBody CreateTenantRequest req) { ... }
```

---

#### T-12 [低] `TenantEnabledCache` 未隨 `tenantRepository.save(updated)` 同步（僅 toggle 時更新）  ✅ 已修復（2026-05-27）

> **修復摘要**：新增 `@Scheduled(fixedRateString = "${tenant.cache.refresh-interval-ms:300000}")` 每 5 分鐘從 DB 重新校正。實作採差集方式（`toAdd / toRemove`）個別更新，避免 `clear + addAll` 造成的短暫空窗；refresh 失敗不會清空既有 cache。同時兜底 T-3 Pub/Sub 訊息遺失與 DB 直改 / Flyway / 批次工具繞過本元件的情境。涵蓋測試：`refresh_addsNewAndRemovesStale_viaDiff`、`refresh_failureDoesNotClearCache`。

**檔案**：[TenantAdminService.java](../../../backend/src/main/java/com/taipei/iot/tenant/service/TenantAdminService.java)

`createTenant` 預設 `enabled = true`，正確路徑下 cache 不需更新。但若管理員透過 **資料庫直改、Flyway migration、或未來新增的批次工具** 變更 `enabled`，cache 不會同步。

**建議**：
- 短期：在 entity `@PostUpdate` listener 上反查 `enabled` 變更後通知 cache（複雜，可能不值得）。
- 中期：採 T-3 提及的 Redis 共用方案，cache 永遠以 DB 為準（TTL 60 秒）。
- 文件化：於 [`TenantEnabledCache` JavaDoc](../../../backend/src/main/java/com/taipei/iot/tenant/TenantEnabledCache.java) 強調「只支援透過 API 變更，禁止直改 DB」。

---

#### T-13 [低] `TenantContext.setSystemContext()` 使用點散落，建議封裝為 `@TenantSystemContext` AOP

**檔案**：散見於 `AuthServiceImpl.java:813,981`、`AuditService.java:139`、`AuditAsyncWriter.java:30`、`AuditPurgeJob.java:33,70`

13 處呼叫 `setSystemContext` / `runInSystemContext`，每處都需要記得 `finally` 還原（或用 v1 修補的 runnable 包裝）。但 `AuthServiceImpl` 與 `AuditService` 內仍是手寫 try-finally / set-without-restore，容易遺漏。

**建議**：

新增 AOP 註解 `@RunInSystemTenantContext`：

```java
@Around("@annotation(RunInSystemTenantContext)")
public Object aroundSystemContext(ProceedingJoinPoint pjp) throws Throwable {
    String previous = TenantContext.getCurrentTenantId();
    try {
        TenantContext.setSystemContext();
        return pjp.proceed();
    } finally {
        if (previous != null) TenantContext.setCurrentTenantId(previous);
        else TenantContext.clear();
    }
}
```

呼叫端只需：

```java
@RunInSystemTenantContext
private void writeAuditAsync(...) { ... }
```

→ 減少模板碼，且新增點不會忘記還原。

> **✅ 修復摘要（2026-05-27）**：新增 [`@RunInSystemTenantContext`](../../../backend/src/main/java/com/taipei/iot/tenant/RunInSystemTenantContext.java) 標記註解與 [TenantSystemContextAspect](../../../backend/src/main/java/com/taipei/iot/tenant/TenantSystemContextAspect.java)（`@Order(0)`，確保比 `TenantFilterAspect` 早執行）。Aspect 將標註方法整段包進 `TenantContext.runInSystemContext(Supplier)`，並透過內部 `CheckedAroundException` wrapper 穿過 `Supplier` 邊界後解開，使呼叫端能正常拋出 checked exception。同時補上 `TenantContext.runInSystemContext(Supplier<T>)` 重載供需要回傳值的情境使用；[AuditService](../../../backend/src/main/java/com/taipei/iot/audit/service/AuditService.java) 的跨租戶查詢已改用此 API 取代手寫 try-finally。已套用於 [AuditAsyncWriter.saveAsync](../../../backend/src/main/java/com/taipei/iot/audit/async/AuditAsyncWriter.java) 與 [AuditPurgeJob.purgeAllTenants](../../../backend/src/main/java/com/taipei/iot/audit/job/AuditPurgeJob.java)。新增 [TenantSystemContextAspectTest](../../../backend/src/test/java/com/taipei/iot/tenant/TenantSystemContextAspectTest.java)（4 tests 全綠），涵蓋：context 在方法執行期間為 SYSTEM、有/無前置 context 的還原行為、RuntimeException 仍能還原 context、checked exception 經 aspect 解包後仍以原型別拋出。

---

## 四、安全性總結（v2）

| 面向 | v1 結論 | v2 結論 | 變化 |
|---|---|---|---|
| 權限控制（API 層） | ✅ 良好 | ✅ 良好 | — |
| 租戶讀取隔離（@Filter） | ✅ 良好 | ⚠️ **發現 1 個 Repository 漏標 + 3 個 Entity 有 tenant_id 但無 @Filter** | 降級 |
| 租戶寫入隔離（Listener） | ✅ 良好 | ⚠️ `null` context 靜默放行，與 Aspect 不一致 | 降級 |
| 多實例部署 | 未評估 | ⚠️ `TenantEnabledCache` 單節點，需 Redis | 新識別 |
| ThreadLocal 邊界 | ✅（v1 已加 `JwtAuthenticationFilter.finally clear`） | ✅ 良好 | — |
| 機敏資料保護（密碼） | 未評估 | ⚠️ adminPassword 跳過 PasswordPolicy | 新識別 |
| Tenant Mode 一致性 | 未評估 | ⚠️ single 模式靜默覆寫 JWT | 新識別 |
| 啟動期一致性檢查 | 無 | ❌ 缺 `TenantConsistencyValidator` | 新識別 |
| 審計追蹤 | ✅ 良好 | ✅ 良好（v1 已修） | — |
| Session 失效（單節點） | ✅ 良好 | ✅ 良好（單節點下） | — |
| Rate Limiting | 未評估 | ⚠️ Admin API 無 rate limit | 新識別 |

---

## 五、建議修復優先順序

| 優先級 | 編號 | 議題 | 預估工 | 狀態 |
|---|---|---|---|---|
| 🚨 P0（立即） | T-1 | `AnnouncementAttachmentRepository` 補上 `TenantScopedRepository` | 5 分鐘 + 測試 | ✅ 已完成 |
| 🚨 P0（立即） | T-8 | 新增 `TenantConsistencyValidator` 啟動期掃描 | 半天 | ✅ 已完成 |
| 🔴 P1 | T-2 | 補 `UserSession` / `TenantAuthConfig` / `RolePermission` 的 @Filter 或文件化全域策略 | 1~2 天（跨模組） | ✅ 已完成（文件化路線） |
| 🔴 P1 | T-6 | `TenantEntityListener` null context 改 fail-closed | 1 小時（含補測試） | ✅ 已完成 |
| 🟡 P2 | T-3 | `TenantEnabledCache` 多實例策略（Redis Pub/Sub + 定期校正） | 視部署決策 | ✅ 已完成 |
| 🟡 P2 | T-5 | `TenantInterceptor` single 模式驗 JWT tenant 一致性 | 半天 | ✅ 已完成 |
| 🟡 P2 | T-4 | `adminPassword` 串接 `PasswordValidator` | 半天 | ✅ 已完成 |
| 🟡 P3 | T-7 | `TenantEnabledCache` 改 `@PostConstruct` 預載 | 30 分鐘 | ✅ 已完成 |
| 🟢 P3 | T-11 | Admin API 加 `@RateLimit` | 1 小時 | ✅ 已完成 |
| 🟢 P3 | T-13 | 封裝 `@RunInSystemTenantContext` AOP | 半天 | ✅ 已完成 |
| 🟢 P4 | T-12 | `TenantEnabledCache` 隨 DB 直改同步（@Scheduled 校正） | 30 分鐘 | ✅ 已完成 |
| 🟢 P4 | T-10 | 文件化、清理（WebMvcConfig 顯式 excludePathPatterns） | 30 分鐘 | ✅ 已完成 |
| 🟢 P4 | T-9 | `TenantEntity.config` jsonb 大小/結構驗證 | 30 分鐘 | ✅ 已完成 |

---

## 六、測試覆蓋觀察

現存測試（[backend/src/test/java/com/taipei/iot/tenant/](../../../backend/src/test/java/com/taipei/iot/tenant/)）：

- ✅ `TenantContextTest` — ThreadLocal 基礎行為
- ✅ `TenantInterceptorTest` — single/multi 模式切換；single 模式 JWT tenant 一致/不一致/SYSTEM context/公開端點（8 tests，2026-05-27 T-5 擴充）
- ✅ `TenantFilterAspectIntegrationTest` — Aspect 啟用 Filter 流程
- ✅ `TenantEntityListenerTest` — PrePersist/PreUpdate/PreRemove；同租戶放行 / 跨租戶拋 SecurityException / SYSTEM context 適用 / **null context fail-closed** / `runInSystemContext` escape hatch（12 tests，2026-05-27 T-6 擴充）
- ✅ `TenantEnabledCacheTest` — warm-up / publish / handleEvent self-filter / scheduled refresh diff / 降級無 Redis（13 tests，2026-05-27 新增）
- ✅ `TenantIsolationDesignDecisionTest` — 鎖死 UserSession/TenantAuthConfig/RolePermission 的設計決策（3 tests）
- ✅ `TenantConsistencyValidatorTest` — [T-8] 掃描邏輯：合規 / 違規 / 混合 / 無 Repository / 空集合（5 tests，2026-05-27 新增）
- ✅ `TenantAdminServiceTest` — createTenant（含密碼政策套用） / updateTenant / toggleEnabled（11 tests，2026-05-27 新增）
- ✅ `WebMvcConfigTest` — [T-10] `TenantInterceptor` 顯式 `/v1/noauth/**` 排除 + `/v1/**` 涵蓋 + 非 `/v1` 路徑不掛 + `RateLimitInterceptor` 覆蓋所有 `/v1/**`（含 noauth）（4 tests，2026-05-27 新增）
- ✅ `TenantAdminControllerRateLimitTest` — [T-11] 4 個 admin 端點 `@RateLimit` key / limit / period 邊界 + 全端點涵蓋防漏網 + key 唯一性 + class-level @PreAuthorize 保護（7 tests，2026-05-27 新增）
- ✅ `TenantConfigValidatorTest` — [T-9] `TenantEntity.config` jsonb 大小（≤10 KB）/ top-level keys（≤50）/ 巢狀深度（≤5）驗證 + entity `@PrePersist`/`@PreUpdate` lifecycle 整合（14 tests，2026-05-27 新增）
- ❌ **缺**：跨租戶實際攻擊情境測試（例：A 租戶試圖 update B 租戶 announcement，應拋 SecurityException）

修復 T-1~T-8 時建議同步補上對應測試。

---

## 七、結論

v1 review 已穩固「基礎建設正確性」；v2 揭露「**新增模組時的一致性風險**」與「**多實例部署假設**」兩大方向。**T-1 與 T-8 應立即處理**，因為它們是其餘所有保護機制是否有效的前提。

> 本文僅就 Tenant 模組視角發現的議題彙整。跨模組（如 Auth、RBAC）相關項目（如 T-2 中 `TenantAuthConfigEntity`）建議在對應模組 review 時同步檢視。
