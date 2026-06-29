package com.taipei.iot.tenant;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.Topic;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 純 Mockito 單元測試（不啟動 Spring context）。涵蓋 T-3 / T-7 / T-12 修復後的所有路徑。
 *
 * <ul>
 * <li>T-7: warm-up 在 init() 時 eager 載入；warm-up 失敗時 fail-open</li>
 * <li>T-3: markDisabled/markEnabled 發布 Pub/Sub 事件；handleEvent 忽略自己的訊息； handleEvent
 * 套用遠端訊息</li>
 * <li>T-12: scheduled refresh 以差集方式從 DB 校正（不會清空快取）</li>
 * <li>降級：Redis 不可用時仍能運作，僅本機生效</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class TenantEnabledCacheTest {

	@Mock
	private TenantRepository tenantRepository;

	@Mock
	private StringRedisTemplate redisTemplate;

	@Mock
	private RedisMessageListenerContainer listenerContainer;

	@Mock
	private ObjectProvider<StringRedisTemplate> redisTemplateProvider;

	@Mock
	private ObjectProvider<RedisMessageListenerContainer> listenerProvider;

	private final ObjectMapper objectMapper = new ObjectMapper();

	private TenantEnabledCache cacheWithRedis() {
		when(redisTemplateProvider.getIfAvailable()).thenReturn(redisTemplate);
		when(listenerProvider.getIfAvailable()).thenReturn(listenerContainer);
		return new TenantEnabledCache(tenantRepository, objectMapper, redisTemplateProvider, listenerProvider);
	}

	private TenantEnabledCache cacheWithoutRedis() {
		when(redisTemplateProvider.getIfAvailable()).thenReturn(null);
		when(listenerProvider.getIfAvailable()).thenReturn(null);
		return new TenantEnabledCache(tenantRepository, objectMapper, redisTemplateProvider, listenerProvider);
	}

	private static TenantEntity tenant(String id, boolean enabled) {
		TenantEntity e = new TenantEntity();
		e.setTenantId(id);
		e.setEnabled(enabled);
		return e;
	}

	// ───────── T-7: warm-up ─────────

	@Test
	void init_warmsUpAndSubscribes() {
		when(tenantRepository.findAll())
			.thenReturn(List.of(tenant("t-a", true), tenant("t-b", false), tenant("t-c", false)));
		TenantEnabledCache cache = cacheWithRedis();

		cache.init();

		assertThat(cache.snapshotDisabled()).containsExactlyInAnyOrder("t-b", "t-c");
		verify(listenerContainer).addMessageListener(any(), eq(new ChannelTopic("iot.tenant.enabled.changed")));
	}

	@Test
	void warmUp_failureDoesNotThrow_andCacheStaysEmpty() {
		when(tenantRepository.findAll()).thenThrow(new RuntimeException("DB down"));
		TenantEnabledCache cache = cacheWithRedis();

		// 不應丟出例外（fail-open）
		cache.init();

		assertThat(cache.snapshotDisabled()).isEmpty();
		// 仍然訂閱 Pub/Sub
		verify(listenerContainer).addMessageListener(any(), any(ChannelTopic.class));
	}

	// ───────── T-3: Pub/Sub publish ─────────

	@Test
	void markDisabled_addsLocallyAndPublishes() throws Exception {
		when(tenantRepository.findAll()).thenReturn(List.of());
		TenantEnabledCache cache = cacheWithRedis();
		cache.init();

		cache.markDisabled("t-x");

		assertThat(cache.isTenantDisabled("t-x")).isTrue();
		verify(redisTemplate).convertAndSend(eq("iot.tenant.enabled.changed"), any(String.class));
	}

	@Test
	void markEnabled_removesLocallyAndPublishes() {
		when(tenantRepository.findAll()).thenReturn(List.of(tenant("t-x", false)));
		TenantEnabledCache cache = cacheWithRedis();
		cache.init();
		assertThat(cache.isTenantDisabled("t-x")).isTrue();

		cache.markEnabled("t-x");

		assertThat(cache.isTenantDisabled("t-x")).isFalse();
		verify(redisTemplate).convertAndSend(eq("iot.tenant.enabled.changed"), any(String.class));
	}

	// ───────── T-3: Pub/Sub handleEvent ─────────

	@Test
	void handleEvent_ignoresOwnPodMessage() throws Exception {
		when(tenantRepository.findAll()).thenReturn(List.of());
		TenantEnabledCache cache = cacheWithRedis();
		cache.init();

		// 自己 publish 的事件不應重複套用（這裡偽造一個 enabled=true 事件，若被套用會嘗試 remove；
		// 但驗證行為更穩固的方法是事先 add 一個並驗證沒被移除）
		cache.markDisabled("t-self");
		String payload = objectMapper
			.writeValueAsString(new TenantEnabledCache.ChangeEvent(cache.getPodId(), "t-self", true));

		cache.handleEvent(payload.getBytes());

		// 自己 publish 的 enable 訊息應被忽略，t-self 仍應為 disabled
		assertThat(cache.isTenantDisabled("t-self")).isTrue();
	}

	@Test
	void handleEvent_appliesRemoteDisable() throws Exception {
		when(tenantRepository.findAll()).thenReturn(List.of());
		TenantEnabledCache cache = cacheWithRedis();
		cache.init();
		String payload = objectMapper
			.writeValueAsString(new TenantEnabledCache.ChangeEvent("other-pod", "t-remote", false));

		cache.handleEvent(payload.getBytes());

		assertThat(cache.isTenantDisabled("t-remote")).isTrue();
	}

	@Test
	void handleEvent_appliesRemoteEnable() throws Exception {
		when(tenantRepository.findAll()).thenReturn(List.of(tenant("t-remote", false)));
		TenantEnabledCache cache = cacheWithRedis();
		cache.init();
		assertThat(cache.isTenantDisabled("t-remote")).isTrue();

		String payload = objectMapper
			.writeValueAsString(new TenantEnabledCache.ChangeEvent("other-pod", "t-remote", true));
		cache.handleEvent(payload.getBytes());

		assertThat(cache.isTenantDisabled("t-remote")).isFalse();
	}

	@Test
	void handleEvent_malformedPayloadDoesNotThrow() {
		when(tenantRepository.findAll()).thenReturn(List.of());
		TenantEnabledCache cache = cacheWithRedis();
		cache.init();

		cache.handleEvent("not-json".getBytes());

		assertThat(cache.snapshotDisabled()).isEmpty();
	}

	// ───────── T-12: scheduled refresh ─────────

	@Test
	void refresh_addsNewAndRemovesStale_viaDiff() {
		// 初始狀態：DB 有 t-a (disabled), t-b (disabled)
		when(tenantRepository.findAll()).thenReturn(List.of(tenant("t-a", false), tenant("t-b", false)));
		TenantEnabledCache cache = cacheWithRedis();
		cache.init();
		assertThat(cache.snapshotDisabled()).containsExactlyInAnyOrder("t-a", "t-b");

		// DB 後續變動：t-a 被啟用 (移除)、t-c 被新增停用
		when(tenantRepository.findAll())
			.thenReturn(List.of(tenant("t-a", true), tenant("t-b", false), tenant("t-c", false)));

		cache.refresh();

		assertThat(cache.snapshotDisabled()).containsExactlyInAnyOrder("t-b", "t-c");
	}

	@Test
	void refresh_failureDoesNotClearCache() {
		when(tenantRepository.findAll()).thenReturn(List.of(tenant("t-a", false)));
		TenantEnabledCache cache = cacheWithRedis();
		cache.init();
		assertThat(cache.snapshotDisabled()).containsExactly("t-a");

		when(tenantRepository.findAll()).thenThrow(new RuntimeException("transient DB error"));

		cache.refresh();

		// 失敗時應保留現有 cache，不應因例外而清空
		assertThat(cache.snapshotDisabled()).containsExactly("t-a");
	}

	// ───────── 降級：無 Redis ─────────

	@Test
	void worksWithoutRedis_publishIsNoOp() {
		when(tenantRepository.findAll()).thenReturn(List.of());
		TenantEnabledCache cache = cacheWithoutRedis();

		cache.init();
		cache.markDisabled("t-local");

		assertThat(cache.isTenantDisabled("t-local")).isTrue();
		// 完全沒有跟 Redis / listenerContainer 互動
		verify(redisTemplate, never()).convertAndSend(any(String.class), any());
		verify(listenerContainer, never()).addMessageListener(any(MessageListener.class), any(Topic.class));
	}

	@Test
	void isTenantDisabled_nullReturnsFalse() {
		when(tenantRepository.findAll()).thenReturn(List.of());
		TenantEnabledCache cache = cacheWithRedis();
		cache.init();

		assertThat(cache.isTenantDisabled(null)).isFalse();
	}

	// ───────── 既有 contract: 重複 init() 不會丟例外 ─────────

	@Test
	void init_idempotent() {
		when(tenantRepository.findAll()).thenReturn(List.of(tenant("t-a", false)));
		TenantEnabledCache cache = cacheWithRedis();

		cache.init();
		cache.init();

		assertThat(cache.snapshotDisabled()).containsExactly("t-a");
		// 訂閱了兩次（@PostConstruct 實際上 Spring 只呼叫一次，這裡只驗證不丟例外）
		verify(listenerContainer, times(2)).addMessageListener(any(), any(ChannelTopic.class));
	}

}
