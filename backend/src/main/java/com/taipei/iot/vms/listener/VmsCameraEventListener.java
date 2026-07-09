package com.taipei.iot.vms.listener;

import com.taipei.iot.common.context.TenantContext;
import com.taipei.iot.notification.dto.NotificationPayload;
import com.taipei.iot.notification.enums.NotificationRefType;
import com.taipei.iot.notification.enums.NotificationType;
import com.taipei.iot.notification.service.NotificationService;
import com.taipei.iot.vms.event.VmsCameraEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 訂閱 {@link VmsCameraEvent}，轉發通知至前端（STOMP / InApp）。
 *
 * <p>
 * 以 {@code @Async} 執行，需顯式還原 {@link TenantContext}。 與 {@code RuleTriggeredEventListener}
 * 相同模式。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VmsCameraEventListener {

	private final NotificationService notificationService;

	@Async
	@EventListener
	public void onVmsCameraEvent(VmsCameraEvent event) {
		TenantContext.setCurrentTenantId(event.tenantId());
		try {
			String severity = switch (event.eventType()) {
				case CAMERA_OFFLINE, VIDEO_LOST -> "WARNING";
				case CAMERA_ONLINE -> "INFO";
				case MOTION_DETECT -> "INFO";
				default -> "INFO";
			};

			String title = "[" + severity + "] " + translateEventType(event.eventType().name());
			String content = "攝影機「" + event.vmsCameraId() + "」" + translateEventType(event.eventType().name());

			NotificationPayload payload = NotificationPayload.builder()
				.tenantId(event.tenantId())
				.userIds(List.of()) // 透過 STOMP topic 廣播；特定使用者路由由前端訂閱處理
				.type(severityToType(severity))
				.title(title)
				.content(content)
				.refType(NotificationRefType.VMS_EVENT)
				.refId(String.valueOf(event.cameraId()))
				.build();
			notificationService.send(payload);

			log.debug("VMS 事件通知已發送: cameraId={}, eventType={}", event.cameraId(), event.eventType());
		}
		catch (Exception e) {
			log.warn("[VmsCameraEventListener] 處理失敗: cameraId={}, eventType={}: {}", event.cameraId(),
					event.eventType(), e.getMessage());
		}
		finally {
			TenantContext.clear();
		}
	}

	private String translateEventType(String type) {
		return switch (type) {
			case "MOTION_DETECT" -> "移動偵測";
			case "CAMERA_OFFLINE" -> "鏡頭離線";
			case "CAMERA_ONLINE" -> "鏡頭恢復";
			case "VIDEO_LOST" -> "影像遺失";
			case "RECORDING_STARTED" -> "錄影開始";
			case "RECORDING_STOPPED" -> "錄影結束";
			default -> "未知事件";
		};
	}

	private NotificationType severityToType(String severity) {
		return switch (severity) {
			case "WARNING", "CRITICAL" -> NotificationType.ALERT;
			default -> NotificationType.INFO;
		};
	}

}
