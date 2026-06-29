package com.taipei.iot.notification.service;

import com.taipei.iot.common.enums.ErrorCode;
import com.taipei.iot.common.exception.BusinessException;
import com.taipei.iot.notification.channel.NotificationChannel;
import com.taipei.iot.notification.dto.NotificationPayload;
import com.taipei.iot.notification.dto.NotificationResponse;
import com.taipei.iot.notification.dto.UnreadCountResponse;
import com.taipei.iot.notification.entity.NotificationEntity;
import com.taipei.iot.notification.enums.NotificationRefType;
import com.taipei.iot.notification.enums.NotificationType;
import com.taipei.iot.notification.repository.NotificationRepository;
import com.taipei.iot.common.dto.PageResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

	@Mock
	private NotificationRepository notificationRepository;

	@Mock
	private List<NotificationChannel> channels;

	@InjectMocks
	private NotificationService notificationService;

	// --- TC-00-014: 通知建立與發送 ---

	@Test
	void send_shouldDelegateToAllChannels() {
		NotificationChannel ch1 = mock(NotificationChannel.class);
		NotificationChannel ch2 = mock(NotificationChannel.class);
		List<NotificationChannel> channelList = List.of(ch1, ch2);
		NotificationService service = new NotificationService(notificationRepository, channelList);

		NotificationPayload payload = NotificationPayload.builder()
			.tenantId("T1")
			.userIds(List.of("u1"))
			.type(NotificationType.TODO)
			.title("Test")
			.build();

		service.send(payload);

		verify(ch1).send(payload);
		verify(ch2).send(payload);
	}

	@Test
	void send_shouldContinueWhenOneChannelFails() {
		NotificationChannel ch1 = mock(NotificationChannel.class);
		NotificationChannel ch2 = mock(NotificationChannel.class);
		doThrow(new RuntimeException("fail")).when(ch1).send(any());
		List<NotificationChannel> channelList = List.of(ch1, ch2);
		NotificationService service = new NotificationService(notificationRepository, channelList);

		NotificationPayload payload = NotificationPayload.builder()
			.tenantId("T1")
			.userIds(List.of("u1"))
			.type(NotificationType.ALERT)
			.title("Test")
			.build();

		assertDoesNotThrow(() -> service.send(payload));
		verify(ch2).send(payload);
	}

	@Test
	void send_shouldHandleMultipleRecipients() {
		NotificationChannel ch = mock(NotificationChannel.class);
		List<NotificationChannel> channelList = List.of(ch);
		NotificationService service = new NotificationService(notificationRepository, channelList);

		NotificationPayload payload = NotificationPayload.builder()
			.tenantId("T1")
			.userIds(List.of("u1", "u2", "u3"))
			.type(NotificationType.INFO)
			.title("Broadcast")
			.build();

		service.send(payload);
		verify(ch).send(payload);
	}

	// --- TC-00-015: 通知清單查詢 ---

	@Test
	void list_shouldReturnPagedNotifications() {
		NotificationEntity entity = buildEntity(1L, "u1", NotificationType.INFO, false);
		Page<NotificationEntity> page = new PageImpl<>(List.of(entity), PageRequest.of(0, 20), 1);
		when(notificationRepository.findByUserIdAndArchivedAtIsNullOrderByCreatedAtDesc("u1", PageRequest.of(0, 20)))
			.thenReturn(page);

		PageResponse<NotificationResponse> result = notificationService.list("u1", 0, 20);

		assertEquals(1, result.getContent().size());
		assertEquals(1L, result.getTotalElements());
		assertEquals("Test title", result.getContent().get(0).getTitle());
	}

	@Test
	void list_shouldReturnEmptyWhenNoNotifications() {
		Page<NotificationEntity> page = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);
		when(notificationRepository.findByUserIdAndArchivedAtIsNullOrderByCreatedAtDesc("u1", PageRequest.of(0, 20)))
			.thenReturn(page);

		PageResponse<NotificationResponse> result = notificationService.list("u1", 0, 20);

		assertTrue(result.getContent().isEmpty());
		assertEquals(0, result.getTotalElements());
	}

	// --- TC-00-016: 待辦清單查詢 ---

	@Test
	void listTodos_shouldReturnOnlyTodoType() {
		NotificationEntity entity = buildEntity(1L, "u1", NotificationType.TODO, false);
		Page<NotificationEntity> page = new PageImpl<>(List.of(entity), PageRequest.of(0, 20), 1);
		when(notificationRepository.findByUserIdAndTypeAndArchivedAtIsNullOrderByCreatedAtDesc("u1",
				NotificationType.TODO, PageRequest.of(0, 20)))
			.thenReturn(page);

		PageResponse<NotificationResponse> result = notificationService.listTodos("u1", 0, 20);

		assertEquals(1, result.getContent().size());
		assertEquals(NotificationType.TODO, result.getContent().get(0).getType());
	}

	// --- TC-00-017: 未讀計數 ---

	@Test
	void unreadCount_shouldReturnCorrectCount() {
		when(notificationRepository.countUnreadByUserId("u1")).thenReturn(5L);

		UnreadCountResponse result = notificationService.unreadCount("u1");

		assertEquals(5, result.getCount());
	}

	@Test
	void unreadCount_shouldReturnZeroWhenAllRead() {
		when(notificationRepository.countUnreadByUserId("u1")).thenReturn(0L);

		UnreadCountResponse result = notificationService.unreadCount("u1");

		assertEquals(0, result.getCount());
	}

	// --- TC-00-018: 標記已讀（原子更新）---

	@Test
	void markRead_shouldCallAtomicUpdate() {
		NotificationEntity entity = buildEntity(1L, "u1", NotificationType.INFO, false);
		when(notificationRepository.findById(1L)).thenReturn(Optional.of(entity));
		when(notificationRepository.markReadAtomic(1L, "u1")).thenReturn(1);

		notificationService.markRead("u1", 1L);

		verify(notificationRepository).markReadAtomic(1L, "u1");
		verify(notificationRepository, never()).save(any());
	}

	@Test
	void markRead_shouldThrowWhenNotFound() {
		when(notificationRepository.findById(999L)).thenReturn(Optional.empty());

		BusinessException ex = assertThrows(BusinessException.class, () -> notificationService.markRead("u1", 999L));
		assertEquals(ErrorCode.NOTIFICATION_NOT_FOUND, ex.getErrorCode());
	}

	@Test
	void markRead_shouldThrowWhenUserMismatch() {
		NotificationEntity entity = buildEntity(1L, "other-user", NotificationType.INFO, false);
		when(notificationRepository.findById(1L)).thenReturn(Optional.of(entity));

		BusinessException ex = assertThrows(BusinessException.class, () -> notificationService.markRead("u1", 1L));
		assertEquals(ErrorCode.NOTIFICATION_NOT_FOUND, ex.getErrorCode());
	}

	@Test
	void markRead_shouldBeIdempotentWhenAlreadyRead() {
		NotificationEntity entity = buildEntity(1L, "u1", NotificationType.INFO, true);
		when(notificationRepository.findById(1L)).thenReturn(Optional.of(entity));
		// Atomic update returns 0 when read=false condition not met (already read)
		when(notificationRepository.markReadAtomic(1L, "u1")).thenReturn(0);

		assertDoesNotThrow(() -> notificationService.markRead("u1", 1L));
		verify(notificationRepository).markReadAtomic(1L, "u1");
	}

	// --- TC-00-019: 全部標記已讀 ---

	@Test
	void markAllRead_shouldCallRepositoryBulkUpdate() {
		when(notificationRepository.markAllReadByUserId("u1")).thenReturn(3);

		notificationService.markAllRead("u1");

		verify(notificationRepository).markAllReadByUserId("u1");
	}

	@Test
	void markAllRead_shouldHandleZeroUnread() {
		when(notificationRepository.markAllReadByUserId("u1")).thenReturn(0);

		assertDoesNotThrow(() -> notificationService.markAllRead("u1"));
	}

	// --- N-5: send() 設計意圖驗證 — best-effort、無 @Transactional ---

	@Test
	void send_shouldNotHaveTransactionalAnnotation() throws NoSuchMethodException {
		Method method = NotificationService.class.getDeclaredMethod("send", NotificationPayload.class);
		Transactional annotation = method.getAnnotation(Transactional.class);

		assertNull(annotation, "send() 不應標註 @Transactional — best-effort 設計，各 channel 獨立執行");
	}

	@Test
	void send_shouldNotRollbackOtherChannelsOnFailure() {
		NotificationChannel ch1 = mock(NotificationChannel.class);
		NotificationChannel ch2 = mock(NotificationChannel.class);
		NotificationChannel ch3 = mock(NotificationChannel.class);
		doThrow(new RuntimeException("email down")).when(ch2).send(any());
		List<NotificationChannel> channelList = List.of(ch1, ch2, ch3);
		NotificationService service = new NotificationService(notificationRepository, channelList);

		NotificationPayload payload = NotificationPayload.builder()
			.tenantId("T1")
			.userIds(List.of("u1"))
			.type(NotificationType.ALERT)
			.title("Alert")
			.build();

		assertDoesNotThrow(() -> service.send(payload));

		// ch1 和 ch3 都必須被呼叫，ch2 失敗不影響其他
		verify(ch1).send(payload);
		verify(ch2).send(payload);
		verify(ch3).send(payload);
	}

	@Test
	void send_shouldContinueEvenIfAllChannelsFail() {
		NotificationChannel ch1 = mock(NotificationChannel.class);
		NotificationChannel ch2 = mock(NotificationChannel.class);
		doThrow(new RuntimeException("fail1")).when(ch1).send(any());
		doThrow(new RuntimeException("fail2")).when(ch2).send(any());
		List<NotificationChannel> channelList = List.of(ch1, ch2);
		NotificationService service = new NotificationService(notificationRepository, channelList);

		NotificationPayload payload = NotificationPayload.builder()
			.tenantId("T1")
			.userIds(List.of("u1"))
			.type(NotificationType.INFO)
			.title("Test")
			.build();

		// 即使所有 channel 都失敗，方法也不拋出例外
		assertDoesNotThrow(() -> service.send(payload));
		verify(ch1).send(payload);
		verify(ch2).send(payload);
	}

	private NotificationEntity buildEntity(Long id, String userId, NotificationType type, boolean read) {
		return NotificationEntity.builder()
			.id(id)
			.tenantId("T1")
			.userId(userId)
			.type(type)
			.title("Test title")
			.content("Test content")
			.refType(NotificationRefType.FAULT)
			.refId("F-001")
			.read(read)
			.readAt(read ? LocalDateTime.now() : null)
			.createdAt(LocalDateTime.now())
			.build();
	}

}
