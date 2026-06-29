# 02 — 實作 Pattern 與範例程式碼

> 上層文件：[README.md](README.md) | 設計理據：[01-cache-strategy.md](01-cache-strategy.md)

本文提供可直接複製貼上的 implementation pattern，搭配 taipei-iot 現有技術棧（Spring Boot 3.4.1、Lombok、Caffeine、Redis、Micrometer）。

---

## Pattern 1：Caffeine 本機 cache（策略 B）

### 1.1 通用設定 Bean

```java
@Configuration
public class CacheConfig {

    @Bean
    public CacheManager localCacheManager(MeterRegistry meterRegistry) {
        CaffeineCacheManager manager = new CaffeineCacheManager();
        manager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterWrite(Duration.ofSeconds(30))
                .recordStats());  // 必開，Micrometer 才能讀
        // 註冊到 Micrometer 之後可在 /actuator/metrics 看到 cache.* 指標
        return manager;
    }
}
```

### 1.2 標註使用

```java
@Service
@RequiredArgsConstructor
public class DepartmentService {

    private final DepartmentRepository repository;

    @Cacheable(cacheNames = "departments", key = "#tenantId")
    public List<DepartmentDto> listByTenant(String tenantId) {
        return repository.findByTenantId(tenantId).stream()
                .map(this::toDto)
                .toList();
    }

    @CacheEvict(cacheNames = "departments", key = "#tenantId")
    public void onDepartmentChanged(String tenantId) { /* no-op */ }
}
```

### 1.3 注意事項

- `@CacheEvict` 只清「**當前實例**」的 cache → 多實例下其他 Pod 仍 stale，靠 TTL 自然收斂
- 若需立即跨實例失效 → 升級到 Pattern 2

---

## Pattern 2：Redis Pub/Sub 廣播 cache invalidation（策略 C）

### 2.1 共用 Redis 設定

```java
@Configuration
public class RedisPubSubConfig {

    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        return container;
    }
}
```

### 2.2 Cache 實作骨架

```java
@Component
@Slf4j
@RequiredArgsConstructor
public class TenantEnabledCache {

    private static final String CHANNEL = "iot.tenant.enabled.changed";
    /** 兜底 refresh 間隔；防 Pub/Sub 訊息遺失 */
    private static final Duration REFRESH_INTERVAL = Duration.ofMinutes(5);

    private final Set<String> disabledTenantIds = ConcurrentHashMap.newKeySet();
    private final TenantRepository tenantRepository;
    private final StringRedisTemplate redisTemplate;
    private final RedisMessageListenerContainer listenerContainer;
    private final ObjectMapper objectMapper;
    /** 本 Pod 的識別碼，用來忽略自己的 publish 訊息 */
    private final String podId = UUID.randomUUID().toString();

    @PostConstruct
    void init() {
        warmUp();
        listenerContainer.addMessageListener(
                (message, pattern) -> handleEvent(message.getBody()),
                new ChannelTopic(CHANNEL)
        );
        log.info("[TenantEnabledCache] Initialized as podId={}, loaded {} disabled tenants",
                podId, disabledTenantIds.size());
    }

    /** 定期重新校正，兜底 Pub/Sub 訊息遺失 */
    @Scheduled(fixedRateString = "PT5M")
    void refresh() {
        Set<String> latest = loadDisabledFromDb();
        disabledTenantIds.clear();
        disabledTenantIds.addAll(latest);
    }

    public boolean isTenantDisabled(String tenantId) {
        return disabledTenantIds.contains(tenantId);
    }

    public void markDisabled(String tenantId) {
        disabledTenantIds.add(tenantId);
        publish(new ChangeEvent(podId, tenantId, false));
    }

    public void markEnabled(String tenantId) {
        disabledTenantIds.remove(tenantId);
        publish(new ChangeEvent(podId, tenantId, true));
    }

    // ────────── 私有方法 ──────────

    private void warmUp() {
        try {
            disabledTenantIds.addAll(loadDisabledFromDb());
        } catch (Exception e) {
            log.error("[TenantEnabledCache] Warm up failed; starting with empty set", e);
        }
    }

    private Set<String> loadDisabledFromDb() {
        return tenantRepository.findAll().stream()
                .filter(t -> !Boolean.TRUE.equals(t.getEnabled()))
                .map(TenantEntity::getTenantId)
                .collect(Collectors.toSet());
    }

    private void publish(ChangeEvent event) {
        try {
            redisTemplate.convertAndSend(CHANNEL, objectMapper.writeValueAsString(event));
        } catch (Exception e) {
            log.error("[TenantEnabledCache] Failed to publish change event {}", event, e);
        }
    }

    private void handleEvent(byte[] body) {
        try {
            ChangeEvent event = objectMapper.readValue(body, ChangeEvent.class);
            // 忽略自己 publish 的訊息（本地已更新過）
            if (podId.equals(event.podId())) {
                return;
            }
            if (event.enabled()) {
                disabledTenantIds.remove(event.tenantId());
            } else {
                disabledTenantIds.add(event.tenantId());
            }
            log.debug("[TenantEnabledCache] Synced from podId={} tenantId={} enabled={}",
                    event.podId(), event.tenantId(), event.enabled());
        } catch (Exception e) {
            log.error("[TenantEnabledCache] Failed to handle change event", e);
        }
    }

    record ChangeEvent(String podId, String tenantId, boolean enabled) {}
}
```

