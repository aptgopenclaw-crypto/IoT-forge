package com.taipei.iot.tenant;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 記憶體快取：已停用的 tenantId 集合。用於 {@code JwtAuthenticationFilter} 中即時拒絕 已停用場域的請求，避免每次請求都查詢資料庫。
 *
 * <h3>[Tenant v2 T-3 / T-7 / T-12] 多實例同步策略：本機 cache + Redis Pub/Sub + 定期校正</h3>
 * <p>
 * 實作對應 {@code 01-docs/new-feature/cache/02-implementation-patterns.md} Pattern 2 （本機
 * cache + Pub/Sub 廣播）。設計重點：
 * </p>
 * <ul>
 * <li><b>T-7 修復</b>：{@code @PostConstruct} eager warm-up；不再 lazy init 把 DB 全表掃描
 * 成本轉嫁給第一個請求。warm-up 失敗時 fail-open（log + 空集合啟動），避免循環依賴 導致應用啟動失敗。</li>
 * <li><b>T-3 修復</b>：{@code markDisabled / markEnabled} 透過 Redis Pub/Sub 廣播到所有 訂閱者（含其他
 * Pod），各 Pod 收到後更新本機 cache，達成秒級一致性。發送者用 {@code podId} 過濾自己的訊息避免重複處理。</li>
 * <li><b>T-12 修復</b>：{@code @Scheduled} 每 {@code tenant.cache.refresh-interval-ms}（預設 5
 * 分鐘）從 DB 重新校正，兜底 Pub/Sub 訊息遺失與「DB 直改 / Flyway / 批次工具」 繞過本元件的情境。</li>
 * <li><b>降級策略</b>：若 Redis 不可用（{@code spring.data.redis.host} 未配置），自動 退化為純本機模式並印
 * {@code WARN}；不影響應用啟動。</li>
 * </ul>
 *
 * <p>
 * 對應的單元測試見 {@code TenantEnabledCacheTest}。
 * </p>
 */
@Component
@Slf4j
public class TenantEnabledCache {

	/** Redis Pub/Sub channel。所有 Pod 都訂閱此 channel 同步 tenant enable/disable 事件。 */
	static final String CHANNEL = "iot.tenant.enabled.changed";

	private final Set<String> disabledTenantIds = ConcurrentHashMap.newKeySet();

	private final TenantRepository tenantRepository;

	private final ObjectMapper objectMapper;

	/** 可選依賴；無 Redis 時自動降級為純本機模式 */
	private final StringRedisTemplate redisTemplate;

	private final RedisMessageListenerContainer listenerContainer;

	/** 本 Pod 的識別碼，用來忽略自己 publish 的訊息（避免重複套用） */
	private final String podId = UUID.randomUUID().toString();

	public TenantEnabledCache(TenantRepository tenantRepository, ObjectMapper objectMapper,
			ObjectProvider<StringRedisTemplate> redisTemplateProvider,
			ObjectProvider<RedisMessageListenerContainer> listenerProvider) {
		this.tenantRepository = tenantRepository;
		this.objectMapper = objectMapper;
		this.redisTemplate = redisTemplateProvider.getIfAvailable();
		this.listenerContainer = listenerProvider.getIfAvailable();
	}

	// ───────────────────────── 啟動 ─────────────────────────

	@PostConstruct
	void init() {
		warmUp();
		subscribe();
	}

	/** T-7：eager warm-up；失敗不阻擋啟動 */
	void warmUp() {
		try {
			Set<String> latest = loadDisabledFromDb();
			disabledTenantIds.clear();
			disabledTenantIds.addAll(latest);
			log.info("[TenantEnabledCache] warm-up loaded {} disabled tenants (podId={})", latest.size(), podId);
		}
		catch (Exception e) {
			log.error("[TenantEnabledCache] warm-up failed — starting with empty disabled set "
					+ "(podId={}); scheduled refresh will retry every interval", podId, e);
		}
	}

