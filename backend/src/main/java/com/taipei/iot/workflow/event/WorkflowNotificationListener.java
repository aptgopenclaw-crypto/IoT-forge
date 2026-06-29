package com.taipei.iot.workflow.event;

import com.taipei.iot.notification.dto.NotificationPayload;
import com.taipei.iot.notification.enums.NotificationRefType;
import com.taipei.iot.notification.enums.NotificationType;
import com.taipei.iot.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

/**
 * 流程引擎事件的監聽器 — 將步驟指派／完成事件轉發至 NotificationService。
 * <p>
 * 使用 {@link TransactionalEventListener#phase() AFTER_COMMIT}，確保通知在
 * 流程引擎交易成功提交後才發送，避免交易回滾時發送孤兒通知。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkflowNotificationListener {

	private final NotificationService notificationService;

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onStepAssigned(WorkflowStepAssignedEvent event) {
		log.info("[WorkflowNotification] received WorkflowStepAssignedEvent: assignee={}, step={}, instance={}",
				event.getAssigneeUserId(), event.getStepId(), event.getWorkflowInstanceId());
		if (event.getAssigneeUserId() == null) {
			return; // end step 無審核人，不發通知
		}
		try {
			String title = "你有新的審核待辦";
			String content = String.format("「%s」步驟已指派給你，請前往處理。", event.getStepName());
			NotificationPayload payload = NotificationPayload.builder()
				.tenantId(event.getTenantId())
				.userIds(List.of(event.getAssigneeUserId()))
				.type(NotificationType.INFO)
				.title(title)
				.content(content)
				.refType(NotificationRefType.WORKFLOW)
				.refId(event.getWorkflowInstanceId())
				.build();
			notificationService.send(payload);
			log.debug("[Workflow] notified assignee={} for step={} (instance={})", event.getAssigneeUserId(),
					event.getStepId(), event.getWorkflowInstanceId());
		}
		catch (Exception e) {
			log.warn("[Workflow] failed to notify assignee={} for step={}: {}", event.getAssigneeUserId(),
					event.getStepId(), e.getMessage());
		}
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onStepCompleted(WorkflowStepCompletedEvent event) {
		try {
			String actionLabel = switch (event.getAction()) {
				case APPROVE -> "審核通過";
				case REJECT -> "審核退回";
				case RESUBMIT -> "補件重送";
				case CANCEL -> "已取消";
			};
			log.debug("[Workflow] step completed: instance={}, step={}, action={} by {}", event.getWorkflowInstanceId(),
					event.getStepId(), actionLabel, event.getActorUserId());
		}
		catch (Exception e) {
			log.warn("[Workflow] failed to handle step completion event: {}", e.getMessage());
		}
	}

}
