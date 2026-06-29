package com.taipei.iot.workflow.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "啟動流程實例的請求")
public record WorkflowStartRequest(
		@Schema(description = "流程定義代碼，例如 asset_transfer", example = "asset_transfer") @NotBlank String workflowCode,

		@Schema(description = "關聯業務單據 ID，例如資產移轉的 assetTransferId", example = "42") @NotBlank String businessId,

		@Schema(description = "業務類型，例如 ASSET_TRANSFER", example = "ASSET_TRANSFER") @NotBlank String businessType,

		@Schema(description = "申請人所屬部門 ID（可選）", example = "10") String departmentId,

		@Schema(description = "區域代碼（可選）", example = "TPE") String district,

		@Schema(description = "合約 ID（可選）", example = "C-2024-001") String contractId) {

}
