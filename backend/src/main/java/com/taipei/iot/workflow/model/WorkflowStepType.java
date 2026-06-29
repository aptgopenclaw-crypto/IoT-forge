package com.taipei.iot.workflow.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 步驟類型（對應 workflow_definitions.steps_json 中的 {@code "type"} 欄位）。
 * <p>
 * JSON 內以小寫儲存（{@code "normal"} / {@code "end"}）， 透過 {@link JsonProperty} 完成雙向映射。
 */
public enum WorkflowStepType {

	/** 一般審核步驟 */
	@JsonProperty("normal")
	NORMAL,

	/** 結束步驟（流程完成，不建立待辦） */
	@JsonProperty("end")
	END

}
