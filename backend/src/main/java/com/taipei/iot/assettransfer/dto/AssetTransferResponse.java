package com.taipei.iot.assettransfer.dto;

import com.taipei.iot.assettransfer.entity.AssetTransferApplicationEntity;
import com.taipei.iot.assettransfer.enums.AssetTransferStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 資產異動申請 API 回應 DTO。
 * <p>
 * 刻意排除 tenantId（多租戶隔離 key，不應暴露給前端） 與 workflowInstanceId（內部流程 DB ID，不應暴露給前端）。
 */
public record AssetTransferResponse(Long id, String applicationNo, String applicantId, String applicantName,
		Long departmentId, String departmentName, String assetCode, String assetName, String transferType,
		Long targetDepartmentId, String reason, BigDecimal assetValue, AssetTransferStatus status,
		String currentAssignee, String currentAssigneeName, LocalDateTime createdAt, String createdBy,
		LocalDateTime updatedAt, LocalDateTime approvedAt, String approvedBy, String rejectReason, boolean canAct) {

	public static AssetTransferResponse from(AssetTransferApplicationEntity entity, String currentAssigneeName,
			boolean canAct) {
		return new AssetTransferResponse(entity.getId(), entity.getApplicationNo(), entity.getApplicantId(),
				entity.getApplicantName(), entity.getDepartmentId(), entity.getDepartmentName(), entity.getAssetCode(),
				entity.getAssetName(), entity.getTransferType(), entity.getTargetDepartmentId(), entity.getReason(),
				entity.getAssetValue(), entity.getStatus(), entity.getCurrentAssignee(), currentAssigneeName,
				entity.getCreatedAt(), entity.getCreatedBy(), entity.getUpdatedAt(), entity.getApprovedAt(),
				entity.getApprovedBy(), entity.getRejectReason(), canAct);
	}

}
