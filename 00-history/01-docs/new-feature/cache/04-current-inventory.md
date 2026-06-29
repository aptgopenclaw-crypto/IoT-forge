# 本機 Cache 現況盤點（2026-05-27）

> 範圍：以「**cache = 本機 JVM heap 的可寫資料儲存**」為定義（**不含 Redis**），盤點 backend 目前所有需要在多實例部署下注意一致性的本機 cache。
>
> 對應上層文件：[README.md](README.md) §四「本專案現有 cache 的建議策略」之即時快照；當代碼或部署假設變更時更新本文件。
>
> 相關 review：[Tenant Code Review v2 T-3 / T-7 / T-12](../../code-review/tenant/tenant-module-code-review-v2.md)（已完成）。

---

## 一、判定標準

一個 in-memory 結構被列為「本機 cache」，必須同時符合：

1. ✅ 儲存於 **JVM heap**（`ConcurrentHashMap` / `Set` / `volatile` field 等），不是 Redis / DB。
2. ✅ **可寫**（可在啟動後被 mutate），不只是 `Map.ofEntries(...)` 的不可變常數查表。
3. ✅ **跨請求重用**（生命週期 ≥ HTTP request），不是 method-local 的 builder。

未通過上述任一條的不在本文件範圍：

- [`FileValidationService.EXTENSION_MIME_MAP`](../../../backend/src/main/java/com/taipei/iot/common/service/FileValidationService.java#L49) — `Map.ofEntries(...)` 不可變常數，啟動後永不變更。
- 各 `Service` / `Controller` 內方法區域變數 `new HashMap<>()` / `new LinkedHashMap<>()`（如 [`AnnouncementService`](../../../backend/src/main/java/com/taipei/iot/announcement/service/AnnouncementService.java#L612)、[`JwtUtil`](../../../backend/src/main/java/com/taipei/iot/auth/security/JwtUtil.java#L52)）— 只是請求區段 builder，非跨請求 cache。
- 純 Redis-only 的資料（Captcha / Turnstile / Session 活動時間 / Refresh-Token JTI revocation / Rate Limit 主路徑）— 已是分散式儲存，無本機同步問題。

---

## 二、現有本機 cache 一覽

截至 2026-05-27，全 backend 符合上述判定的本機 cache 共 **2 處**（`PasswordPolicyResolver` 已於同日移除 cache，見 §3.2；`MenuService.allMenusCache` 已於 2026-05-29 移除，見 §3.4）：

| # | 元件 | 檔案 | 內容 | 失效機制 | 多實例同步狀態 |
|---|---|---|---|---|---|
| 1 | **TenantEnabledCache** | [tenant/TenantEnabledCache.java](../../../backend/src/main/java/com/taipei/iot/tenant/TenantEnabledCache.java) | 停用中租戶 ID 的 `Set<String>` | ① Redis Pub/Sub 廣播<br>② `@Scheduled` 每 5 分鐘 DB 差集校正<br>③ `@PostConstruct` warm-up | ✅ **已處理**（Pattern 2，含降級）|
| 2 | **RateLimitInterceptor.localFallback** | [common/interceptor/RateLimitInterceptor.java](../../../backend/src/main/java/com/taipei/iot/common/interceptor/RateLimitInterceptor.java#L68) | `Map<key, long[]>` 滑動窗口計數 | 滑動窗口邊界自然汰換；Redis 不可用時才啟用 | ⚠️ **設計上接受**（降級路徑）|

---

## 三、逐項分析

### 3.1 `TenantEnabledCache` — ✅ 已處理（參考實作）

**內容**：以 `ConcurrentHashMap.newKeySet()` 保存「目前被停用的租戶 ID」集合，供高頻路徑 [`TenantInterceptor`](../../../backend/src/main/java/com/taipei/iot/tenant/TenantInterceptor.java) 在每個請求上判定 tenant 是否被禁用。

**多實例同步機制**（[README.md](README.md) Pattern 2 的標準實作）：

1. **寫入路徑**：`markDisabled / markEnabled` 寫 DB 後 `convertAndSend` 至 Redis channel，所有 Pod 訂閱 `TenantEnabledCache.handleEvent(...)` 各自更新本機集合；發送者用 `podId` 過濾掉自己發出的事件，避免重複套用。
2. **訂閱**：透過 [`RedisConfig.redisMessageListenerContainer`](../../../backend/src/main/java/com/taipei/iot/config/RedisConfig.java) 註冊。
3. **啟動 warm-up**：`@PostConstruct` 從 DB 讀取所有 `enabled=false` 的 tenant；warm-up 失敗時 fail-open（log + 空集合啟動），不再把 DB 全表掃描成本轉嫁給第一個請求。
4. **兜底校正**：`@Scheduled(fixedRateString = "${tenant.cache.refresh-interval-ms:300000}")` 每 5 分鐘以「差集（`toAdd` / `toRemove`）」方式從 DB 重算，避免 Pub/Sub 訊息遺失、DB 直改、Flyway migration 與未來批次工具繞過本元件；refresh 失敗不會清空 cache。
5. **無 Redis 降級**：`ObjectProvider<StringRedisTemplate>.getIfAvailable()` 取不到 bean 時自動切 local-only 模式並印 `WARN`，仍能正常啟動。

**剩餘風險**：

| 風險 | 說明 | 評估 |
|---|---|---|
| 無 Redis 時的漂移 | 完全沒有 Redis 環境下，多 Pod 間最長 5 分鐘漂移（依 `@Scheduled` 週期） | 已 log `WARN`；部署時應啟用 Redis |
| Pub/Sub 訊息遺失 | Redis Pub/Sub 為 fire-and-forget，subscriber 斷線期間訊息丟失 | 由 `@Scheduled` 校正兜底 |
| DB 直改 | 管理員 / Flyway 直接 `UPDATE tenant SET enabled = ...` | 由 `@Scheduled` 校正兜底（最長 5 分鐘） |

**結論**：本元件作為 Pattern 2 的參考實作，未來新增類似 cache 可比照其結構。

---

### 3.2 `PasswordPolicyResolver` — ✅ 已處理（移除 cache，2026-05-27）

**原狀**：曾以 `Map<tenantId, CacheEntry>` 保存 per-tenant 解析後的 `PasswordPolicy`，每筆 60 s TTL，`evict()` 只清本節點。多實例下最長 60 s 漂移——管理員在 Pod-A 把 `minLength` 從 8 升到 12 時，使用者在 Pod-B 仍可成功設定 8 碼密碼，為安全風險。

**決策**：評估實際使用情境後採用「方案 B：完全移除 cache」。

- **使用 QPS 極低**：只在密碼寫入路徑被呼叫（使用者改密碼、管理員建立 / 重設、自助註冊），並不在登入驗證 hash 路徑。
- **資料量極小**：每 tenant 類似 15 個 key，DB 查詢 ~1 ms。
- **添加 cache 是猜豫** — 根據原來的 [JavaDoc](../../../backend/src/main/java/com/taipei/iot/auth/policy/PasswordPolicyResolver.java) 註解，cache 是「對齊 `TenantEnabledCache` convention」加上的，而非實測需求。

**變更點**：

1. [`PasswordPolicyResolver`](../../../backend/src/main/java/com/taipei/iot/auth/policy/PasswordPolicyResolver.java)：刪除內部 `Map<String, CacheEntry>` 欄位、`CacheEntry` record、`CACHE_TTL_MILLIS` 常數、`evict()` API；`resolve()` 仍採「tenant override → platform default → hard-coded」三階 fallback，但每次都走 [`PasswordPolicyDao`](../../../backend/src/main/java/com/taipei/iot/auth/policy/PasswordPolicyDao.java)。JavaDoc 明記「No in-process cache」及理由。
2. [`PasswordPolicyService`](../../../backend/src/main/java/com/taipei/iot/auth/policy/PasswordPolicyService.java)：`updatePlatformDefault` / `updateTenantOverride` / `deleteTenantOverride` 三個寫入路徑刪除 `resolver.evict(...)` 呼叫；類 JavaDoc 同步更新。
3. 測試：
   - [`PasswordPolicyResolverTest`](../../../backend/src/test/java/com/taipei/iot/auth/policy/PasswordPolicyResolverTest.java)：移除 `evictPlatform_clearsAllCaches` 與 `resolve_cachesResult_andEvictForcesReload` 兩則，新增 `resolve_hitsDaoEveryCall_noInProcessCache` 鎖住「多次 resolve 均呼叫 DAO」以防未來誤加回 cache。4 tests 全綠。
   - [`PasswordPolicyServiceTest`](../../../backend/src/test/java/com/taipei/iot/auth/policy/PasswordPolicyServiceTest.java)：移除三處 `verify(resolver).evict(...)`。12 tests 全綠。
4. 全量回歸：`mvn test` 583 tests / 0 fail / 0 err。

**狀態**：✅ 已處理，多實例一致性問題自動消失。

---

### 3.3 `RateLimitInterceptor.localFallback` — ⚠️ 設計上接受

**內容**：以 `Map<key, long[]>` 維持本機滑動窗口計數，**僅在 Redis 不可用時啟用**，作為主路徑（Redis `INCR` + TTL）的降級求生機制。

**問題**：當 Redis 全斷時，每顆 Pod 各自獨立計數 → 等於 **N 倍 quota** 被放出去；攻擊者只要刷新連線挑不同 Pod，就能突破上限。

**為何不修**：

- 這是降級路徑，「能限流多少算多少」優於「完全不限流」。
- 修法只有兩條：(a) Redis 斷線時直接拒絕所有請求 → DoS 自己；(b) 改用更重的協調基礎設施（如 Hazelcast / Apache Ignite） → 為了極少發生的 Redis 全斷導入複雜度，CP 值低。

**建議補強（觀測 / 文件，非修 cache）**：

1. 進入 fallback 時持續打 `WARN` log（目前已有），追加一個 **metrics counter**（如 `ratelimit_fallback_active`）以便監控告警可訂閱。
2. 部署文件須註明：「Redis 一旦斷線，rate limit 等於同時被乘以 Pod 數量」。

**優先級**：🟢 P3（已知 trade-off，補觀測即可）

---

### 3.4 `MenuService.allMenusCache` — ✅ 已處理（移除 cache，2026-05-29）

**原狀**：以 `volatile List<MenuEntity>` 保存全域選單清單，`invalidateMenuCache()` 僅清本節點的 `allMenusCache = null`。多實例部署下 Pod-A 修改選單後，Pod-B / Pod-C 仍持有舊 cache，導致使用者看到不一致的選單樹。

**決策**：採用方案 A「完全移除 cache」。

- **使用 QPS 極低**：`getMyMenus()` 僅在登入後前端載入一次，不在每個 API 請求的 hot path 上。
- **資料量極小**：選單 < 200 筆，`findAllByOrderBySortOrder()` 查詢 ~1 ms。
- **與 `PasswordPolicyResolver`（§3.2）同一類論點**：cache 帶來的多實例一致性問題遠大於省下的 DB 查詢成本。

**變更點**：

1. [`MenuService.java`](../../../backend/src/main/java/com/taipei/iot/rbac/service/MenuService.java)：移除 `volatile List<MenuEntity> allMenusCache` 欄位、`getAllMenusCached()` 方法、`invalidateMenuCache()` 方法；`getMenuTree()` / `getMyMenus()` 改為直接呼叫 `menuRepository.findAllByOrderBySortOrder()`；`createMenu` / `updateMenu` / `deleteMenu` / `toggleVisible` 移除 `invalidateMenuCache()` 呼叫。
2. [`MenuServiceCacheTest.java`](../../../backend/src/test/java/com/taipei/iot/rbac/service/MenuServiceCacheTest.java)：改為驗證「無 in-process cache」的 4 個測試：
   - `getMenuTree_shouldQueryDbEveryCall_noInProcessCache` — 每次呼叫均觸發 DB 查詢
   - `menuService_shouldNotHaveVolatileCacheField` — 反射驗證無 cache field
   - `menuService_shouldNotHaveInvalidateMethod` — 反射驗證無 invalidate 方法
   - `getMenuTree_afterUpdate_shouldReturnFreshData` — DB 資料更新後立即可見
3. 4 tests 全綠（BUILD SUCCESS）。

**狀態**：✅ 已處理，多實例一致性問題自動消失。

---

## 四、總結與行動清單

| 項目 | 狀態 | 建議行動 | 優先級 |
|---|---|---|---|
| `TenantEnabledCache` | ✅ 已處理（Pattern 2） | 無；作為參考實作 | — |
| `PasswordPolicyResolver` | ✅ 已處理（2026-05-27 移除 cache）| 無；如未來實測證明為熱路徑再採 Pattern 2 | — |
| `MenuService.allMenusCache` | ✅ 已處理（2026-05-29 移除 cache）| 無；選單資料量小且僅登入後載入一次 | — |
| `RateLimitInterceptor.localFallback` | ⚠️ 設計上接受 | 補 metrics + 部署文件 | 🟢 P3 |

---

## 五、新增 cache 時的檢查清單

照抄自 [README.md §5.4](README.md#54-pr-review-checklist)，新增任何 `ConcurrentHashMap` / `Set<>` / `Map<>` / `@Cacheable` 時：

1. 對照 [README.md §二 決策樹](README.md#二決策樹什麼時候需要同步) 判定是否真的需要 cache、需要哪種同步策略。
2. 若選 Pattern 2（本機 + Pub/Sub），請比照 `TenantEnabledCache` 結構：
   - `@PostConstruct` warm-up（含 fail-open）。
   - `convertAndSend` 寫入後廣播，含 `podId` self-filter。
   - `@Scheduled` 差集校正兜底。
   - `ObjectProvider<StringRedisTemplate>` 取不到時降級 local-only + WARN。
3. 啟動時若處於「單實例假設」，**必須** log `WARN` 並交叉引用本文件。
4. 在本文件 §二 表格中新增一列，標明同步狀態與優先級。
5. 對應的單元測試請涵蓋：warm-up 成功 / warm-up 失敗、publish 自我過濾、handleEvent 套用遠端事件、scheduled 差集校正、降級 local-only。可參考 [`TenantEnabledCacheTest`](../../../backend/src/test/java/com/taipei/iot/tenant/TenantEnabledCacheTest.java)（13 tests 全綠）。
