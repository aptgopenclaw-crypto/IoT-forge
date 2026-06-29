package com.taipei.iot.workflow.model;

/**
 * 步驟待辦的審核動作。
 * <p>
 * 以 {@link jakarta.persistence.EnumType#STRING} 儲存於 DB，值為大寫 （{@code APPROVE} /
 * {@code REJECT} / {@code RESUBMIT} / {@code CANCEL}）。
 */
public enum WorkflowAction {

	/** 審核通過 */
	APPROVE,

	/** 審核退回 */
	REJECT,

	/** 申請人補件重送 */
	RESUBMIT,

	/** 申請人取消流程 */
	CANCEL

}
