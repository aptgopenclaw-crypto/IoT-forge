package com.taipei.iot.eventrule.dto;

import java.time.LocalDateTime;
import java.util.Map;

import com.taipei.iot.eventrule.entity.EventRuleTriggerLog;

/**
 * 觸發記錄回應 DTO。
 */
public record EventRuleTriggerLogResponse(

		Long id, String tenantId, Long ruleId, Long deviceId, LocalDateTime triggeredAt, String severity,
		Map<String, Object> matchedValues

) {

	public static EventRuleTriggerLogResponse from(EventRuleTriggerLog log) {
		return new EventRuleTriggerLogResponse(log.getId(), log.getTenantId(), log.getRuleId(), log.getDeviceId(),
				log.getTriggeredAt(), log.getSeverity(), log.getMatchedValues());
	}

}
