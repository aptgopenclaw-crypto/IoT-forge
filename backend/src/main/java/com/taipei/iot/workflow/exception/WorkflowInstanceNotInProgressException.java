package com.taipei.iot.workflow.exception;

import com.taipei.iot.workflow.model.WorkflowStatus;

public class WorkflowInstanceNotInProgressException extends WorkflowException {

	public WorkflowInstanceNotInProgressException(Long instanceId, WorkflowStatus status) {
		super("流程實例 [" + instanceId + "] 已" + status.name() + "，無法繼續操作");
	}

}
