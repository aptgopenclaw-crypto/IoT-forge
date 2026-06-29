package com.taipei.iot.setting.enums;

import lombok.Getter;

@Getter
public enum SettingKey {

	IDLE_TIMEOUT_MINUTES("idle_timeout_minutes", "15", "使用者閒置自動登出時間（分鐘）", SettingValidator.intRange(1, 480)),
	AUDIT_RETENTION_DAYS("audit_retention_days", "180", "稽核日誌保留天數", SettingValidator.intRange(1, 3650)),
	NOTIFICATION_RETENTION_DAYS("notification_retention_days", "90", "通知保留天數（已讀通知超過此天數將自動歸檔）",
			SettingValidator.intRange(1, 3650)),
	FRONTEND_BASE_URL("frontend_base_url", "http://localhost:5173", "前端應用程式 Base URL", SettingValidator.url());

	private final String key;

	private final String defaultValue;

	private final String description;

	private final SettingValidator validator;

	SettingKey(String key, String defaultValue, String description, SettingValidator validator) {
		this.key = key;
		this.defaultValue = defaultValue;
		this.description = description;
		this.validator = validator;
	}

}
