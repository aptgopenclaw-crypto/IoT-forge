package com.taipei.iot.workflow.exception;

public class WorkflowNoRejectHistoryException extends WorkflowException {

	public WorkflowNoRejectHistoryException(Long instanceId) {
		super("流程實例 [" + instanceId + "] 無退回歷程，無法補件重送");
	}

}
