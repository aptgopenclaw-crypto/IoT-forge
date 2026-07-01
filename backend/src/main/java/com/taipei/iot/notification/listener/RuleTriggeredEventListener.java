package com.taipei.iot.notification.listener;

import java.util.ArrayList;
import java.util.List;

import com.taipei.iot.common.context.TenantContext;
import com.taipei.iot.common.event.RuleTriggeredEvent;
import com.taipei.iot.notification.dto.NotificationPayload;
import com.taipei.iot.notification.enums.NotificationRefType;
import com.taipei.iot.notification.enums.NotificationType;
import com.taipei.iot.notification.service.NotificationService;
import com.taipei.iot.user.entity.UserTenantMappingEntity;
import com.taipei.iot.user.repository.UserTenantMappingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 訂閱 {@link RuleTriggeredEvent}，解析收件人後發送通知。
 *
 * <p>
 * 以 {@code @Async} 在 event-rule executor 之外的線程執行（避免堵塞規則評估鏈），需顯式還原
 * {@link TenantContext}。{@code recipients.roleCodes} 以 {@code "ROLE_" + roleCode} 查
 * {@link UserTenantMappingRepository}。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RuleTriggeredEventListener {

	private final NotificationService notificationService;

	private final UserTenantMappingRepository userTenantMappingRepository;

	@Async
	@EventListener
	public void onRuleTriggered(RuleTriggeredEvent event) {
		TenantContext.setCurrentTenantId(event.tenantId());
		try {
			List<String> userIds = resolveRecipients(event.tenantId(), event.recipients());
			if (userIds.isEmpty()) {
				log.debug("[RuleTriggeredEventListener] no recipients for rule={} tenant={}", event.ruleId(),
						event.tenantId());
				return;
			}
			String severity = event.severity() != null ? event.severity().toUpperCase() : "INFO";
			String title = "[" + severity + "] " + event.ruleName() + " 已觸發";
			String content = "設備 " + event.deviceId() + " 於 " + event.triggeredAt() + " 觸發規則「" + event.ruleName() + "」";
			NotificationPayload payload = NotificationPayload.builder()
				.tenantId(event.tenantId())
				.userIds(userIds)
				.type(severityToType(severity))
				.title(title)
				.content(content)
				.refType(NotificationRefType.EVENT_RULE)
				.refId(event.ruleId())
				.build();
			notificationService.send(payload);
		}
		catch (Exception e) {
			log.warn("[RuleTriggeredEventListener] failed for rule={} tenant={}: {}", event.ruleId(), event.tenantId(),
					e.getMessage());
		}
		finally {
			TenantContext.clear();
		}
	}

	private List<String> resolveRecipients(String tenantId, RuleTriggeredEvent.RecipientSpec recipients) {
		List<String> result = new ArrayList<>();
		if (recipients == null) {
			return result;
		}
		// 直接指定的 userId
		if (recipients.userIds() != null) {
			result.addAll(recipients.userIds());
		}
		// 角色代碼 → 查詢該租戶下擁有該角色的所有使用者
		if (recipients.roleCodes() != null) {
			for (String roleCode : recipients.roleCodes()) {
				String roleId = "ROLE_" + roleCode.toUpperCase();
				List<UserTenantMappingEntity> mappings = userTenantMappingRepository
					.findByTenantIdAndRoleIdAndEnabledTrue(tenantId, roleId);
				mappings.stream().map(UserTenantMappingEntity::getUserId).forEach(result::add);
			}
		}
		// 去重
		return result.stream().distinct().toList();
	}

	private NotificationType severityToType(String severity) {
		return switch (severity) {
			case "CRITICAL" -> NotificationType.ALERT;
			case "WARNING" -> NotificationType.INFO;
			default -> NotificationType.INFO;
		};
	}

}
