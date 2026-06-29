# 01 — Cache 策略對照與設計決策

> 上層文件：[README.md](README.md) — 含決策樹與現況盤點
> 相關文件：[02-implementation-patterns.md](02-implementation-patterns.md) — 實作範例

本文聚焦於「**為什麼選這個策略**」的設計理據。實作範例請看 02 號文件。

---

## 一、核心問題回顧

> **可寫 + 即時性需求 > 0 的 cache，在多實例下會資料漂移。**

```
        ┌─────────────────┐
Req 1 → │  Pod A          │  收到 disable T1 請求
        │  cache: {T1: ❌}│  更新自己 cache + DB
        └─────────────────┘
                ↓ DB
        ┌─────────────────┐
Req 2 → │  Pod B          │  T1 用戶下一個請求進來
        │  cache: {}      │  cache 沒同步 → 仍允許！
        └─────────────────┘   (fail-open)
```

任何「**期望變更立即生效**」的本機 cache，在水平擴展下都是這個劇本。

---

## 二、四種主流策略詳解

### A. 不 cache，每次查 DB

```java
public boolean isTenantDisabled(String tenantId) {
    return tenantRepository.findByTenantId(tenantId)
            .map(t -> !Boolean.TRUE.equals(t.getEnabled()))
            .orElse(false);
}
```

| 維度 | 說明 |
|---|---|
| 即時性 | ✅ 強一致（DB 是唯一真相）|
| 程式複雜度 | ⭐ 最低 |
| 額外基礎設施 | 無 |
| 缺點 | 每次請求都打 DB，QPS 高時可能成為瓶頸 |

**適用**：QPS < 100、查詢 < 10ms 的場景。
**不適用**：身份驗證流程（每個 API 都會碰到）、熱點資料。

---

### B. 本機 cache + 短 TTL（最終一致）

```java
@Component
public class TenantEnabledCache {
    private final Cache<String, Boolean> cache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofSeconds(30))
            .maximumSize(10_000)
            .build();

    private final TenantRepository tenantRepository;

    public boolean isTenantDisabled(String tenantId) {
        return cache.get(tenantId, id ->
            tenantRepository.findByTenantId(id)
                    .map(t -> !Boolean.TRUE.equals(t.getEnabled()))
                    .orElse(false)
        );
    }

    // 寫入時不主動 invalidate；30 秒後自然過期
}
```

| 維度 | 說明 |
|---|---|
| 即時性 | ⚠️ 最壞 `TTL` 秒 stale |
| 程式複雜度 | ⭐⭐ 低 |
| 額外基礎設施 | 無（Caffeine 已是依賴）|
| 缺點 | 變更不即時；安全敏感場景不適用 |

**適用**：菜單、部門、i18n、公告列表（容忍秒級延遲）。
**不適用**：安全黑名單、權限撤銷（需即時生效）。

---

### C. 本機 cache + Pub/Sub 廣播（推薦預設）

```
Pod A ──subscribe──→ Redis ←──subscribe── Pod B
                       ↑
              Pod A publish 變更
                       ↓ 廣播
                  所有 Pod 收到 → 各自更新本機 cache
```

```java
@Component
@RequiredArgsConstructor
public class TenantEnabledCache {

    private static final String CHANNEL = "tenant.enabled.changed";

    private final Set<String> disabledTenantIds = ConcurrentHashMap.newKeySet();
    private final TenantRepository tenantRepository;
    private final StringRedisTemplate redisTemplate;
    private final RedisMessageListenerContainer listenerContainer;
    private final ObjectMapper objectMapper;

    @PostConstruct
    void init() {
        // 1. 啟動時從 DB 預熱
        warmUp();
        // 2. 訂閱變更廣播
        listenerContainer.addMessageListener(
                (message, pattern) -> handleEvent(message.getBody()),
                new ChannelTopic(CHANNEL)
        );
    }

    public boolean isTenantDisabled(String tenantId) {
        return disabledTenantIds.contains(tenantId);
    }

    public void markDisabled(String tenantId) {
        disabledTenantIds.add(tenantId);
        publish(new TenantEnabledEvent(tenantId, false));
    }

    public void markEnabled(String tenantId) {
        disabledTenantIds.remove(tenantId);
        publish(new TenantEnabledEvent(tenantId, true));
    }

    private void publish(TenantEnabledEvent event) {
        try {
            redisTemplate.convertAndSend(CHANNEL, objectMapper.writeValueAsString(event));
        } catch (Exception e) {
            log.error("Failed to publish tenant.enabled.changed event", e);
        }
    }

    private void handleEvent(byte[] body) {
        try {
            TenantEnabledEvent event = objectMapper.readValue(body, TenantEnabledEvent.class);
            if (event.enabled()) {
                disabledTenantIds.remove(event.tenantId());
            } else {
                disabledTenantIds.add(event.tenantId());
            }
        } catch (Exception e) {
            log.error("Failed to handle tenant.enabled.changed event", e);
        }
    }

    record TenantEnabledEvent(String tenantId, boolean enabled) {}
}
```

