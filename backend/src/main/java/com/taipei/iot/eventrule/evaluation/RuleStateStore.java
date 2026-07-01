package com.taipei.iot.eventrule.evaluation;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 規則執行狀態存 Redis，key 格式：{@code evtrule:state:{ruleId}:{deviceId}}。
 *
 * <p>
 * Hash fields：
 * <ul>
 * <li>{@code firstMatchTs} — FOR_DURATION：首次滿足時間（epoch ms）</li>
 * <li>{@code lastResult} — ON_CHANGE：上次評估結果（"true"/"false"）</li>
 * <li>{@code lastTriggerTs} — cooldown：上次實際觸發時間（epoch ms）</li>
 * </ul>
 *
 * 狀態 TTL = max(durationSec, cooldownSec) + 1 天（防止殭屍鍵）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RuleStateStore {

	static final String KEY_PREFIX = "evtrule:state:";

	static final String FIELD_FIRST_MATCH_TS = "firstMatchTs";

	static final String FIELD_LAST_RESULT = "lastResult";

	static final String FIELD_LAST_TRIGGER_TS = "lastTriggerTs";

	private final StringRedisTemplate redisTemplate;

	// ---- cooldown ----

	/** 是否在冷卻中（上次觸發距今 < cooldownSec）。 */
	public boolean isInCooldown(String ruleId, Long deviceId, int cooldownSec) {
		if (cooldownSec <= 0) {
			return false;
		}
		String raw = (String) redisTemplate.opsForHash().get(key(ruleId, deviceId), FIELD_LAST_TRIGGER_TS);
		if (raw == null) {
			return false;
		}
		long lastTriggerMs = Long.parseLong(raw);
		return Instant.now().toEpochMilli() - lastTriggerMs < (long) cooldownSec * 1000;
	}

	/** 記錄觸發時間（冷卻計時起點）。 */
	public void recordTrigger(String ruleId, Long deviceId, int cooldownSec) {
		String k = key(ruleId, deviceId);
		redisTemplate.opsForHash().put(k, FIELD_LAST_TRIGGER_TS, String.valueOf(Instant.now().toEpochMilli()));
		refreshTtl(k, cooldownSec, 0);
	}

	// ---- FOR_DURATION ----

	/** 取得首次滿足時間（null = 尚未記錄）。 */
	public Instant getFirstMatchTs(String ruleId, Long deviceId) {
		String raw = (String) redisTemplate.opsForHash().get(key(ruleId, deviceId), FIELD_FIRST_MATCH_TS);
		return raw == null ? null : Instant.ofEpochMilli(Long.parseLong(raw));
	}

	/** 記錄首次滿足時間（僅當尚未設定時）。 */
	public void setFirstMatchTsIfAbsent(String ruleId, Long deviceId, int durationSec) {
		String k = key(ruleId, deviceId);
		redisTemplate.opsForHash().putIfAbsent(k, FIELD_FIRST_MATCH_TS, String.valueOf(Instant.now().toEpochMilli()));
		refreshTtl(k, 0, durationSec);
	}

	/** 清除首次滿足時間（條件中斷時呼叫）。 */
	public void clearFirstMatchTs(String ruleId, Long deviceId) {
		redisTemplate.opsForHash().delete(key(ruleId, deviceId), FIELD_FIRST_MATCH_TS);
	}

	// ---- ON_CHANGE ----

	/** 取得上次評估結果（null = 無記錄，視為 false）。 */
	public Boolean getLastResult(String ruleId, Long deviceId) {
		String raw = (String) redisTemplate.opsForHash().get(key(ruleId, deviceId), FIELD_LAST_RESULT);
		return raw == null ? null : Boolean.parseBoolean(raw);
	}

	/** 儲存本次評估結果。 */
	public void setLastResult(String ruleId, Long deviceId, boolean result) {
		String k = key(ruleId, deviceId);
		redisTemplate.opsForHash().put(k, FIELD_LAST_RESULT, String.valueOf(result));
		refreshTtl(k, 0, 0);
	}

	// ---- helpers ----

	private String key(String ruleId, Long deviceId) {
		return KEY_PREFIX + ruleId + ":" + deviceId;
	}

	private void refreshTtl(String key, int cooldownSec, int durationSec) {
		long ttlSec = Math.max(cooldownSec, durationSec) + Duration.ofDays(1).toSeconds();
		Long existing = redisTemplate.getExpire(key, TimeUnit.SECONDS);
		if (existing == null || existing < 0 || existing < ttlSec) {
			redisTemplate.expire(key, ttlSec, TimeUnit.SECONDS);
		}
	}

}
