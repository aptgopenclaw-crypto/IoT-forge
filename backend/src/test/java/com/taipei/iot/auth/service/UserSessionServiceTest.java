package com.taipei.iot.auth.service;

import com.taipei.iot.auth.dto.response.SessionDto;
import com.taipei.iot.auth.entity.UserSessionEntity;
import com.taipei.iot.auth.repository.UserSessionRepository;
import com.taipei.iot.auth.service.impl.UserSessionServiceImpl;
import com.taipei.iot.common.enums.ErrorCode;
import com.taipei.iot.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserSessionServiceTest {

	@Mock
	private UserSessionRepository userSessionRepository;

	@Mock
	private StringRedisTemplate stringRedisTemplate;

	@Mock
	private ValueOperations<String, String> valueOps;

	@InjectMocks
	private UserSessionServiceImpl service;

	@Test
	void listMine_returnsActiveSessions_markingCurrentByJti() {
		LocalDateTime now = LocalDateTime.now();
		UserSessionEntity s1 = UserSessionEntity.builder()
			.sessionId("jti-current")
			.userId("u1")
			.tenantId("T1")
			.ipAddress("10.0.0.1")
			.userAgent("Chrome")
			.issuedAt(now.minusHours(1))
			.lastSeenAt(now.minusMinutes(5))
			.expiresAt(now.plusDays(7))
			.revoked(false)
			.build();
		UserSessionEntity s2 = UserSessionEntity.builder()
			.sessionId("jti-other")
			.userId("u1")
			.tenantId("T1")
			.issuedAt(now.minusDays(2))
			.lastSeenAt(now.minusHours(2))
			.expiresAt(now.plusDays(5))
			.revoked(false)
			.build();
		when(userSessionRepository.findActiveByUserId(eq("u1"), any())).thenReturn(List.of(s1, s2));

		List<SessionDto> result = service.listMine("u1", "jti-current");

		assertEquals(2, result.size());
		assertTrue(result.get(0).isCurrent());
		assertFalse(result.get(1).isCurrent());
		assertEquals("Chrome", result.get(0).getUserAgent());
	}

	@Test
	void revoke_writesRedisAndDb() {
		LocalDateTime now = LocalDateTime.now();
		UserSessionEntity session = UserSessionEntity.builder()
			.sessionId("jti-1")
			.userId("u1")
			.expiresAt(now.plusDays(1))
			.revoked(false)
			.build();
		when(userSessionRepository.findBySessionIdAndUserId("jti-1", "u1")).thenReturn(Optional.of(session));
		when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);

		service.revoke("u1", "jti-1");

		verify(userSessionRepository).revokeById(eq("jti-1"), any());
		verify(valueOps).set(eq("auth:revoked_refresh:jti-1"), eq("1"), anyLong(), eq(TimeUnit.MILLISECONDS));
	}

	@Test
	void revoke_alreadyRevoked_isNoop() {
		UserSessionEntity session = UserSessionEntity.builder()
			.sessionId("jti-1")
			.userId("u1")
			.expiresAt(LocalDateTime.now().plusDays(1))
			.revoked(true)
			.build();
		when(userSessionRepository.findBySessionIdAndUserId("jti-1", "u1")).thenReturn(Optional.of(session));

		service.revoke("u1", "jti-1");

		verify(userSessionRepository, never()).revokeById(any(), any());
		verifyNoInteractions(stringRedisTemplate);
	}

	@Test
	void revoke_notOwner_throwsSessionNotFound() {
		when(userSessionRepository.findBySessionIdAndUserId("jti-1", "evil-user")).thenReturn(Optional.empty());

		BusinessException ex = assertThrows(BusinessException.class, () -> service.revoke("evil-user", "jti-1"));
		assertEquals(ErrorCode.SESSION_NOT_FOUND, ex.getErrorCode());
	}

	@Test
	void revoke_redisFailure_doesNotRollback() {
		UserSessionEntity session = UserSessionEntity.builder()
			.sessionId("jti-1")
			.userId("u1")
			.expiresAt(LocalDateTime.now().plusDays(1))
			.revoked(false)
			.build();
		when(userSessionRepository.findBySessionIdAndUserId("jti-1", "u1")).thenReturn(Optional.of(session));
		when(stringRedisTemplate.opsForValue()).thenThrow(new RuntimeException("redis down"));

		// 不應拋出例外
		service.revoke("u1", "jti-1");
		verify(userSessionRepository).revokeById(eq("jti-1"), any());
	}

}
