package com.taipei.iot.eventrule.action;

import java.time.Instant;
import java.util.Map;

import com.taipei.iot.eventrule.entity.EventRule;

/**
 * 規則命中後的觸發資訊。
 *
 * @param rule 命中的規則實體
 * @param tenantId 租戶 ID
 * @param deviceId 觸發設備
 * @param ts 遙測資料時間點
 * @param matchedValues 命中當下的遙測值快照
 */
public record RuleMatch(EventRule rule, String tenantId, Long deviceId, Instant ts, Map<String, Object> matchedValues) {
}
