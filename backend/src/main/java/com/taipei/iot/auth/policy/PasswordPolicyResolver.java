package com.taipei.iot.auth.policy;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Resolves the effective {@link PasswordPolicy} for a given tenant using a three-tier
 * fallback (see spec §5.3):
 *
 * <pre>
 *   tenant override (tenant_id = X) → platform default (tenant_id = '__PLATFORM__') → hard-coded
 * </pre>
 *
 * <p>
 * <b>No in-process cache.</b> Password-policy resolution sits on extremely low-QPS paths
 * (password change, admin user create / reset, self-registration) and the DB row count is
 * tiny (~15 keys × N tenants). Removing the previous 60 s local cache eliminates the
 * multi-instance staleness window that would otherwise let weak passwords slip through on
 * Pods whose cache had not yet expired after a policy tightening. See
 * {@code 01-docs/new-feature/cache/04-current-inventory.md} §3.2.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PasswordPolicyResolver {

	/** Reserved tenantId sentinel for platform-level defaults. */
	public static final String PLATFORM_SENTINEL = "__PLATFORM__";

	private final PasswordPolicyDao dao;

	/**
	 * @param tenantId tenant to resolve; {@code null} or {@link #PLATFORM_SENTINEL}
	 * returns the platform-default policy
	 */
	public PasswordPolicy resolve(String tenantId) {
		String key = (tenantId == null || tenantId.isBlank()) ? PLATFORM_SENTINEL : tenantId;
		return doResolve(key);
	}

	/**
	 * Read raw tenant-level overrides without merging the platform defaults. Used by the
	 * "show me only my overrides" admin endpoint.
	 */
	public Map<String, String> readRawOverrides(String tenantId) {
		return dao.findAllForTenant(tenantId);
	}

	/** Read the raw platform-default rows (used by the platform settings endpoint). */
	public Map<String, String> readRawPlatformDefaults() {
		return dao.findAllForTenant(PLATFORM_SENTINEL);
	}

	// ── internals ───────────────────────────────────────────────────────

	private PasswordPolicy doResolve(String tenantId) {
		Map<String, String> tenantValues = PLATFORM_SENTINEL.equals(tenantId) ? Map.of()
				: dao.findAllForTenant(tenantId);
		Map<String, String> platformValues = dao.findAllForTenant(PLATFORM_SENTINEL);

		return PasswordPolicy.builder()
			.minLength(intValue(PasswordPolicyKey.MIN_LENGTH, tenantValues, platformValues))
			.requireUppercase(boolValue(PasswordPolicyKey.REQUIRE_UPPERCASE, tenantValues, platformValues))
			.requireLowercase(boolValue(PasswordPolicyKey.REQUIRE_LOWERCASE, tenantValues, platformValues))
			.requireDigit(boolValue(PasswordPolicyKey.REQUIRE_DIGIT, tenantValues, platformValues))
			.requireSpecial(boolValue(PasswordPolicyKey.REQUIRE_SPECIAL, tenantValues, platformValues))
			.historyCount(intValue(PasswordPolicyKey.HISTORY_COUNT, tenantValues, platformValues))
			.maxLength(intValue(PasswordPolicyKey.MAX_LENGTH, tenantValues, platformValues))
			.minSpecialChars(intValue(PasswordPolicyKey.MIN_SPECIAL_CHARS, tenantValues, platformValues))
			.minDigits(intValue(PasswordPolicyKey.MIN_DIGITS, tenantValues, platformValues))
			.minUppercase(intValue(PasswordPolicyKey.MIN_UPPERCASE, tenantValues, platformValues))
			.minLowercase(intValue(PasswordPolicyKey.MIN_LOWERCASE, tenantValues, platformValues))
			.notContainsUsername(boolValue(PasswordPolicyKey.NOT_CONTAINS_USERNAME, tenantValues, platformValues))
			.expireDays(intValue(PasswordPolicyKey.EXPIRE_DAYS, tenantValues, platformValues))
			.forceChangeOnFirstLogin(
					boolValue(PasswordPolicyKey.FORCE_CHANGE_ON_FIRST_LOGIN, tenantValues, platformValues))
			.forceChangeOnAdminReset(
					boolValue(PasswordPolicyKey.FORCE_CHANGE_ON_ADMIN_RESET, tenantValues, platformValues))
			.build();
	}

	private int intValue(PasswordPolicyKey k, Map<String, String> tenant, Map<String, String> platform) {
		String raw = pick(k.getKey(), tenant, platform, k.getDefaultValue());
		try {
			return Integer.parseInt(raw);
		}
		catch (NumberFormatException e) {
			log.warn("Invalid integer for policy key {} = '{}', falling back to default {}", k.getKey(), raw,
					k.getDefaultValue());
			return Integer.parseInt(k.getDefaultValue());
		}
	}

	private boolean boolValue(PasswordPolicyKey k, Map<String, String> tenant, Map<String, String> platform) {
		return Boolean.parseBoolean(pick(k.getKey(), tenant, platform, k.getDefaultValue()));
	}

	private String pick(String key, Map<String, String> tenant, Map<String, String> platform, String hardDefault) {
		String v = tenant.get(key);
		if (v != null)
			return v;
		v = platform.get(key);
		if (v != null)
			return v;
		return hardDefault;
	}

}
