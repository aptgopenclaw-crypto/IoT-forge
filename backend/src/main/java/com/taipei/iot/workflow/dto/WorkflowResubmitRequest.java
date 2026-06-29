package com.taipei.iot.workflow.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "補件重送的請求。流程將回到最後一次執行退回操作的審核步驟。")
public record WorkflowResubmitRequest(@Schema(description = "流程實例 ID", example = "1") @NotNull Long instanceId,

		@Schema(description = "補件說明（可選）", example = "已補齊所有附件，請重新審核。") String comment) {

}
