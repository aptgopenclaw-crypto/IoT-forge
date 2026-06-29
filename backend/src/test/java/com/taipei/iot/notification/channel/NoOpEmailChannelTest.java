package com.taipei.iot.notification.channel;

import com.taipei.iot.notification.dto.NotificationPayload;
import com.taipei.iot.notification.enums.NotificationType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NoOpEmailChannelTest {

	private final NoOpEmailChannel channel = new NoOpEmailChannel();

	@Test
	void channelType_shouldReturnEmail() {
		assertEquals("EMAIL", channel.channelType());
	}

	@Test
	void send_shouldNotThrow() {
		NotificationPayload payload = NotificationPayload.builder()
			.tenantId("T1")
			.userIds(List.of("u1"))
			.type(NotificationType.INFO)
			.title("Test")
			.build();

		assertDoesNotThrow(() -> channel.send(payload));
	}

}
