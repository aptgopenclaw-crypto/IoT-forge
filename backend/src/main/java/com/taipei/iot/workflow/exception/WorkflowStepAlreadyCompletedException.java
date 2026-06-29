package com.taipei.iot.workflow.exception;

public class WorkflowStepAlreadyCompletedException extends WorkflowException {

	public WorkflowStepAlreadyCompletedException(String stepName) {
		super("步驟已完成，無法重複審核：" + stepName);
	}

}
