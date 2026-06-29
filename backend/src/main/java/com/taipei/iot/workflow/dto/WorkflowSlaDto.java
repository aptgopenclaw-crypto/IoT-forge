package com.taipei.iot.workflow.dto;

import com.taipei.iot.workflow.model.WorkflowAction;
import com.taipei.iot.workflow.model.WorkflowStatus;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 流程實例 SLA／時效 KPI 回應。
 * <ul>
 * <li>{@link #slaTotalDays} — 流程定義各步驟 {@code sla_days} 加總；任一步驟未設定則為 null</li>
 * <li>{@link #actualDays} — 實際耗費天數：已完結者取 completedAt − createdAt，進行中者取 now −
 * createdAt</li>
 * <li>{@link #overdue} — actualDays 超過 slaTotalDays 則為 true</li>
 * </ul>
 */
public record WorkflowSlaDto(

		Long instanceId, String businessId, String businessType, WorkflowStatus status, LocalDateTime createdAt,
		LocalDateTime completedAt,

		/** 全流程 SLA 天數（各步驟 slaDays 加總），任一步驟無設定則為 null */
		Integer slaTotalDays,

		/** 實際花費天數（小數，精確到小時） */
		Double actualDays,

		/** 是否已超過 SLA 時效 */
		boolean overdue,

		/** 各步驟 SLA 明細 */
		List<StepSlaDto> steps

) {

	/**
	 * 單一流程步驟的 SLA 明細。
	 */
	public record StepSlaDto(

			String stepId, String stepName, String assigneeUserId, WorkflowAction action,

			/** 此步驟的 SLA 天數，來自流程定義；null 表示此步驟不設時效 */
			Integer slaDays,

			/** 實際花費天數（小數）；步驟尚未完成則為 null */
			Double actualDays,

			/** 是否超過此步驟 SLA；slaDays 或 actualDays 任一為 null 時為 false */
			boolean overdue,

			LocalDateTime enteredAt, LocalDateTime completedAt

	) {
	}
}
