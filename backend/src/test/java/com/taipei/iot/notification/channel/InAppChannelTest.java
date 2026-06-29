package com.taipei.iot.notification.channel;

import com.taipei.iot.notification.dto.NotificationPayload;
import com.taipei.iot.notification.entity.NotificationEntity;
import com.taipei.iot.notification.enums.NotificationType;
import com.taipei.iot.notification.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InAppChannelTest {

	@Mock
	private NotificationRepository notificationRepository;

	@Mock
	private SimpMessagingTemplate messagingTemplate;

	@InjectMocks
	private InAppChannel inAppChannel;

	@Test
	void channelType_shouldReturnInApp() {
		assertEquals("IN_APP", inAppChannel.channelType());
	}

	@Test
	void send_shouldSaveOneNotificationPerUser() {
		NotificationPayload payload = NotificationPayload.builder()
			.tenantId("T1")
			.userIds(List.of("u1", "u2"))
			.type(NotificationType.TODO)
			.title("New fault")
			.content("Fault F-001 assigned")
			.build();

		when(notificationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

		inAppChannel.send(payload);

		ArgumentCaptor<NotificationEntity> captor = ArgumentCaptor.forClass(NotificationEntity.class);
		verify(notificationRepository, times(2)).save(captor.capture());

		List<NotificationEntity> saved = captor.getAllValues();
		assertEquals("u1", saved.get(0).getUserId());
		assertEquals("u2", saved.get(1).getUserId());
		assertEquals("New fault", saved.get(0).getTitle());
		assertEquals(NotificationType.TODO, saved.get(0).getType());

		// Verify WebSocket push
		verify(messagingTemplate, times(2)).convertAndSendToUser(anyString(), eq("/queue/notifications"), any());
	}

	@Test
	void send_shouldSetTenantIdOnEntity() {
		NotificationPayload payload = NotificationPayload.builder()
			.tenantId("TENANT_A")
			.userIds(List.of("u1"))
			.type(NotificationType.INFO)
			.title("Info")
			.build();

		when(notificationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

		inAppChannel.send(payload);

		ArgumentCaptor<NotificationEntity> captor = ArgumentCaptor.forClass(NotificationEntity.class);
		verify(notificationRepository).save(captor.capture());
		assertEquals("TENANT_A", captor.getValue().getTenantId());
	}

}