	/** T-3：訂閱跨實例 cache invalidation 廣播 */
	private void subscribe() {
		if (listenerContainer == null || redisTemplate == null) {
			log.warn("[TenantEnabledCache] Redis unavailable — running in LOCAL-ONLY mode. "
					+ "Multi-instance deployments WILL NOT synchronize tenant enable/disable. "
					+ "See 01-docs/new-feature/cache/README.md");
			return;
		}
		listenerContainer.addMessageListener((message, pattern) -> handleEvent(message.getBody()),
				new ChannelTopic(CHANNEL));
		log.info("[TenantEnabledCache] subscribed to channel '{}' (podId={})", CHANNEL, podId);
	}

	// ───────────────────────── 排程 ─────────────────────────

	/** T-12：定期從 DB 重新校正，兜底 Pub/Sub 訊息遺失與 DB 直改 */
	@Scheduled(fixedRateString = "${tenant.cache.refresh-interval-ms:300000}",
			initialDelayString = "${tenant.cache.refresh-interval-ms:300000}")
	void refresh() {
		try {
			Set<String> latest = loadDisabledFromDb();
			// 計算差集，避免「clear + addAll」造成短暫空窗
			Set<String> toAdd = new HashSet<>(latest);
			toAdd.removeAll(disabledTenantIds);
			Set<String> toRemove = new HashSet<>(disabledTenantIds);
			toRemove.removeAll(latest);

			disabledTenantIds.addAll(toAdd);
			toRemove.forEach(disabledTenantIds::remove);

			if (!toAdd.isEmpty() || !toRemove.isEmpty()) {
				log.info("[TenantEnabledCache] scheduled refresh: +{} -{} (podId={})", toAdd.size(), toRemove.size(),
						podId);
			}
		}
		catch (Exception e) {
			log.error("[TenantEnabledCache] scheduled refresh failed (podId={})", podId, e);
		}
	}

	// ───────────────────────── 對外 API ─────────────────────────

	public boolean isTenantDisabled(String tenantId) {
		if (tenantId == null)
			return false;
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

	// ───────────────────────── 內部 ─────────────────────────

	private Set<String> loadDisabledFromDb() {
		return tenantRepository.findAll()
			.stream()
			.filter(t -> !Boolean.TRUE.equals(t.getEnabled()))
			.map(TenantEntity::getTenantId)
			.collect(Collectors.toSet());
	}

	private void publish(ChangeEvent event) {
		if (redisTemplate == null)
			return; // local-only 模式
		try {
			redisTemplate.convertAndSend(CHANNEL, objectMapper.writeValueAsString(event));
		}
		catch (Exception e) {
			log.error("[TenantEnabledCache] publish failed for event {} (podId={})", event, podId, e);
		}
	}

	/** package-private 以便單元測試直接驅動 */
	void handleEvent(byte[] body) {
		try {
			ChangeEvent event = objectMapper.readValue(body, ChangeEvent.class);
			if (podId.equals(event.podId())) {
				return; // 忽略自己 publish 的訊息（本機已先行更新）
			}
			if (event.enabled()) {
				disabledTenantIds.remove(event.tenantId());
			}
			else {
				disabledTenantIds.add(event.tenantId());
			}
			log.debug("[TenantEnabledCache] synced from podId={} tenantId={} enabled={} (selfPodId={})", event.podId(),
					event.tenantId(), event.enabled(), podId);
		}
		catch (Exception e) {
			log.error("[TenantEnabledCache] failed to handle event (podId={})", podId, e);
		}
	}

	/** for tests */
	String getPodId() {
		return podId;
	}

	/** for tests */
	Set<String> snapshotDisabled() {
		return Set.copyOf(disabledTenantIds);
	}

	/**
	 * Pub/Sub 廣播事件 payload。
	 *
	 * @param podId 發送者 Pod 識別碼（用於 self-filter）
	 * @param tenantId 變更的 tenant
	 * @param enabled true = 啟用、false = 停用
	 */
	record ChangeEvent(String podId, String tenantId, boolean enabled) {
	}

}
