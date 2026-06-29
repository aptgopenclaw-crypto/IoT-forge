package com.taipei.iot.auth.service.impl;

import com.taipei.iot.auth.dto.response.SessionDto;
import com.taipei.iot.auth.entity.UserSessionEntity;
import com.taipei.iot.auth.repository.UserSessionRepository;
import com.taipei.iot.auth.service.UserSessionService;
import com.taipei.iot.common.exception.BusinessException;
import com.taipei.iot.common.enums.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * [v2 N-7] 使用者「登入裝置／Session」管理服務實作。
 *
 * 撤銷流程：DB 標記 revoked + Redis 黑名單（TTL = expires_at - now）。兩者皆失敗 才能造成攻擊面擴大，因此 Redis 失敗仍允許
 * DB 撤銷成功並回應 200，但記錄 warn log。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserSessionServiceImpl implements UserSessionService {

	private static final String REFRESH_REVOCATION_KEY_PREFIX = "auth:revoked_refresh:";

	private final UserSessionRepository userSessionRepository;

	private final StringRedisTemplate stringRedisTemplate;

	@Override
	@Transactional(readOnly = true)
	public List<SessionDto> listMine(String userId, String currentJti) {
		List<UserSessionEntity> rows = userSessionRepository.findActiveByUserId(userId, LocalDateTime.now());
		return rows.stream()
			.map(s -> SessionDto.builder()
				.sessionId(s.getSessionId())
				.tenantId(s.getTenantId())
				.ipAddress(s.getIpAddress())
				.userAgent(s.getUserAgent())
				.issuedAt(s.getIssuedAt())
				.lastSeenAt(s.getLastSeenAt())
				.expiresAt(s.getExpiresAt())
				.current(currentJti != null && currentJti.equals(s.getSessionId()))
				.build())
			.collect(Collectors.toList());
	}

	@Override
	@Transactional
	public void revoke(String userId, String sessionId) {
		UserSessionEntity session = userSessionRepository.findBySessionIdAndUserId(sessionId, userId)
			.orElseThrow(() -> new BusinessException(ErrorCode.SESSION_NOT_FOUND));
		// 已撤銷或已過期視為 idempotent：仍回 200，避免外洩「是否存在」資訊。
		LocalDateTime now = LocalDateTime.now();
		if (Boolean.TRUE.equals(session.getRevoked()) || session.getExpiresAt().isBefore(now)) {
			return;
		}
		userSessionRepository.revokeById(sessionId, now);
		// Redis blacklist：TTL = 剩餘有效時間。失敗不阻斷撤銷流程。
		try {
			long ttlMs = Duration.between(now, session.getExpiresAt()).toMillis();
			if (ttlMs > 0) {
				stringRedisTemplate.opsForValue()
					.set(REFRESH_REVOCATION_KEY_PREFIX + sessionId, "1", ttlMs, TimeUnit.MILLISECONDS);
			}
		}
		catch (Exception e) {
			log.warn("revoke session: Redis blacklist write failed for sessionId={}: {}", sessionId, e.getMessage());
		}
	}

	@Override
	@Transactional
	public void revokeAllExceptCurrent(String userId, String excludeJti) {
		LocalDateTime now = LocalDateTime.now();

		// 1. 查出要撤銷的 sessions（供 Redis blacklist 寫入）
		List<UserSessionEntity> activeSessions = userSessionRepository.findActiveByUserId(userId, now);
		List<UserSessionEntity> toRevoke = activeSessions.stream()
			.filter(s -> excludeJti == null || !excludeJti.equals(s.getSessionId()))
			.toList();

		if (toRevoke.isEmpty()) {
			return;
		}

		// 2. DB 批次撤銷
		if (excludeJti != null) {
			userSessionRepository.revokeAllByUserIdExcept(userId, excludeJti, now);
		}
		else {
			userSessionRepository.revokeAllByUserId(userId, now);
		}

		// 3. Redis blacklist（best-effort）
		for (UserSessionEntity session : toRevoke) {
			try {
				long ttlMs = Duration.between(now, session.getExpiresAt()).toMillis();
				if (ttlMs > 0) {
					stringRedisTemplate.opsForValue()
						.set(REFRESH_REVOCATION_KEY_PREFIX + session.getSessionId(), "1", ttlMs, TimeUnit.MILLISECONDS);
				}
			}
			catch (Exception e) {
				log.warn("revokeAllExceptCurrent: Redis blacklist write failed for sessionId={}: {}",
						session.getSessionId(), e.getMessage());
			}
		}

		log.info("revokeAllExceptCurrent: revoked {} sessions for userId={}", toRevoke.size(), userId);
	}

	// 保留 ZoneId 引用以方便未來時區處理擴充
	@SuppressWarnings("unused")
	private static final ZoneId DEFAULT_ZONE = ZoneId.systemDefault();

}
