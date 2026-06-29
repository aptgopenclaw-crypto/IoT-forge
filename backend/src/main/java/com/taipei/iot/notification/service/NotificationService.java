package com.taipei.iot.notification.service;

import com.taipei.iot.common.enums.ErrorCode;
import com.taipei.iot.common.exception.BusinessException;
import com.taipei.iot.notification.channel.NotificationChannel;
import com.taipei.iot.notification.dto.NotificationPayload;
import com.taipei.iot.notification.dto.NotificationResponse;
import com.taipei.iot.notification.dto.UnreadCountResponse;
import com.taipei.iot.notification.entity.NotificationEntity;
import com.taipei.iot.notification.enums.NotificationType;
import com.taipei.iot.notification.repository.NotificationRepository;
import com.taipei.iot.common.dto.PageResponse;
import com.taipei.iot.common.util.PageConversionHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

	private final NotificationRepository notificationRepository;

	private final List<NotificationChannel> channels;

	/**
	 * 以 best-effort 方式透過所有已註冊的 channel 發送通知。
	 * <p>
	 * 設計意圖：本方法<strong>不使用 {@code @Transactional}</strong>，各 channel 獨立執行、獨立捕捉例外。
	 * <ul>
	 * <li>{@code InAppChannel} 負責持久化通知至 DB（其內部自行管理交易）。</li>
	 * <li>其他 channel（Email、SMS 等）為外部 I/O，失敗不影響其他 channel，也不回滾 InApp 持久化。</li>
	 * </ul>
	 * 若需 all-or-nothing 語意，請勿在此方法加上 {@code @Transactional}。
	 * @param payload 通知內容與收件人資訊
	 */
	public void send(NotificationPayload payload) {
		log.info("[NotificationService] send: {} channels, userIds={}, title={}", channels.size(), payload.getUserIds(),
				payload.getTitle());
		for (NotificationChannel channel : channels) {
			try {
				channel.send(payload);
			}
			catch (Exception e) {
				log.warn("Channel {} failed: {}", channel.channelType(), e.getMessage());
			}
		}
	}

	@Transactional(readOnly = true)
	public PageResponse<NotificationResponse> list(String userId, int page, int size) {
		Page<NotificationEntity> result = notificationRepository
			.findByUserIdAndArchivedAtIsNullOrderByCreatedAtDesc(userId, PageRequest.of(page, size));
		return PageConversionHelper.from(result, this::toResponse);
	}

	@Transactional(readOnly = true)
	public PageResponse<NotificationResponse> listTodos(String userId, int page, int size) {
		Page<NotificationEntity> result = notificationRepository
			.findByUserIdAndTypeAndArchivedAtIsNullOrderByCreatedAtDesc(userId, NotificationType.TODO,
					PageRequest.of(page, size));
		return PageConversionHelper.from(result, this::toResponse);
	}

	@Transactional(readOnly = true)
	public UnreadCountResponse unreadCount(String userId) {
		long count = notificationRepository.countUnreadByUserId(userId);
		return UnreadCountResponse.builder().count(count).build();
	}

	@Transactional
	public void markRead(String userId, Long notificationId) {
		NotificationEntity entity = notificationRepository.findById(notificationId)
			.orElseThrow(() -> new BusinessException(ErrorCode.NOTIFICATION_NOT_FOUND));
		if (!entity.getUserId().equals(userId)) {
			throw new BusinessException(ErrorCode.NOTIFICATION_NOT_FOUND);
		}
		// Atomic update: only the first concurrent request will set readAt
		notificationRepository.markReadAtomic(notificationId, userId);
	}

	@Transactional
	public void markAllRead(String userId) {
		int updated = notificationRepository.markAllReadByUserId(userId);
		log.debug("Marked {} notifications as read for userId={}", updated, userId);
	}

	private NotificationResponse toResponse(NotificationEntity entity) {
		return NotificationResponse.builder()
			.id(entity.getId())
			.type(entity.getType())
			.title(entity.getTitle())
			.content(entity.getContent())
			.refType(entity.getRefType())
			.refId(entity.getRefId())
			.read(entity.getRead())
			.readAt(entity.getReadAt())
			.createdAt(entity.getCreatedAt())
			.build();
	}

}
