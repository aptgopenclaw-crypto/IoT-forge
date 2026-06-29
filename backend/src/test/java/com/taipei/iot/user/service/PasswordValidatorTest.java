package com.taipei.iot.user.service;

import com.taipei.iot.auth.policy.PasswordPolicy;
import com.taipei.iot.auth.policy.PasswordPolicyResolver;
import com.taipei.iot.common.enums.ErrorCode;
import com.taipei.iot.common.exception.BusinessException;
import com.taipei.iot.user.entity.PasswordHistoryEntity;
import com.taipei.iot.user.repository.PasswordHistoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PasswordValidatorTest {

	@Mock
	private PasswordHistoryRepository passwordHistoryRepository;

	@Mock
	private PasswordEncoder passwordEncoder;

	@Mock
	private PasswordPolicyResolver policyResolver;

	@InjectMocks
	private PasswordValidator validator;

	private PasswordPolicy strictPolicy() {
		return PasswordPolicy.builder()
			.minLength(8)
			.requireUppercase(true)
			.requireLowercase(true)
			.requireDigit(true)
			.requireSpecial(true)
			.historyCount(5)
			.build();
	}

	/** Phase 2 strict policy: counts >1 + not_contains_username + max_length. */
	private PasswordPolicy phase2StrictPolicy() {
		return PasswordPolicy.builder()
			.minLength(8)
			.requireUppercase(true)
			.requireLowercase(true)
			.requireDigit(true)
			.requireSpecial(true)
			.historyCount(5)
			.maxLength(128)
			.minUppercase(2)
			.minLowercase(2)
			.minDigits(2)
			.minSpecialChars(2)
			.notContainsUsername(true)
			.build();
	}

	@BeforeEach
	void stubPolicy() {
		when(policyResolver.resolve(any())).thenReturn(strictPolicy());
	}

	@Test
	void validate_strongPassword_passes() {
		assertDoesNotThrow(() -> validator.validate("tenant-A", "GoodPass1!", null));
	}

	@Test
	void validate_tooShort_throws() {
		BusinessException ex = assertThrows(BusinessException.class,
				() -> validator.validate("tenant-A", "Aa1!", null));
		assertEquals(ErrorCode.RESET_PASSWORD_ERROR, ex.getErrorCode());
	}

	@Test
	void validate_missingUppercase_throws() {
		BusinessException ex = assertThrows(BusinessException.class,
				() -> validator.validate("tenant-A", "goodpass1!", null));
		assertTrue(ex.getMessage().contains("大寫"));
	}

	@Test
	void validate_missingLowercase_throws() {
		assertThrows(BusinessException.class, () -> validator.validate("tenant-A", "GOODPASS1!", null));
	}

	@Test
	void validate_missingDigit_throws() {
		assertThrows(BusinessException.class, () -> validator.validate("tenant-A", "GoodPass!!", null));
	}

	@Test
	void validate_missingSpecial_throws() {
		assertThrows(BusinessException.class, () -> validator.validate("tenant-A", "GoodPass11", null));
	}

	@Test
	void validate_respectsRelaxedPolicy() {
		when(policyResolver.resolve("tenant-A")).thenReturn(PasswordPolicy.builder()
			.minLength(6)
			.requireUppercase(false)
			.requireLowercase(false)
			.requireDigit(false)
			.requireSpecial(false)
			.historyCount(0)
			.build());

		assertDoesNotThrow(() -> validator.validate("tenant-A", "simple", null));
	}

	@Test
	void checkNotRecentlyUsed_noMatch_passes() {
		when(passwordHistoryRepository.findByUserIdOrderByCreateTimeDesc(eq("u1"), any(Pageable.class)))
			.thenReturn(List.of(PasswordHistoryEntity.builder().passwordHash("h1").build()));
		when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

		assertDoesNotThrow(() -> validator.checkNotRecentlyUsed("tenant-A", "u1", "NewPass1!"));
	}

	@Test
	void checkNotRecentlyUsed_match_throws() {
		when(passwordHistoryRepository.findByUserIdOrderByCreateTimeDesc(eq("u1"), any(Pageable.class)))
			.thenReturn(List.of(PasswordHistoryEntity.builder().passwordHash("h1").build()));
		when(passwordEncoder.matches("NewPass1!", "h1")).thenReturn(true);

		BusinessException ex = assertThrows(BusinessException.class,
				() -> validator.checkNotRecentlyUsed("tenant-A", "u1", "NewPass1!"));
		assertEquals(ErrorCode.PASSWORD_RECENTLY_USED, ex.getErrorCode());
	}

	@Test
	void checkNotRecentlyUsed_historyDisabled_skipsRepoCall() {
		when(policyResolver.resolve("tenant-A")).thenReturn(PasswordPolicy.builder()
			.minLength(8)
			.requireUppercase(true)
			.requireLowercase(true)
			.requireDigit(true)
			.requireSpecial(true)
			.historyCount(0)
			.build());

		validator.checkNotRecentlyUsed("tenant-A", "u1", "any");

		verify(passwordHistoryRepository, never()).findByUserIdOrderByCreateTimeDesc(anyString(), any(Pageable.class));
	}

	// ── Phase 2 ──────────────────────────────────────────────────────────

	@Test
	void phase2_maxLengthExceeded_throws() {
		when(policyResolver.resolve("tenant-A"))
			.thenReturn(PasswordPolicy.builder().minLength(8).maxLength(16).build());
		BusinessException ex = assertThrows(BusinessException.class,
				() -> validator.validate("tenant-A", "A".repeat(17), null));
		assertTrue(ex.getMessage().contains("不可超過 16"));
	}

	@Test
	void phase2_maxLengthZeroDisablesCap() {
		when(policyResolver.resolve("tenant-A")).thenReturn(PasswordPolicy.builder().minLength(4).maxLength(0).build());
		assertDoesNotThrow(() -> validator.validate("tenant-A", "abcdefghij", null));
	}

	@Test
	void phase2_minUppercase_belowThreshold_throws() {
		when(policyResolver.resolve(any())).thenReturn(phase2StrictPolicy());
		// Only one uppercase letter → fails min_uppercase=2.
		BusinessException ex = assertThrows(BusinessException.class,
				() -> validator.validate("tenant-A", "Aabbcc11!!", null));
		assertTrue(ex.getMessage().contains("大寫"));
	}

	@Test
	void phase2_minCounts_allMet_passes() {
		when(policyResolver.resolve(any())).thenReturn(phase2StrictPolicy());
		assertDoesNotThrow(() -> validator.validate("tenant-A", "AAbb11!!cc", null));
	}

	@Test
	void phase2_minDigits_belowThreshold_throws() {
		when(policyResolver.resolve(any())).thenReturn(phase2StrictPolicy());
		BusinessException ex = assertThrows(BusinessException.class,
				() -> validator.validate("tenant-A", "AAbbcc1!!d", null));
		assertTrue(ex.getMessage().contains("數字"));
	}

	@Test
	void phase2_minSpecialChars_belowThreshold_throws() {
		when(policyResolver.resolve(any())).thenReturn(phase2StrictPolicy());
		BusinessException ex = assertThrows(BusinessException.class,
				() -> validator.validate("tenant-A", "AAbb1122!d", null));
		assertTrue(ex.getMessage().contains("特殊"));
	}

	@Test
	void phase2_containsUsername_caseInsensitive_throws() {
		when(policyResolver.resolve(any()))
			.thenReturn(PasswordPolicy.builder().minLength(6).notContainsUsername(true).build());
		BusinessException ex = assertThrows(BusinessException.class, () -> validator.validate("tenant-A", "myALICE99",
				new PasswordValidator.UserContext("alice", "alice@example.com")));
		assertTrue(ex.getMessage().contains("使用者名稱"));
	}

	@Test
	void phase2_containsEmailLocalPart_throws() {
		when(policyResolver.resolve(any()))
			.thenReturn(PasswordPolicy.builder().minLength(6).notContainsUsername(true).build());
		BusinessException ex = assertThrows(BusinessException.class, () -> validator.validate("tenant-A", "xJohn.Doe99",
				new PasswordValidator.UserContext("u-1", "john.doe@example.com")));
		assertTrue(ex.getMessage().contains("電子郵件"));
	}

	@Test
	void phase2_notContainsUsername_nullContext_skipped() {
		when(policyResolver.resolve(any()))
			.thenReturn(PasswordPolicy.builder().minLength(6).notContainsUsername(true).build());
		assertDoesNotThrow(() -> validator.validate("tenant-A", "anything-goes", null));
	}

	@Test
	void phase2_notContainsUsername_disabled_allowsMatch() {
		when(policyResolver.resolve(any()))
			.thenReturn(PasswordPolicy.builder().minLength(6).notContainsUsername(false).build());
		assertDoesNotThrow(() -> validator.validate("tenant-A", "myalice99",
				new PasswordValidator.UserContext("alice", "alice@example.com")));
	}

	// === N-8: HARD_MAX enforcement ===

	@Test
	void validate_exceedsHardMax_shouldThrow() {
		// Policy has maxLength=0 (disabled), but HARD_MAX should still catch it
		when(policyResolver.resolve(any())).thenReturn(PasswordPolicy.builder().minLength(8).maxLength(0).build());

		String oversized = "A".repeat(PasswordValidator.HARD_MAX + 1);

		BusinessException ex = assertThrows(BusinessException.class,
				() -> validator.validate("tenant-A", oversized, null));
		assertEquals(ErrorCode.RESET_PASSWORD_ERROR, ex.getErrorCode());
		assertTrue(ex.getMessage().contains(String.valueOf(PasswordValidator.HARD_MAX)));
	}

	@Test
	void validate_exactlyHardMax_shouldPass() {
		when(policyResolver.resolve(any())).thenReturn(PasswordPolicy.builder().minLength(8).maxLength(0).build());

		// 1024 chars — should pass (no policy maxLength, at exactly HARD_MAX)
		String atLimit = "Aa1!" + "x".repeat(PasswordValidator.HARD_MAX - 4);

		assertDoesNotThrow(() -> validator.validate("tenant-A", atLimit, null));
	}

	@Test
	void validate_policyMaxLengthStillEnforced_withinHardMax() {
		// policy.maxLength=128, password is 200 chars (under HARD_MAX but over policy)
		when(policyResolver.resolve(any())).thenReturn(PasswordPolicy.builder().minLength(8).maxLength(128).build());

		String overPolicy = "A".repeat(200);

		BusinessException ex = assertThrows(BusinessException.class,
				() -> validator.validate("tenant-A", overPolicy, null));
		assertTrue(ex.getMessage().contains("128"));
	}

}
