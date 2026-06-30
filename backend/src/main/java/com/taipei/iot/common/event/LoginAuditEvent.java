package com.taipei.iot.common.event;

import com.taipei.iot.common.audit.enums.AuditEventType;

/**
 * 登入/登出等認證流程的審計事件。
 *
 * <p>
 * 由 {@code auth} 模組在認證流程中透過 {@link org.springframework.context.ApplicationEventPublisher}
 * 發佈；{@code audit} 模組的 listener 訂閱並寫入 {@code user_event_log}。
 *
 * <p>
 * 設計目的：解除 {@code auth → audit} 的直接依賴，遵循 Dependency Inversion Principle。
 */
public record LoginAuditEvent(

		String userId,

		String tenantId,

		String email,

		String displayName,

		Long deptId,

		AuditEventType eventType,

		/** 補充說明（登入失敗原因、force-change 原因等） */
		String detail,

		String apiEndpoint,

		String ipAddress,

		String userAgent

) {
}
