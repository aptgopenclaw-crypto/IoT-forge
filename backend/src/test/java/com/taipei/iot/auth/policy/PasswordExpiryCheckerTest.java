package com.taipei.iot.auth.policy;

import com.taipei.iot.auth.entity.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * [Phase 3] Unit tests for {@link PasswordExpiryChecker}.
 *
 * <p>
 * Covers the three return states (OK / EXPIRED / FORCE_CHANGE) and the policy fallback
 * when {@code expire_days} is 0 (expiry disabled).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PasswordExpiryCheckerTest {

	@Mock
	private PasswordPolicyResolver resolver;

	@InjectMocks
	private PasswordExpiryChecker checker;

	private PasswordPolicy policyWithExpiry90;

	private PasswordPolicy policyNoExpiry;

	@BeforeEach
	void setUp() {
		policyWithExpiry90 = PasswordPolicy.builder()
			.minLength(8)
			.expireDays(90)
			.forceChangeOnFirstLogin(true)
			.forceChangeOnAdminReset(true)
			.build();
		policyNoExpiry = PasswordPolicy.builder()
			.minLength(8)
			.expireDays(0)
			.forceChangeOnFirstLogin(true)
			.forceChangeOnAdminReset(true)
			.build();
	}

	@Test
	void forceChangeFlag_takesPrecedence_overExpiry() {
		// forceChangePassword=true must short-circuit even if password was changed
		// recently
		UserEntity u = UserEntity.builder()
			.userId("u1")
			.forceChangePassword(true)
			.passwordChangedAt(LocalDateTime.now())
			.build();

		// resolver should not even be consulted; lenient strictness allows the missing
		// stub
		assertThat(checker.check(u, "tenant-A")).isEqualTo(PasswordExpiryStatus.FORCE_CHANGE);
	}

	@Test
	void expireDaysZero_alwaysOk() {
		when(resolver.resolve("tenant-A")).thenReturn(policyNoExpiry);
		UserEntity u = UserEntity.builder()
			.userId("u1")
			.forceChangePassword(false)
			.passwordChangedAt(LocalDateTime.now().minusYears(5))
			.build();

		assertThat(checker.check(u, "tenant-A")).isEqualTo(PasswordExpiryStatus.OK);
	}

	@Test
	void changedRecently_isOk() {
		when(resolver.resolve(null)).thenReturn(policyWithExpiry90);
		UserEntity u = UserEntity.builder()
			.userId("u1")
			.forceChangePassword(false)
			.passwordChangedAt(LocalDateTime.now().minusDays(10))
			.build();

		assertThat(checker.check(u, null)).isEqualTo(PasswordExpiryStatus.OK);
	}

	@Test
	void changedBeyondExpireDays_isExpired() {
		when(resolver.resolve(null)).thenReturn(policyWithExpiry90);
		UserEntity u = UserEntity.builder()
			.userId("u1")
			.forceChangePassword(false)
			.passwordChangedAt(LocalDateTime.now().minusDays(120))
			.build();

		assertThat(checker.check(u, null)).isEqualTo(PasswordExpiryStatus.EXPIRED);
	}

	@Test
	void nullChangedAt_defensivelyExpired() {
		// V45 backfills this column, but if somehow null we expire to force remediation.
		when(resolver.resolve(null)).thenReturn(policyWithExpiry90);
		UserEntity u = UserEntity.builder().userId("u1").forceChangePassword(false).passwordChangedAt(null).build();

		assertThat(checker.check(u, null)).isEqualTo(PasswordExpiryStatus.EXPIRED);
	}

}
