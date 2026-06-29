package com.taipei.iot.notification.channel;

import com.taipei.iot.notification.dto.NotificationPayload;
import com.taipei.iot.notification.dto.NotificationResponse;
import com.taipei.iot.notification.entity.NotificationEntity;
import com.taipei.iot.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class InAppChannel implements NotificationChannel {

	private final NotificationRepository notificationRepository;

	private final SimpMessagingTemplate messagingTemplate;

	@Override
	public String channelType() {
		return "IN_APP";
	}

	@Override
	public void send(NotificationPayload payload) {
		log.info("[InAppChannel] sending notification to users={}, title={}", payload.getUserIds(), payload.getTitle());
		for (String userId : payload.getUserIds()) {
			try {
				NotificationEntity entity = NotificationEntity.builder()
					.tenantId(payload.getTenantId())
					.userId(userId)
					.type(payload.getType())
					.title(payload.getTitle())
					.content(payload.getContent())
					.refType(payload.getRefType())
					.refId(payload.getRefId())
					.build();
				NotificationEntity saved = notificationRepository.save(entity);
				log.info("[InAppChannel] notification saved: id={}, userId={}", saved.getId(), userId);

				// Push via WebSocket (STOMP)
				try {
					NotificationResponse response = NotificationResponse.builder()
						.id(saved.getId())
						.type(saved.getType())
						.title(saved.getTitle())
						.content(saved.getContent())
						.refType(saved.getRefType())
						.refId(saved.getRefId())
						.read(false)
						.createdAt(saved.getCreatedAt())
						.build();
					messagingTemplate.convertAndSendToUser(userId, "/queue/notifications", response);
					log.debug("WebSocket push sent to user={}", userId);
				}
				catch (Exception e) {
					log.warn("WebSocket push failed for user={}: {}", userId, e.getMessage());
				}
			}
			catch (Exception e) {
				log.error("Failed to send in-app notification to user={}: {}", userId, e.getMessage());
			}
		}
	}

}
