package com.taipei.iot.workflow.model;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 步驟定義（從 workflow_definitions.steps_json 反序列化）
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class StepDefinition {

	private String id;

	private String name;

	private WorkflowStepType type;

	private String roleCode;

	private String next;

	private String rejectTarget;

	/** 此步驟的 SLA 時效（天數）。為 null 表示此步驟不設時效。 */
	private Integer slaDays;

}