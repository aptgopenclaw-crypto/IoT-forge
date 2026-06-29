package com.taipei.iot.audit.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 審計事件分類（對應 user_event_log.event_desc）。
 */
@Getter
@RequiredArgsConstructor
public enum AuditCategory {

	USER_AUTH("USER_AUTH"), ACCOUNT("ACCOUNT"), SYSTEM("SYSTEM"), ASSET("ASSET"), WORKFLOW("WORKFLOW"),
	MAINTENANCE("MAINTENANCE"), MATERIAL("MATERIAL"), REPLACEMENT("REPLACEMENT"), KPI("KPI"), DASHBOARD("DASHBOARD"),
	IOT("IOT"), TENANT("TENANT"), NOTIFICATION("NOTIFICATION"),;

	private final String value;

}
