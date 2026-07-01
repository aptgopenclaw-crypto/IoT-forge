package com.taipei.iot.eventrule.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.taipei.iot.eventrule.entity.EventRule;
import com.taipei.iot.eventrule.model.ActionConfig;
import com.taipei.iot.eventrule.model.ConditionNode;
import com.taipei.iot.eventrule.model.RuleScope;
import com.taipei.iot.eventrule.model.TriggerConfig;

/**
 * 事件規則回應 DTO。
 */
public record EventRuleResponse(

		Long id, String tenantId, String ruleCode, String name, String deviceType, boolean enabled, String severity,
		RuleScope scope, ConditionNode condition, TriggerConfig triggerCfg, List<ActionConfig> actions,
		LocalDateTime createTime, LocalDateTime updateTime

) {

	public static EventRuleResponse from(EventRule e) {
		return new EventRuleResponse(e.getId(), e.getTenantId(), e.getRuleCode(), e.getName(), e.getDeviceType(),
				e.isEnabled(), e.getSeverity(), e.getScope(), e.getCondition(), e.getTriggerCfg(), e.getActions(),
				e.getCreateTime(), e.getUpdateTime());
	}

}