### 2.3 測試重點

```java
@SpringBootTest
class TenantEnabledCacheIntegrationTest {

    @Test
    void publish_event_should_invalidate_other_instances() {
        // 模擬 Pod A 與 Pod B（同 JVM 但兩個 cache 實例）
        TenantEnabledCache podA = createCacheInstance();
        TenantEnabledCache podB = createCacheInstance();

        podA.markDisabled("T1");

        // 等待 pub/sub 廣播（< 100ms）
        await().atMost(Duration.ofSeconds(1))
                .untilAsserted(() -> assertTrue(podB.isTenantDisabled("T1")));
    }

    @Test
    void self_published_event_should_be_ignored() { /* ... */ }

    @Test
    void scheduled_refresh_should_recover_from_lost_event() { /* ... */ }
}
```

---

## Pattern 3：Redis 集中式 cache（策略 D）

### 3.1 適合 Rate Limit

```java
@Component
@RequiredArgsConstructor
public class RateLimitService {

    private final StringRedisTemplate redisTemplate;

    /**
     * Sliding window rate limit using Redis INCR + EXPIRE.
     * @return true if request is allowed
     */
    public boolean tryAcquire(String key, int limit, Duration window) {
        String redisKey = "ratelimit:" + key + ":" + (Instant.now().getEpochSecond() / window.toSeconds());
        Long count = redisTemplate.opsForValue().increment(redisKey);
        if (count != null && count == 1L) {
            redisTemplate.expire(redisKey, window);
        }
        return count != null && count <= limit;
    }
}
```

### 3.2 適合 Distributed Lock

```java
@Component
@RequiredArgsConstructor
public class DistributedLock {

    private final StringRedisTemplate redisTemplate;

    public boolean tryLock(String key, Duration ttl) {
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent("lock:" + key, UUID.randomUUID().toString(), ttl);
        return Boolean.TRUE.equals(acquired);
    }

    public void unlock(String key) {
        redisTemplate.delete("lock:" + key);
    }
}
```

