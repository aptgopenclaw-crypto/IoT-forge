package com.taipei.iot.workflow.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 流程步驟指派事件 — 當流程引擎建立新步驟待辦時發布。
 * <p>
 * 訂閱者可據此發送通知（In-App / Email / SMS）給新任審核人。
 */
@Getter
public class WorkflowStepAssignedEvent extends ApplicationEvent {

	private final String tenantId;

	private final String assigneeUserId;

	private final String workflowInstanceId;

	private final String businessType;

	private final String stepId;

	private final String stepName;

	public WorkflowStepAssignedEvent(Object source, String tenantId, String assigneeUserId, Long workflowInstanceId,
			String businessType, String stepId, String stepName) {
		super(source);
		this.tenantId = tenantId;
		this.assigneeUserId = assigneeUserId;
		this.workflowInstanceId = String.valueOf(workflowInstanceId);
		this.businessType = businessType;
		this.stepId = stepId;
		this.stepName = stepName;
	}

}
