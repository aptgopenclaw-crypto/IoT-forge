package com.taipei.iot.workflow.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "審核同意的請求")
public record WorkflowApproveRequest(@Schema(description = "流程實例 ID", example = "1") @NotNull Long instanceId,

		@Schema(description = "審核意見（可選）", example = "同意，請進行下一步。") String comment) {

}
