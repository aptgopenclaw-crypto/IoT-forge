package com.taipei.iot.assettransfer.dto;

import com.taipei.iot.workflow.entity.WorkflowStepLogEntity;
import com.taipei.iot.workflow.model.WorkflowAction;

import java.time.LocalDateTime;

/**
 * 簽核軌跡 DTO。
 * <p>
 * 包裝 {@link WorkflowStepLogEntity} 的資料，並將 assigneeUserId 解析為顯示名稱後回傳給前端。
 */
public record WorkflowStepLogDto(Long id, String stepId, String stepName, String assigneeUserId, String assigneeName,
		WorkflowAction action, String comment, String targetStepId, LocalDateTime enteredAt,
		LocalDateTime completedAt) {

	public static WorkflowStepLogDto from(WorkflowStepLogEntity entity, String assigneeName) {
		return new WorkflowStepLogDto(entity.getId(), entity.getStepId(), entity.getStepName(),
				entity.getAssigneeUserId(), assigneeName, entity.getAction(), entity.getComment(),
				entity.getTargetStepId(), entity.getEnteredAt(), entity.getCompletedAt());
	}

}
