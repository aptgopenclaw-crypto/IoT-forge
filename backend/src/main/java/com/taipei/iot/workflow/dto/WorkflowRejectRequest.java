package com.taipei.iot.workflow.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(description = "審核退回的請求")
public record WorkflowRejectRequest(@Schema(description = "流程實例 ID", example = "1") @NotNull Long instanceId,

		@Schema(description = "退回目標步驟 ID，必須符合流程定義中該步驟的 rejectTarget 白名單",
				example = "step_applicant") @NotBlank String targetStepId,

		@Schema(description = "退回原因（可選）", example = "資料不完整，請補齊附件。") String comment) {

}
