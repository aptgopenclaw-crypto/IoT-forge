package com.taipei.iot.workflow.model;

/**
 * 流程實例狀態。
 */
public enum WorkflowStatus {

	/** 審核進行中 */
	IN_PROGRESS,

	/** 審核全數通過，流程結案 */
	COMPLETED,

	/** 流程已退回至申請人且申請人未重送（最終拒絕） */
	REJECTED,

	/** 申請人主動取消流程 */
	CANCELLED

}
