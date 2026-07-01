package com.taipei.iot.eventrule.model;

/**
 * 規則動作類型（可插拔）。
 */
public enum ActionType {

	/** 發送通知（In-App / Email / STOMP），第一版實作。 */
	NOTIFY,
	/** 呼叫外部 HTTP（後續）。 */
	WEBHOOK,
	/** 反向下令設備（後續）。 */
	DEVICE_CMD,
	/** 觸發流程引擎（後續）。 */
	WORKFLOW

}
