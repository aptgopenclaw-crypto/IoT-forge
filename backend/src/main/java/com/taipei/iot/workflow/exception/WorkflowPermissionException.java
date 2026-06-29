package com.taipei.iot.workflow.exception;

public class WorkflowPermissionException extends WorkflowException {

	public WorkflowPermissionException(String userId, String stepName) {
		super("使用者 [" + userId + "] 無權審核步驟：" + stepName);
	}

}
