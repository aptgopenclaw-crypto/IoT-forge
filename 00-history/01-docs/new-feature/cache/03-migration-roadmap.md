# 03 — Cache 遷移路線圖

> 上層文件：[README.md](README.md) | 策略對照：[01-cache-strategy.md](01-cache-strategy.md) | 實作範例：[02-implementation-patterns.md](02-implementation-patterns.md)

本文列出 taipei-iot 後端**現有**的本機 cache 點，以及將其遷移至多實例安全的階段性計畫。

---

## 一、總覽

| 階段 | 目標 | 預估工時 | 對應 review 議題 |
|---|---|---|---|
| **Phase 0** | 文件化、啟動 log 警告、CI 阻擋 | 半天 | Tenant T-3（短期） |
| **Phase 1** | `TenantEnabledCache` 遷至 Pub/Sub 或 Redis | 1~2 天 | Tenant T-3（中長期）、T-7、T-12 |
| **Phase 2** | RateLimit 遷至 Redis | 1 天 | （待開立）|
| **Phase 3** | 全面盤點 `@Cacheable`，制定預設策略 | 2~3 天 | （待開立）|
| **Phase 4** | 引入 Cache 觀測性 dashboard | 1 天 | （持續改善）|

---

## 二、Phase 0：當下立刻能做（無架構變更）

### 2.1 為所有「單實例假設」cache 加啟動 log

對下列檔案各加一段 `@PostConstruct warnSingleInstanceAssumption()`（範例見 [02-implementation-patterns.md](02-implementation-patterns.md) Pattern 5）：

- [ ] `backend/src/main/java/com/taipei/iot/tenant/TenantEnabledCache.java`
- [ ] （盤點後補完整清單）

### 2.2 部署文件加註

- [ ] 在 `deploy/README.md`（如不存在則建立）列出「**禁止 replicas > 1**」清單
- [ ] Helm chart / docker-compose 預設 `replicas: 1`，並在註解標示原因

### 2.3 CI 加靜態檢查（選做）

ArchUnit 規則範例：

```java
@AnalyzeClasses(packages = "com.taipei.iot")
class CacheArchitectureTest {

    @ArchTest
    static final ArchRule local_cache_must_be_documented =
        classes()
            .that().haveSimpleNameEndingWith("LocalCache")
            .should().beAnnotatedWith(SingleInstanceOnly.class)
            .because("Local caches must declare @SingleInstanceOnly to be auditable.");
}
```

---

## 三、Phase 1：`TenantEnabledCache` 重構（P0）

### 3.1 背景

對應 Tenant v2 review：
- **T-3**：多實例下停用 tenant fail-open
- **T-7**：lazy init 把 DB 全表掃描成本轉嫁給第一個請求
- **T-12**：DB 直改不會同步 cache

→ 一次重構同時解掉 3 個議題。

### 3.2 方案選擇

| 方案 | 即時性 | 程式變動 | 運維成本 |
|---|---|---|---|
| **3.2.A** Caffeine + 30s TTL | 30s stale | 小 | 無 |
| **3.2.B** Pub/Sub 廣播 | 秒級 | 中 | Redis（已有）|
| **3.2.C** Redis Set 集中 | 強一致 | 中 | Redis + 每次 RTT |

**建議**：**3.2.B**（Pub/Sub），理由：
- 已有 Redis（JWT revocation 用），運維成本零
- 「停用 tenant」屬於管理操作，QPS 低；廣播成本可忽略
- 讀取走本機 cache，零延遲
- 5 分鐘 scheduled refresh 兜底 Pub/Sub 訊息遺失

### 3.3 實作步驟

```
□ Step 1: 重構 TenantEnabledCache（套用 02 號文件 Pattern 2）
□ Step 2: @PostConstruct 從 lazy 改為 eager warmUp（解 T-7）
□ Step 3: @Scheduled(fixedRate = 5min) refresh 兜底（解 T-12）
□ Step 4: 整合測試覆蓋
   - markDisabled 後 Pod B 應在 1s 內感知
   - Pub/Sub 訊息遺失情境下 5 分鐘內收斂
   - DB 直改後 5 分鐘內收斂
□ Step 5: 移除啟動 log 警告（不再是單實例假設）
□ Step 6: 更新 Tenant v2 doc 將 T-3 / T-7 / T-12 標記為 ✅
□ Step 7: 更新本目錄 README §四 表格
```

### 3.4 風險與回滾

| 風險 | 緩解 |
|---|---|
| Redis 故障導致 cache 廣播失敗 | 廣播失敗時 log 但不擲出；本機 cache 仍可運作（已是舊資料），靠 scheduled refresh 兜底 |
| 訊息序列化版本不相容（rolling deploy） | event 用 record + 顯式欄位；新欄位給 default value |
| 啟動順序問題（Redis container 還沒起） | retry + circuit breaker（Spring Boot 已內建）|

回滾：保留前一版 image，DB 不需 migration → 30 秒可回滾。

---

## 四、Phase 2：RateLimit 遷移（P1）

### 4.1 現狀

