package com.taipei.iot.auth.provider.local;

import com.taipei.iot.auth.entity.UserEntity;
import com.taipei.iot.auth.provider.AuthType;
import com.taipei.iot.auth.provider.AuthenticationRequest;
import com.taipei.iot.auth.provider.AuthenticationResult;
import com.taipei.iot.auth.repository.UserRepository;
import com.taipei.iot.common.enums.ErrorCode;
import com.taipei.iot.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LocalAuthProviderTest {

	@InjectMocks
	private LocalAuthProvider localAuthProvider;

	@Mock
	private UserRepository userRepository;

	@Mock
	private PasswordEncoder passwordEncoder;

	private UserEntity validUser;

	@BeforeEach
	void setUp() {
		ReflectionTestUtils.setField(localAuthProvider, "maxFailCount", 5);
		ReflectionTestUtils.setField(localAuthProvider, "lockDurationMinutes", 10);

		validUser = UserEntity.builder()
			.userId("user-001")
			.email("test@example.com")
			.passwordHash("$2a$10$hashed")
			.displayName("Test User")
			.enabled(true)
			.locked(false)
			.loginFailCount(0)
			.isSuperAdmin(false)
			.build();
	}

	@Test
	void getType_returnsLOCAL() {
		assertEquals(AuthType.LOCAL, localAuthProvider.getType());
	}

	@Test
	void authenticate_success() {
		AuthenticationRequest request = AuthenticationRequest.builder()
			.identifier("test@example.com")
			.credential("password123")
			.build();

		when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(validUser));
		when(passwordEncoder.matches("password123", "$2a$10$hashed")).thenReturn(true);

		AuthenticationResult result = localAuthProvider.authenticate(request, null);

		assertNotNull(result);
		assertEquals("user-001", result.getLocalUserId());
		assertEquals("test@example.com", result.getEmail());
		assertEquals("Test User", result.getDisplayName());

		// Verify fail count reset and lastLoginAt updated
		verify(userRepository, times(1)).save(validUser);
		assertEquals(0, validUser.getLoginFailCount());
		assertNotNull(validUser.getLastLoginAt());
	}

	@Test
	void authenticate_userNotFound_throwsException() {
		AuthenticationRequest request = AuthenticationRequest.builder()
			.identifier("nonexistent@example.com")
			.credential("password123")
			.build();

		when(userRepository.findByEmail("nonexistent@example.com")).thenReturn(Optional.empty());

		BusinessException ex = assertThrows(BusinessException.class,
				() -> localAuthProvider.authenticate(request, null));
		assertEquals(ErrorCode.USER_NOT_FOUND, ex.getErrorCode());
	}

	@Test
	void authenticate_accountDisabled_throwsException() {
		validUser.setEnabled(false);
		AuthenticationRequest request = AuthenticationRequest.builder()
			.identifier("test@example.com")
			.credential("password123")
			.build();

		when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(validUser));

		BusinessException ex = assertThrows(BusinessException.class,
				() -> localAuthProvider.authenticate(request, null));
		assertEquals(ErrorCode.ACCOUNT_DISABLED, ex.getErrorCode());
	}

	@Test
	void authenticate_accountLocked_notExpired_throwsException() {
		validUser.setLocked(true);
		validUser.setLockedAt(LocalDateTime.now()); // Locked just now, not expired
		validUser.setLoginFailCount(5);

		AuthenticationRequest request = AuthenticationRequest.builder()
			.identifier("test@example.com")
			.credential("password123")
			.build();

		when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(validUser));

		BusinessException ex = assertThrows(BusinessException.class,
				() -> localAuthProvider.authenticate(request, null));
		assertEquals(ErrorCode.ACCOUNT_LOCKED, ex.getErrorCode());
	}

	@Test
	void authenticate_accountLocked_expired_autoUnlocks() {
		validUser.setLocked(true);
		validUser.setLockedAt(LocalDateTime.now().minusMinutes(15)); // Expired
		validUser.setLoginFailCount(5);

		AuthenticationRequest request = AuthenticationRequest.builder()
			.identifier("test@example.com")
			.credential("password123")
			.build();

		when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(validUser));
		when(passwordEncoder.matches("password123", "$2a$10$hashed")).thenReturn(true);

		AuthenticationResult result = localAuthProvider.authenticate(request, null);

		assertNotNull(result);
		assertEquals("user-001", result.getLocalUserId());
		// Auto-unlock should have been saved
		assertFalse(validUser.getLocked());
		assertNull(validUser.getLockedAt());
	}

	@Test
	void authenticate_wrongPassword_incrementsFailCount() {
		AuthenticationRequest request = AuthenticationRequest.builder()
			.identifier("test@example.com")
			.credential("wrong-password")
			.build();

		when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(validUser));
		when(passwordEncoder.matches("wrong-password", "$2a$10$hashed")).thenReturn(false);

		BusinessException ex = assertThrows(BusinessException.class,
				() -> localAuthProvider.authenticate(request, null));
		assertEquals(ErrorCode.LOGIN_FAIL, ex.getErrorCode());
		assertEquals(1, validUser.getLoginFailCount());
		assertFalse(validUser.getLocked());
	}

	@Test
	void authenticate_wrongPassword_reachesMaxFails_locksAccount() {
		validUser.setLoginFailCount(4); // One more fail = lock

		AuthenticationRequest request = AuthenticationRequest.builder()
			.identifier("test@example.com")
			.credential("wrong-password")
			.build();

		when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(validUser));
		when(passwordEncoder.matches("wrong-password", "$2a$10$hashed")).thenReturn(false);

		BusinessException ex = assertThrows(BusinessException.class,
				() -> localAuthProvider.authenticate(request, null));
		assertEquals(ErrorCode.LOGIN_FAIL, ex.getErrorCode());
		assertEquals(5, validUser.getLoginFailCount());
		assertTrue(validUser.getLocked());
		assertNotNull(validUser.getLockedAt());
	}

}