> ⚠️ 上述為簡化版。Production 等級的 lock（含 token 驗證、Redlock）建議用 [Redisson](https://github.com/redisson/redisson)。

---

## Pattern 4：Spring Cloud Bus（策略 C 的高階封裝）

如果未來決定全面引入 Spring Cloud Bus，cache invalidation 可簡化為：

```java
// 1. pom.xml
// <dependency>
//     <groupId>org.springframework.cloud</groupId>
//     <artifactId>spring-cloud-starter-bus-redis</artifactId>
// </dependency>

// 2. 自訂事件
public class TenantEnabledChangeEvent extends RemoteApplicationEvent {
    private String tenantId;
    private boolean enabled;
    // constructors / getters
}

// 3. 廣播
@Component
@RequiredArgsConstructor
public class TenantEnabledCache implements ApplicationEventPublisherAware {
    private ApplicationEventPublisher publisher;
    private final String contextId = ...;

    public void markDisabled(String tenantId) {
        disabledTenantIds.add(tenantId);
        publisher.publishEvent(new TenantEnabledChangeEvent(this, contextId, null, tenantId, false));
    }

    @EventListener
    public void onChange(TenantEnabledChangeEvent event) {
        // 自動只收到「其他 instance」publish 的事件，不會收到自己的
        if (event.isEnabled()) {
            disabledTenantIds.remove(event.getTenantId());
        } else {
            disabledTenantIds.add(event.getTenantId());
        }
    }
}
```

優點：標準化、有 actuator endpoint 控制；缺點：額外依賴重、學習曲線陡。本專案規模目前未必需要。

---

## Pattern 5：啟動 log 提醒（所有「單實例假設」必備）

```java
@PostConstruct
void warnSingleInstanceAssumption() {
    log.warn(
        "============================================================\n" +
        "[SingleInstanceAssumption] {} is a LOCAL in-memory cache.\n" +
        "DO NOT deploy with replicas > 1 without migrating to\n" +
        "Pub/Sub (Pattern 2) or Redis (Pattern 3).\n" +
        "See: 01-docs/new-feature/cache/README.md\n" +
        "============================================================",
        this.getClass().getSimpleName()
    );
}
```

---

## Pattern 6：Cache 監控（Micrometer）

### 6.1 Caffeine 自動接 Micrometer

```java
@Bean
public Cache<String, Object> myCache(MeterRegistry registry) {
    Cache<String, Object> cache = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(Duration.ofMinutes(5))
            .recordStats()
            .build();
    CaffeineCacheMetrics.monitor(registry, cache, "myCache");
    return cache;
}
```

### 6.2 Pub/Sub 同步延遲自訂 metric

```java
private final Timer syncLagTimer;

public RateLimitService(MeterRegistry registry) {
    this.syncLagTimer = Timer.builder("cache.sync.lag")
            .tag("cache", "tenantEnabled")
            .register(registry);
}

private void handleEvent(byte[] body) {
    ChangeEvent event = parse(body);
    long lagMs = System.currentTimeMillis() - event.publishedAt();
    syncLagTimer.record(lagMs, TimeUnit.MILLISECONDS);
    // ... 處理
}
```

### 6.3 Grafana / Prometheus alarm 建議

| Alert | 條件 | 嚴重性 |
|---|---|---|
| Cache hit rate 過低 | `< 50%` 持續 10 分鐘 | Warning |
| Cache 同步延遲過高 | `p95 > 5s` 持續 5 分鐘 | Critical（C 策略）|
| Cache 同步失敗計數 | `> 0` | Warning |
| Cache 大小異常 | `> 80%` of maximumSize | Warning |

---

## 附錄 A：常見錯誤訊息與解法

| 錯誤 | 原因 | 解法 |
|---|---|---|
| `Cannot get Jedis connection` | Redis 故障或網路斷 | 加 fallback（B 策略兜底），不要硬擋業務 |
| `Class is not in the trusted packages` | Jackson `@class` 反序列化阻擋 | 用 record + 手動 serialize 取代 `GenericJackson2JsonRedisSerializer` |
| Pub/Sub 收不到訊息 | listener container 未啟動 / channel 名拼錯 | 檢查 `RedisMessageListenerContainer` 是否 `@Bean` 且呼叫 `addMessageListener` |
| 「自己 publish 的訊息自己也會收到」 | Pub/Sub 機制本身 | 加 `podId` 過濾，或設計成 idempotent |

---

## 附錄 B：技術選型參考

| 函式庫 | 用途 | 本專案是否已引入 |
|---|---|---|
| Caffeine | 本機 cache | ⚠️ 待確認 |
| Spring Data Redis | Redis client | ✅ 已引入（JWT revocation 用）|
| Redisson | 高階 Redis 操作（Distributed Lock、RMap）| ❌ 未引入 |
| Spring Cloud Bus | 跨實例事件廣播 | ❌ 未引入 |
| Hazelcast / Infinispan | 分散式 cache | ❌ 未引入（暫不考慮）|

新增依賴前請評估維護成本與替代方案。
