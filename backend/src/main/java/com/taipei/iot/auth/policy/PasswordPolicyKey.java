package com.taipei.iot.auth.policy;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.Optional;

/**
 * Catalogue of password policy setting keys with their type, hard-coded fallback default,
 * and platform-level lower bound (the floor the platform admin may set and the tenant
 * cannot weaken — see spec D-4).
 *
 * <p>
 * {@code platformFloor} is {@code null} for boolean keys where the concept of "lower
 * bound" does not apply (e.g. tenant may freely toggle requireSpecial).
 */
@Getter
@RequiredArgsConstructor
public enum PasswordPolicyKey {

	MIN_LENGTH("password.min_length", PolicyType.INT, "8", 8),
	REQUIRE_UPPERCASE("password.require_uppercase", PolicyType.BOOL, "true", null),
	REQUIRE_LOWERCASE("password.require_lowercase", PolicyType.BOOL, "true", null),
	REQUIRE_DIGIT("password.require_digit", PolicyType.BOOL, "true", null),
	REQUIRE_SPECIAL("password.require_special", PolicyType.BOOL, "true", null),
	HISTORY_COUNT("password.history_count", PolicyType.INT, "5", 1),

	// ── Phase 2 ──
	/**
	 * Maximum password length (DoS guard). Floor 64 prevents tenant from setting absurdly
	 * low cap.
	 */
	MAX_LENGTH("password.max_length", PolicyType.INT, "128", 64),
	MIN_SPECIAL_CHARS("password.min_special_chars", PolicyType.INT, "1", 1),
	MIN_DIGITS("password.min_digits", PolicyType.INT, "1", 1),
	MIN_UPPERCASE("password.min_uppercase", PolicyType.INT, "1", 1),
	MIN_LOWERCASE("password.min_lowercase", PolicyType.INT, "1", 1),
	/** Reject passwords containing username or email local-part (case-insensitive). */
	NOT_CONTAINS_USERNAME("password.not_contains_username", PolicyType.BOOL, "true", null),

	// ── Phase 3 ──
	/** Password validity in days; 0 disables expiry entirely. No platform floor. */
	EXPIRE_DAYS("password.expire_days", PolicyType.INT, "90", null),
	FORCE_CHANGE_ON_FIRST_LOGIN("password.force_change_on_first_login", PolicyType.BOOL, "true", null),
	FORCE_CHANGE_ON_ADMIN_RESET("password.force_change_on_admin_reset", PolicyType.BOOL, "true", null);

	private final String key;

	private final PolicyType type;

	private final String defaultValue;

	/**
	 * Minimum allowed value for INT keys when set by tenant; {@code null} for BOOL or "no
	 * floor".
	 */
	private final Integer platformFloor;

	public static Optional<PasswordPolicyKey> fromKey(String key) {
		return Arrays.stream(values()).filter(k -> k.key.equals(key)).findFirst();
	}

	public enum PolicyType {

		INT, BOOL

	}

}
