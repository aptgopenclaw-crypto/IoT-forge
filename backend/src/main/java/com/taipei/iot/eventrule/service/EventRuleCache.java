package com.taipei.iot.eventrule.service;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import com.taipei.iot.eventrule.entity.EventRule;
import com.taipei.iot.eventrule.repository.EventRuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 規則清單快取（以 {@code tenantId:deviceType} 為 key 的記憶體 Map）。
 *
 * <p>
 * 規則查詢量遠大於異動頻率，故每次遙測進來時從快取取，不打 DB。 規則 CRUD / 啟停 操作時呼叫 {@link #invalidate} 或
 * {@link #invalidateAll} 清除。
 */
@Component
@RequiredArgsConstructor
public class EventRuleCache {

	private final EventRuleRepository ruleRepository;

	private final ConcurrentHashMap<String, List<EventRule>> cache = new ConcurrentHashMap<>();

	/**
	 * 取得指定租戶 + 設備型別的啟用規則（快取未命中時從 DB 載入）。
	 */
	public List<EventRule> getRules(String tenantId, String deviceType) {
		return cache.computeIfAbsent(cacheKey(tenantId, deviceType),
				k -> ruleRepository.findByTenantIdAndDeviceTypeAndEnabledTrue(tenantId, deviceType));
	}

	/** 清除指定租戶 + 設備型別的快取（規則 CRUD 後呼叫）。 */
	public void invalidate(String tenantId, String deviceType) {
		cache.remove(cacheKey(tenantId, deviceType));
	}

	/** 清除指定租戶所有設備型別的快取。 */
	public void invalidateAll(String tenantId) {
		String prefix = tenantId + ":";
		cache.keySet().removeIf(k -> k.startsWith(prefix));
	}

	private String cacheKey(String tenantId, String deviceType) {
		return tenantId + ":" + deviceType;
	}

}