當前 `RateLimitInterceptor`（位置：`backend/src/main/java/com/taipei/iot/common/interceptor/RateLimitInterceptor.java`，待確認實作）若使用本機 Map 計數 → 多實例下實際限制 = 設定值 × N。

### 4.2 方案

→ 直接走 **策略 D**（Redis 集中式 counter），範例見 [02-implementation-patterns.md](02-implementation-patterns.md) Pattern 3.1。

### 4.3 實作步驟

```
□ Step 1: 確認現有 RateLimit 實作（本機 Map 或已是 Redis？）
□ Step 2: 若為本機 → 用 INCR + EXPIRE sliding window 重寫
□ Step 3: 測試
   - 單實例壓測：QPS 限制是否符合設定
   - 雙實例壓測：限制不應翻倍
   - Redis 故障時是否 fail-open 或 fail-closed（建議 fail-open + 告警）
□ Step 4: 監控
   - cache.sync.failures > 0 告警
   - rate_limit.exceeded counter
```

---

## 五、Phase 3：`@Cacheable` 全面盤點

### 5.1 盤點清單

執行：
```bash
grep -rn "@Cacheable\|@CacheEvict\|@CachePut" backend/src/main/java/
```

對每個結果填入下表：

| 位置 | Cache 名稱 | 寫入入口 | 即時性需求 | 一致性需求 | 建議策略 |
|---|---|---|---|---|---|
| `XxxService:42` | `permissions` | `@CacheEvict` on `updateRole` | 秒級（撤權）| 高（安全）| **C** |
| ... | ... | ... | ... | ... | ... |

### 5.2 統一 CacheManager 配置

```java
@Configuration
public class CacheConfig {
    @Bean
    public CacheManager cacheManager(MeterRegistry registry) {
        // 不同 cache 用不同 spec
        CaffeineCacheManager manager = new CaffeineCacheManager();
        manager.setCacheSpecification("maximumSize=10000,expireAfterWrite=30s,recordStats");
        // 對安全敏感的 cache 用 Pattern 2（自訂 bean，繞過 CacheManager）
        return manager;
    }
}
```

---

## 六、Phase 4：可觀測性

### 6.1 Metrics 必收清單

- [ ] `cache.size`（每個 cache）
- [ ] `cache.gets{result="hit|miss"}`
- [ ] `cache.evictions`
- [ ] `cache.sync.lag`（C 策略 cache）
- [ ] `cache.sync.failures`（C/D 策略 cache）

### 6.2 Grafana Dashboard 模板

建立一個「Cache Overview」dashboard，panels：
1. 各 cache 的 hit rate（line chart, time series）
2. 各 cache 的 size 趨勢（line chart）
3. Pub/Sub 同步延遲 p50 / p95 / p99
4. 同步失敗計數（counter）
5. Redis 連線狀態（健康度）

### 6.3 Alert 規則

| Alert | 條件 | 處理 SOP |
|---|---|---|
| Cache hit rate < 50% | 持續 10 分鐘 | 檢查 TTL 是否過短、容量是否不足 |
| Cache sync lag p95 > 5s | 持續 5 分鐘 | 檢查 Redis 健康度、網路延遲 |
| Cache sync failures > 0 | 任何 | 檢查 Redis 連線；確認 scheduled refresh 仍運作 |
| Cache size > 80% max | 持續 5 分鐘 | 擴大容量或縮短 TTL |

---

## 七、長期願景

完成 Phase 0~4 後，本專案應達到：

1. ✅ **任何 cache 點都有明確策略標籤**（A/B/C/D），且在程式碼註解中標示
2. ✅ **多實例部署無資料漂移**（C/D 策略覆蓋所有安全敏感 cache）
3. ✅ **單實例假設的 cache** 在啟動 log、部署 manifest、本指南三處交叉警告
4. ✅ **PR review checklist** 強制檢視 cache 策略選擇
5. ✅ **可觀測性 dashboard** 即時呈現各 cache 健康度

---

## 八、檢核清單（給 reviewer 用）

任何包含「新增 cache」的 PR 必須通過以下檢核：

- [ ] 已對照 [README §二 決策樹](README.md#二決策樹什麼時候需要同步)
- [ ] 已選擇策略（A/B/C/D），並在 PR 描述中說明理由
- [ ] 若為策略 A/B（本機）→ 已加 `@PostConstruct` warn log
- [ ] 若為策略 C → 已實作自我訊息忽略 + scheduled refresh 兜底
- [ ] 若為策略 D → 已處理 Redis 故障的 fallback 策略
- [ ] 已新增單元/整合測試
- [ ] 已開啟 `recordStats()` 並接 Micrometer
- [ ] 已更新 [README §四](README.md) 的現有 cache 表格

---

## 九、相關文件

- [README.md](README.md) — 總覽
- [01-cache-strategy.md](01-cache-strategy.md) — 設計策略對照
- [02-implementation-patterns.md](02-implementation-patterns.md) — 實作範例
- [Tenant v2 Code Review](../../code-review/tenant/tenant-module-code-review-v2.md) — T-3 / T-7 / T-12 來源
