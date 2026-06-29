package com.taipei.iot.workflow.exception;

public class WorkflowInvalidTransitionException extends WorkflowException {

	public WorkflowInvalidTransitionException(String fromStep, String toStep) {
		super("不允許從步驟 [" + fromStep + "] 退回到 [" + toStep + "]");
	}

}
