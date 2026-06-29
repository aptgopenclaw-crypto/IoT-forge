package com.taipei.iot.notification.channel;

import com.taipei.iot.notification.dto.NotificationPayload;
import com.taipei.iot.notification.enums.NotificationType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NoOpSmsChannelTest {

	private final NoOpSmsChannel channel = new NoOpSmsChannel();

	@Test
	void channelType_shouldReturnSms() {
		assertEquals("SMS", channel.channelType());
	}

	@Test
	void send_shouldNotThrow() {
		NotificationPayload payload = NotificationPayload.builder()
			.tenantId("T1")
			.userIds(List.of("u1", "u2"))
			.type(NotificationType.ALERT)
			.title("Test")
			.build();

		assertDoesNotThrow(() -> channel.send(payload));
	}

}
