package com.taipei.iot.notification.channel;

import com.taipei.iot.auth.entity.UserEntity;
import com.taipei.iot.auth.repository.UserRepository;
import com.taipei.iot.notification.dto.NotificationPayload;
import com.taipei.iot.notification.enums.NotificationType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailChannelTest {

	@Mock
	private JavaMailSender mailSender;

	@Mock
	private UserRepository userRepository;

	@InjectMocks
	private EmailChannel emailChannel;

	@Test
	void channelType_shouldReturnEmail() {
		assert "EMAIL".equals(emailChannel.channelType());
	}

	@Test
	void send_shouldSendEmailWhenUserHasEmailFlagTrue() {
		UserEntity user = UserEntity.builder().userId("u1").email("u1@test.com").notifyEmailFlag(true).build();
		when(userRepository.findAllById(List.of("u1"))).thenReturn(List.of(user));

		NotificationPayload payload = NotificationPayload.builder()
			.tenantId("T1")
			.userIds(List.of("u1"))
			.type(NotificationType.ALERT)
			.title("Alert title")
			.content("Alert body")
			.build();

		emailChannel.send(payload);

		verify(mailSender).send(any(SimpleMailMessage.class));
	}

	@Test
	void send_shouldSkipWhenEmailFlagFalse() {
		UserEntity user = UserEntity.builder().userId("u1").email("u1@test.com").notifyEmailFlag(false).build();
		when(userRepository.findAllById(List.of("u1"))).thenReturn(List.of(user));

		NotificationPayload payload = NotificationPayload.builder()
			.tenantId("T1")
			.userIds(List.of("u1"))
			.type(NotificationType.INFO)
			.title("Info")
			.build();

		emailChannel.send(payload);

		verify(mailSender, never()).send(any(SimpleMailMessage.class));
	}

	@Test
	void send_shouldNotThrowWhenMailSenderFails() {
		UserEntity user = UserEntity.builder().userId("u1").email("u1@test.com").notifyEmailFlag(true).build();
		when(userRepository.findAllById(List.of("u1"))).thenReturn(List.of(user));
		doThrow(new RuntimeException("SMTP error")).when(mailSender).send(any(SimpleMailMessage.class));

		NotificationPayload payload = NotificationPayload.builder()
			.tenantId("T1")
			.userIds(List.of("u1"))
			.type(NotificationType.ALERT)
			.title("Alert")
			.content("Body")
			.build();

		// Should not throw — error is caught and logged
		emailChannel.send(payload);
	}

	@Test
	void send_shouldSkipWhenUserNotFound() {
		when(userRepository.findAllById(List.of("missing"))).thenReturn(Collections.emptyList());

		NotificationPayload payload = NotificationPayload.builder()
			.tenantId("T1")
			.userIds(List.of("missing"))
			.type(NotificationType.INFO)
			.title("Info")
			.build();

		emailChannel.send(payload);

		verify(mailSender, never()).send(any(SimpleMailMessage.class));
	}

	@Test
	void send_batchLoad_shouldQueryOnceForMultipleUsers() {
		UserEntity u1 = UserEntity.builder().userId("u1").email("u1@test.com").notifyEmailFlag(true).build();
		UserEntity u2 = UserEntity.builder().userId("u2").email("u2@test.com").notifyEmailFlag(true).build();
		UserEntity u3 = UserEntity.builder().userId("u3").email("u3@test.com").notifyEmailFlag(false).build();

		when(userRepository.findAllById(List.of("u1", "u2", "u3"))).thenReturn(List.of(u1, u2, u3));

		NotificationPayload payload = NotificationPayload.builder()
			.tenantId("T1")
			.userIds(List.of("u1", "u2", "u3"))
			.type(NotificationType.ALERT)
			.title("Broadcast")
			.content("Content")
			.build();

		emailChannel.send(payload);

		// Only one DB query (findAllById), not N individual findById calls
		verify(userRepository, times(1)).findAllById(any());
		verify(userRepository, never()).findById(any());
		// u1 and u2 get emails, u3 has flag=false
		verify(mailSender, times(2)).send(any(SimpleMailMessage.class));
	}

}