| 維度 | 說明 |
|---|---|
| 即時性 | ✅ 秒級（通常 < 100ms）|
| 程式複雜度 | ⭐⭐⭐ 中 |
| 額外基礎設施 | Redis |
| 缺點 | Pub/Sub 沒有「保證投遞」；訊息遺失時各 Pod 漂移 → 需配合 TTL 兜底 |

**適用**：本專案大部分需即時生效的 cache（`TenantEnabledCache`、權限、設定）。
**重要**：每個 Pod 都會收到自己 publish 的訊息，邏輯需 idempotent。

---

### D. Redis 集中式 cache（強一致）

```java
@Component
@RequiredArgsConstructor
public class TenantEnabledCache {

    private static final String KEY = "tenant:disabled";  // Redis Set

    private final StringRedisTemplate redisTemplate;
    private final TenantRepository tenantRepository;

    @PostConstruct
    void warmUp() {
        // 啟動時用 DB 重建（多實例下 SET 操作 idempotent）
        Set<String> disabled = tenantRepository.findAll().stream()
                .filter(t -> !Boolean.TRUE.equals(t.getEnabled()))
                .map(TenantEntity::getTenantId)
                .collect(Collectors.toSet());
        if (!disabled.isEmpty()) {
            redisTemplate.opsForSet().add(KEY, disabled.toArray(new String[0]));
        }
    }

    public boolean isTenantDisabled(String tenantId) {
        return Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(KEY, tenantId));
    }

    public void markDisabled(String tenantId) {
        redisTemplate.opsForSet().add(KEY, tenantId);
    }

    public void markEnabled(String tenantId) {
        redisTemplate.opsForSet().remove(KEY, tenantId);
    }
}
```

| 維度 | 說明 |
|---|---|
| 即時性 | ✅ 強一致 |
| 程式複雜度 | ⭐⭐ 低 |
| 額外基礎設施 | Redis |
| 缺點 | 每次查詢多 1 個 round-trip（~1ms）；Redis 故障時 cache 全失效 |

**適用**：強一致需求（共享計數、Lock、Rate Limit）、資料量小、QPS 中等。
**不適用**：QPS 極高且容忍 stale 的場景（會浪費 Redis bandwidth）。

---

## 三、策略選型矩陣

| 情境 | 即時性需求 | 一致性需求 | 推薦策略 |
|---|---|---|---|
| 停用場域生效 | 秒級 | 高（安全）| **C** 或 **D** |
| Rate Limit 計數 | 即時 | **強**（需精確加總）| **D** |
| JWT revocation | 秒級 | 高 | **D**（已實作）|
| 權限撤銷 | 秒級 | 高 | **C** |
| 菜單 / i18n | 分鐘級 | 低 | **B** |
| 部門樹（內部用戶量 < 100K）| 分鐘級 | 中 | **B** |
| 啟動載入的設定 | 重啟才變 | N/A | 不算 cache |
| Distributed Lock | 即時 | 強 | **D**（Redis SETNX / Redlock）|

---

## 四、業界相關名詞對照

