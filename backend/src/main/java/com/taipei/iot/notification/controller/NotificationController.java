package com.taipei.iot.notification.controller;

import com.taipei.iot.audit.annotation.AuditEvent;
import com.taipei.iot.audit.enums.AuditEventType;
import com.taipei.iot.common.annotation.PaginationParams;
import com.taipei.iot.common.dto.PageQuery;
import com.taipei.iot.common.response.BaseResponse;
import com.taipei.iot.common.util.SecurityContextUtils;
import com.taipei.iot.notification.dto.NotificationResponse;
import com.taipei.iot.notification.dto.UnreadCountResponse;
import com.taipei.iot.notification.service.NotificationService;
import com.taipei.iot.common.dto.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/auth/notifications")
@RequiredArgsConstructor
@Validated
public class NotificationController {

	private final NotificationService notificationService;

	@GetMapping
	public BaseResponse<PageResponse<NotificationResponse>> list(@PaginationParams PageQuery pageQuery) {
		String userId = SecurityContextUtils.getCurrentUserId();
		return BaseResponse.success(notificationService.list(userId, pageQuery.getPage(), pageQuery.getSize()));
	}

	@GetMapping("/todos")
	public BaseResponse<PageResponse<NotificationResponse>> listTodos(@PaginationParams PageQuery pageQuery) {
		String userId = SecurityContextUtils.getCurrentUserId();
		return BaseResponse.success(notificationService.listTodos(userId, pageQuery.getPage(), pageQuery.getSize()));
	}

	@GetMapping("/unread-count")
	public BaseResponse<UnreadCountResponse> unreadCount() {
		String userId = SecurityContextUtils.getCurrentUserId();
		return BaseResponse.success(notificationService.unreadCount(userId));
	}

	@PatchMapping("/{id}/read")
	@AuditEvent(AuditEventType.MARK_NOTIFICATION_READ)
	public BaseResponse<Void> markRead(@PathVariable Long id) {
		String userId = SecurityContextUtils.getCurrentUserId();
		notificationService.markRead(userId, id);
		return BaseResponse.success(null);
	}

	@PatchMapping("/read-all")
	@AuditEvent(AuditEventType.MARK_ALL_NOTIFICATIONS_READ)
	public BaseResponse<Void> markAllRead() {
		String userId = SecurityContextUtils.getCurrentUserId();
		notificationService.markAllRead(userId);
		return BaseResponse.success(null);
	}

}
