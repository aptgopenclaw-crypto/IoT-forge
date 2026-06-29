package com.taipei.iot.user.service;

import com.taipei.iot.auth.entity.ChangePasswordLogEntity;
import com.taipei.iot.auth.entity.UserEntity;
import com.taipei.iot.auth.repository.ChangePasswordLogRepository;
import com.taipei.iot.auth.repository.UserRepository;
import com.taipei.iot.auth.service.UserSessionService;
import com.taipei.iot.common.enums.ErrorCode;
import com.taipei.iot.common.exception.BusinessException;
import com.taipei.iot.user.dto.request.ChangePasswordRequest;
import com.taipei.iot.user.dto.request.UpdateOwnProfileRequest;
import com.taipei.iot.user.entity.PasswordHistoryEntity;
import com.taipei.iot.user.repository.PasswordHistoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserSelfServiceTest {

	@InjectMocks
	private UserSelfService userSelfService;

	@Mock
	private UserRepository userRepository;

	@Mock
	private PasswordEncoder passwordEncoder;

	@Mock
	private PasswordValidator passwordValidator;

	@Mock
	private PasswordHistoryRepository passwordHistoryRepository;

	@Mock
	private ChangePasswordLogRepository changePasswordLogRepository;

	@Mock
	private UserAuditService userAuditService;

	@Mock
	private UserSessionService userSessionService;

	private UserEntity testUser;

	@BeforeEach
	void setUp() {
		testUser = UserEntity.builder()
			.userId("user-001")
			.email("user@test.com")
			.displayName("Test User")
			.phone("0912345678")
			.passwordHash("$2a$10$existingHash")
			.enabled(true)
			.locked(false)
			.loginFailCount(0)
			.isSuperAdmin(false)
			.build();
	}

	@Test
	void updateOwnProfile_shouldUpdateDisplayNameAndPhone() {
		when(userRepository.findById("user-001")).thenReturn(Optional.of(testUser));
		when(userRepository.save(any(UserEntity.class))).thenAnswer(inv -> inv.getArgument(0));

		UpdateOwnProfileRequest req = UpdateOwnProfileRequest.builder()
			.displayName("New Name")
			.phone("0987654321")
			.build();

		UserEntity result = userSelfService.updateOwnProfile("user-001", req);

		assertEquals("New Name", result.getDisplayName());
		assertEquals("0987654321", result.getPhone());
		verify(userAuditService).logAction(eq("UPDATE"), eq("user-001"), eq("user-001"), anyString());
	}

	@Test
	void changePassword_success() {
		when(userRepository.findById("user-001")).thenReturn(Optional.of(testUser));
		when(passwordEncoder.matches("OldPass000", "$2a$10$existingHash")).thenReturn(true);
		when(passwordEncoder.encode("NewPass123")).thenReturn("$2a$10$newHash");
		when(userRepository.save(any(UserEntity.class))).thenAnswer(inv -> inv.getArgument(0));
		when(passwordHistoryRepository.save(any(PasswordHistoryEntity.class))).thenAnswer(inv -> inv.getArgument(0));
		when(changePasswordLogRepository.save(any(ChangePasswordLogEntity.class)))
			.thenAnswer(inv -> inv.getArgument(0));

		ChangePasswordRequest req = ChangePasswordRequest.builder()
			.oldPassword("OldPass000")
			.newPassword("NewPass123")
			.build();

		userSelfService.changePassword("user-001", req, "session-jti-current");

		assertEquals("$2a$10$newHash", testUser.getPasswordHash());
		verify(passwordValidator).validate(any(), eq("NewPass123"), any());
		verify(passwordValidator).checkNotRecentlyUsed(any(), eq("user-001"), eq("NewPass123"));
		verify(passwordHistoryRepository).save(any(PasswordHistoryEntity.class));
		verify(changePasswordLogRepository).save(any(ChangePasswordLogEntity.class));
		verify(userAuditService).logAction(eq("UPDATE"), eq("user-001"), eq("user-001"), anyString());
		// N-4: 驗證密碼變更後撤銷其他 session
		verify(userSessionService).revokeAllExceptCurrent("user-001", "session-jti-current");
	}

	@Test
	void changePassword_oldPasswordIncorrect_shouldThrow() {
		when(userRepository.findById("user-001")).thenReturn(Optional.of(testUser));
		when(passwordEncoder.matches("WrongPass", "$2a$10$existingHash")).thenReturn(false);

		ChangePasswordRequest req = ChangePasswordRequest.builder()
			.oldPassword("WrongPass")
			.newPassword("NewPass123")
			.build();

		BusinessException ex = assertThrows(BusinessException.class,
				() -> userSelfService.changePassword("user-001", req, null));
		assertEquals(ErrorCode.OLD_PASSWORD_INCORRECT, ex.getErrorCode());
	}

	@Test
	void changePassword_invalidFormat_shouldThrowResetPasswordError() {
		when(userRepository.findById("user-001")).thenReturn(Optional.of(testUser));
		when(passwordEncoder.matches("OldPass000", "$2a$10$existingHash")).thenReturn(true);
		doThrow(new BusinessException(ErrorCode.RESET_PASSWORD_ERROR, "密碼長度至少 8 字元")).when(passwordValidator)
			.validate(any(), eq("short"), any());

		ChangePasswordRequest req = ChangePasswordRequest.builder()
			.oldPassword("OldPass000")
			.newPassword("short")
			.build();

		BusinessException ex = assertThrows(BusinessException.class,
				() -> userSelfService.changePassword("user-001", req, null));
		assertEquals(ErrorCode.RESET_PASSWORD_ERROR, ex.getErrorCode());
	}

	@Test
	void changePassword_recentlyUsed_shouldThrowPasswordRecentlyUsed() {
		when(userRepository.findById("user-001")).thenReturn(Optional.of(testUser));
		when(passwordEncoder.matches("OldPass000", "$2a$10$existingHash")).thenReturn(true);
		doThrow(new BusinessException(ErrorCode.PASSWORD_RECENTLY_USED)).when(passwordValidator)
			.checkNotRecentlyUsed(any(), eq("user-001"), eq("OldPass123"));

		ChangePasswordRequest req = ChangePasswordRequest.builder()
			.oldPassword("OldPass000")
			.newPassword("OldPass123")
			.build();

		BusinessException ex = assertThrows(BusinessException.class,
				() -> userSelfService.changePassword("user-001", req, null));
		assertEquals(ErrorCode.PASSWORD_RECENTLY_USED, ex.getErrorCode());
	}

	@Test
	void changePassword_withNullJti_shouldRevokeAllSessions() {
		when(userRepository.findById("user-001")).thenReturn(Optional.of(testUser));
		when(passwordEncoder.matches("OldPass000", "$2a$10$existingHash")).thenReturn(true);
		when(passwordEncoder.encode("NewPass123")).thenReturn("$2a$10$newHash");
		when(userRepository.save(any(UserEntity.class))).thenAnswer(inv -> inv.getArgument(0));
		when(passwordHistoryRepository.save(any(PasswordHistoryEntity.class))).thenAnswer(inv -> inv.getArgument(0));
		when(changePasswordLogRepository.save(any(ChangePasswordLogEntity.class)))
			.thenAnswer(inv -> inv.getArgument(0));

		ChangePasswordRequest req = ChangePasswordRequest.builder()
			.oldPassword("OldPass000")
			.newPassword("NewPass123")
			.build();

		userSelfService.changePassword("user-001", req, null);

		// JTI 為 null 時，revokeAllExceptCurrent 應仍被呼叫（內部會撤銷所有 session）
		verify(userSessionService).revokeAllExceptCurrent("user-001", null);
	}

}
