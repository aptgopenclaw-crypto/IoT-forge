package com.taipei.iot.eventrule.dto;

import java.time.LocalDateTime;
import java.util.Map;

import com.taipei.iot.eventrule.entity.EventRuleTriggerLog;

/**
 * 觸發記錄回應 DTO。
 */
public record EventRuleTriggerLogResponse(

		Long id, String tenantId, Long ruleId, String ruleName, Long deviceId, String deviceName,
		LocalDateTime triggeredAt, String severity, Map<String, Object> matchedValues

) {

	public static EventRuleTriggerLogResponse from(EventRuleTriggerLog log) {
		return new EventRuleTriggerLogResponse(log.getId(), log.getTenantId(), log.getRuleId(), null, log.getDeviceId(),
				null, log.getTriggeredAt(), log.getSeverity(), log.getMatchedValues());
	}

	public EventRuleTriggerLogResponse withRuleName(String ruleName) {
		return new EventRuleTriggerLogResponse(id, tenantId, ruleId, ruleName, deviceId, deviceName, triggeredAt,
				severity, matchedValues);
	}

	public EventRuleTriggerLogResponse withDeviceName(String deviceName) {
		return new EventRuleTriggerLogResponse(id, tenantId, ruleId, ruleName, deviceId, deviceName, triggeredAt,
				severity, matchedValues);
	}

}
