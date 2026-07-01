package com.taipei.iot.eventrule.dto;

import java.util.List;

import com.taipei.iot.eventrule.model.ActionConfig;
import com.taipei.iot.eventrule.model.ConditionNode;
import com.taipei.iot.eventrule.model.RuleScope;
import com.taipei.iot.eventrule.model.TriggerConfig;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 建立 / 更新事件規則的請求 DTO。
 */
public record EventRuleRequest(

		@NotBlank @Size(max = 50) String ruleCode,

		@NotBlank @Size(max = 200) String name,

		/** 規則綁定的設備型別（對應 schema.telemetry）。 */
		@NotBlank @Size(max = 30) String deviceType,

		/** 嚴重度：INFO / WARNING / CRITICAL。 */
		@NotBlank String severity,

		/** 套用範圍（deviceIds 為 null → 型別所有設備）。 */
		RuleScope scope,

		/** 條件樹（必填）。 */
		@NotNull ConditionNode condition,

		/** 觸發語意（必填）。 */
		@NotNull TriggerConfig triggerCfg,

		/** 動作清單（至少一個）。 */
		@NotNull @Size(min = 1) List<ActionConfig> actions

) {
}
