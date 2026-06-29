package com.taipei.iot.workflow.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "取消流程的請求。僅申請人可在流程進行中取消。")
public record WorkflowCancelRequest(@Schema(description = "流程實例 ID", example = "1") @NotNull Long instanceId,

		@Schema(description = "取消原因（可選）", example = "申請內容有誤，重新申請。") String comment) {

}
