package com.taipei.iot.notification.controller;

import com.taipei.iot.audit.annotation.AuditEvent;
import com.taipei.iot.audit.enums.AuditEventType;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 驗證 NotificationController 的 markRead / markAllRead 端點 正確掛上 @AuditEvent 註解（N-4 修復驗證）。
 */
class NotificationControllerAuditEventTest {

	@Test
	void markRead_shouldHaveAuditEventAnnotation() throws NoSuchMethodException {
		Method method = NotificationController.class.getDeclaredMethod("markRead", Long.class);
		AuditEvent annotation = method.getAnnotation(AuditEvent.class);

		assertThat(annotation).isNotNull();
		assertThat(annotation.value()).isEqualTo(AuditEventType.MARK_NOTIFICATION_READ);
	}

	@Test
	void markAllRead_shouldHaveAuditEventAnnotation() throws NoSuchMethodException {
		Method method = NotificationController.class.getDeclaredMethod("markAllRead");
		AuditEvent annotation = method.getAnnotation(AuditEvent.class);

		assertThat(annotation).isNotNull();
		assertThat(annotation.value()).isEqualTo(AuditEventType.MARK_ALL_NOTIFICATIONS_READ);
	}

	@Test
	void markNotificationRead_eventType_shouldHaveCorrectCategory() {
		assertThat(AuditEventType.MARK_NOTIFICATION_READ.getCategory().getValue()).isEqualTo("NOTIFICATION");
		assertThat(AuditEventType.MARK_NOTIFICATION_READ.isSuccess()).isTrue();
	}

	@Test
	void markAllNotificationsRead_eventType_shouldHaveCorrectCategory() {
		assertThat(AuditEventType.MARK_ALL_NOTIFICATIONS_READ.getCategory().getValue()).isEqualTo("NOTIFICATION");
		assertThat(AuditEventType.MARK_ALL_NOTIFICATIONS_READ.isSuccess()).isTrue();
	}

}
