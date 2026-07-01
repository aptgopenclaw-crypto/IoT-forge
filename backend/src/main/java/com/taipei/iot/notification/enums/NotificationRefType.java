package com.taipei.iot.notification.enums;

public enum NotificationRefType {

	FAULT, REPAIR, REPLACEMENT, WORKFLOW, ANNOUNCEMENT, MATERIAL, ALERT,
	/** [Platform/Tenant Separation ADR-002] Super-admin impersonation session. */
	IMPERSONATION,
	/** 事件規則觸發通知。 */
	EVENT_RULE

}
