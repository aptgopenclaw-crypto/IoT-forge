package com.taipei.iot.workflow.exception;

public class WorkflowInstanceNotFoundException extends WorkflowException {

	public WorkflowInstanceNotFoundException(Long instanceId) {
		super("找不到流程實例：" + instanceId);
	}

}
