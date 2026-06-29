package com.taipei.iot.auth.policy;

import com.taipei.iot.auth.entity.UserEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * [Phase 3] Evaluates whether an authenticated user must change their password before
 * being issued a normal access token.
 *
 * <p>
 * Order of precedence:
 * <ol>
 * <li>{@code user.forceChangePassword == true} →
 * {@link PasswordExpiryStatus#FORCE_CHANGE}</li>
 * <li>Policy {@code password.expire_days <= 0} → expiry disabled →
 * {@link PasswordExpiryStatus#OK}</li>
 * <li>{@code passwordChangedAt + expireDays < now} →
 * {@link PasswordExpiryStatus#EXPIRED}</li>
 * <li>otherwise → {@link PasswordExpiryStatus#OK}</li>
 * </ol>
 *
 * <p>
 * Per spec decision D-2 this check runs AFTER successful
 * {@link org.springframework.security.crypto.password.PasswordEncoder#matches matches()}
 * so that login still treats the credential itself as valid; only token issuance is
 * gated.
 */
@Component
@RequiredArgsConstructor
public class PasswordExpiryChecker {

	private final PasswordPolicyResolver policyResolver;

	/**
	 * @param user authenticated user (must already have passed {@code matches()})
	 * @param tenantId tenant context for policy resolution; {@code null} falls back to
	 * platform defaults
	 */
	public PasswordExpiryStatus check(UserEntity user, String tenantId) {
		if (Boolean.TRUE.equals(user.getForceChangePassword())) {
			return PasswordExpiryStatus.FORCE_CHANGE;
		}

		PasswordPolicy policy = policyResolver.resolve(tenantId);
		int expireDays = policy.getExpireDays();
		if (expireDays <= 0) {
			return PasswordExpiryStatus.OK;
		}

		LocalDateTime changedAt = user.getPasswordChangedAt();
		if (changedAt == null) {
			// Defensive: existing rows are backfilled by V45, but if somehow null,
			// treat as expired to force a remediation flow rather than silently allow.
			return PasswordExpiryStatus.EXPIRED;
		}

		LocalDateTime expiresAt = changedAt.plusDays(expireDays);
		return expiresAt.isBefore(LocalDateTime.now()) ? PasswordExpiryStatus.EXPIRED : PasswordExpiryStatus.OK;
	}

}
