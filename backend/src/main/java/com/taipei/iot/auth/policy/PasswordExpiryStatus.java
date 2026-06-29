package com.taipei.iot.auth.policy;

/**
 * [Phase 3] Outcome of a password expiry / forced-change evaluation.
 *
 * <ul>
 * <li>{@link #OK} — credential is valid, login may proceed normally.</li>
 * <li>{@link #EXPIRED} — password age exceeds the tenant/platform {@code expire_days}
 * policy. User must change password before being issued a full access token.</li>
 * <li>{@link #FORCE_CHANGE} — administrative flag {@code force_change_password=true} is
 * set (initial user creation, admin-reset). Same downstream handling as
 * {@link #EXPIRED}.</li>
 * </ul>
 */
public enum PasswordExpiryStatus {

	OK, EXPIRED, FORCE_CHANGE

}
