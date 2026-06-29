package com.taipei.iot.workflow.event;

import com.taipei.iot.workflow.model.WorkflowAction;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 流程步驟完成事件 — 當流程引擎完成某步驟待辦時發布。
 * <p>
 * 訂閱者可據此發送通知（In-App / Email / SMS）給申請人或相關人員。
 */
@Getter
public class WorkflowStepCompletedEvent extends ApplicationEvent {

	private final String tenantId;

	private final Long workflowInstanceId;

	private final String businessType;

	private final String stepId;

	private final String stepName;

	private final WorkflowAction action;

	private final String actorUserId;

	public WorkflowStepCompletedEvent(Object source, String tenantId, Long workflowInstanceId, String businessType,
			String stepId, String stepName, WorkflowAction action, String actorUserId) {
		super(source);
		this.tenantId = tenantId;
		this.workflowInstanceId = workflowInstanceId;
		this.businessType = businessType;
		this.stepId = stepId;
		this.stepName = stepName;
		this.action = action;
		this.actorUserId = actorUserId;
	}

}
