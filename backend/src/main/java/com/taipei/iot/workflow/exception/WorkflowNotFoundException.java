package com.taipei.iot.workflow.exception;

public class WorkflowNotFoundException extends WorkflowException {

	public WorkflowNotFoundException(String code) {
		super("找不到流程定義：" + code);
	}

}