| 名稱 | 對應本文哪個策略 | 備註 |
|---|---|---|
| Cache-Aside Pattern | A / B / D 都用此模式 | 「Cache miss 才查 DB；寫入時雙寫 / invalidate」|
| Write-Through Cache | D 的變體 | 寫入時同步寫 cache + DB |
| Write-Behind / Write-Back | （本專案不建議）| 寫 cache 後 async 寫 DB；崩潰會掉資料 |
| Read-Through Cache | B / D | Cache miss 自動載入 |
| Spring Cloud Bus | C 的封裝 | 底層可選 Redis / RabbitMQ / Kafka |
| Distributed Cache | D | Redis / Hazelcast / Infinispan |
| Near Cache | C 的別名 | 本機 cache + 中心同步 |

---

## 五、常見反模式

### ❌ 反模式 1：用 `@Cacheable` 預設配置上線

```java
@Cacheable("permissions")
public List<String> getPermissions(String userId) { ... }
```

Spring 預設用 `ConcurrentMapCacheManager`（純本機、無 TTL、無大小限制）。多實例下：
- 撤權後其他 Pod 仍給舊權限
- 永不過期 → 記憶體洩漏

**修正**：明確配置 Caffeine + TTL + 大小上限（策略 B），或改用 Redis（策略 D）。

### ❌ 反模式 2：用 ConcurrentHashMap 自製 cache + 「應該夠用了吧」

```java
private final Map<String, Object> cache = new ConcurrentHashMap<>();
```

問題：
- 無 TTL、無 eviction → 記憶體洩漏
- 無 metrics → 沒人知道命中率
- 多實例下漂移

**修正**：用 Caffeine（單機）或 Redis（分散式）。

### ❌ 反模式 3：在 cache 寫入後 publish，卻沒處理「自己」收到的訊息

```java
public void markDisabled(String tenantId) {
    disabledTenantIds.add(tenantId);
    publish(...);  // 自己也會收到
}
private void onMessage(event) {
    disabledTenantIds.add(event.tenantId());  // 重複 add 還好（idempotent）
                                               // 但如果是 ++counter 就會錯！
}
```

**修正**：訊息附上 `originPodId`，自己忽略自己；或設計為 idempotent。

### ❌ 反模式 4：Pub/Sub 廣播但沒兜底機制

Redis Pub/Sub **不保證投遞**（subscriber 斷線時的訊息會掉）。

**修正**：搭配 TTL 自動 refresh，或啟動時 + 定期（每 5 分鐘）從 DB 重抓校正。

### ❌ 反模式 5：Cache 與 DB 雙寫順序錯誤

```java
public void disable(String tenantId) {
    cache.markDisabled(tenantId);  // ① 先改 cache
    tenantRepository.save(...);    // ② 後改 DB
    // 如果 ② 失敗 → cache 與 DB 不一致
}
```

**修正**：DB 是 source of truth，**先 DB 後 cache**；cache 更新失敗時 log 但不擲出。

---

## 六、可觀測性必備

任何 cache 都至少要曝出以下 metrics（Spring Actuator / Micrometer）：

| Metric | 用途 |
|---|---|
| `cache.size` | 偵測記憶體洩漏 |
| `cache.gets{result="hit"}` / `cache.gets{result="miss"}` | 命中率 |
| `cache.puts` | 寫入頻率 |
| `cache.evictions` | 是否頻繁驅逐（容量不足）|
| `cache.sync.lag`（自訂）| Pub/Sub 廣播平均延遲 |
| `cache.sync.failures`（自訂）| 廣播失敗數 |

Caffeine 內建 `recordStats()`，可直接接 Micrometer。

---

## 七、本專案的預設選擇

> **新增 cache 時，預設用策略 B（短 TTL local）；除非有明確即時性需求才升級到 C；除非有強一致需求才用 D。**

理由：
1. 大部分業務 cache（部門、菜單、設定）可接受秒級 stale → B 已夠
2. C / D 引入 Redis 依賴與運維成本，需要正當理由
3. A（不 cache）成本最低，QPS 不高時優先考慮

決策時請在 PR 描述中註明：
```
Cache strategy: B (Caffeine + 30s TTL)
Rationale: 部門列表預期 5 分鐘內變更不影響業務功能；多實例下最壞 30s 不一致可接受
```
