package com.taipei.iot.common.enums;

/**
 * 安全事件類型枚舉 — 搭配 {@link com.taipei.iot.common.util.SecurityLogger} 使用。
 * <p>
 * 所有安全相關事件統一定義在此，方便 grep、告警規則、ELK 索引等統一過濾。
 */
public enum SecurityEvent {

	LOGIN_FAILED, CAPTCHA_FAILED, RATE_LIMITED, JWT_INVALID, ACCESS_DENIED, PASSWORD_RESET_REQUEST, SUSPICIOUS_INPUT,
	/** [Tenant v2 T-5] single 模式下 JWT tenantId 與部署預設不一致 */
	TENANT_MODE_MISMATCH,
	/**
	 * [Platform/Tenant Separation ADR-007] JWT scope claim 與 request path prefix 不符。
	 * Phase 1.1.2 為 warning-only（log 但不擋）；Phase 3 切換為 enforce mode。
	 */
	SCOPE_MISMATCH;

}
