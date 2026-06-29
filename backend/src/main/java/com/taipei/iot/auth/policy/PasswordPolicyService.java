package com.taipei.iot.auth.policy;

import com.taipei.iot.auth.policy.dto.PasswordPolicyDto;
import com.taipei.iot.auth.policy.dto.UpdatePasswordPolicyRequest;
import com.taipei.iot.common.enums.ErrorCode;
import com.taipei.iot.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * Read/write façade for password-policy settings.
 *
 * <p>
 * Two write paths exist:
 * <ul>
 * <li>{@link #updatePlatformDefault} — SUPER_ADMIN writes to the platform sentinel; only
 * the key's own {@code platformFloor} constrains the value</li>
 * <li>{@link #updateTenantOverride} — TENANT_ADMIN writes to their tenant; value must
 * satisfy the platform-default lower bound (spec D-4)</li>
 * </ul>
 *
 * <p>
 * Reads go straight through {@link PasswordPolicyResolver}, which holds no in-process
 * cache, so writes take effect immediately on every node without an explicit eviction
 * step. See {@code 01-docs/new-feature/cache/04-current-inventory.md} §3.2 for the
 * rationale.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordPolicyService {

	private final PasswordPolicyResolver resolver;

	private final PasswordPolicyDao dao;

	// ── reads ───────────────────────────────────────────────────────────

	@Transactional(readOnly = true)
	public PasswordPolicyDto getEffective(String tenantId) {
		return toDto(resolver.resolve(tenantId));
	}

	@Transactional(readOnly = true)
	public Map<String, String> getTenantOverrides(String tenantId) {
		if (tenantId == null || tenantId.isBlank()) {
			throw new BusinessException(ErrorCode.PERMISSION_DENIED, "tenantId required");
		}
		return resolver.readRawOverrides(tenantId);
	}

	@Transactional(readOnly = true)
	public Map<String, String> getPlatformDefaults() {
		return resolver.readRawPlatformDefaults();
	}

	// ── writes ──────────────────────────────────────────────────────────

	@Transactional
	public void updatePlatformDefault(UpdatePasswordPolicyRequest req) {
		PasswordPolicyKey key = PasswordPolicyKey.fromKey(req.getKey())
			.orElseThrow(() -> new BusinessException(ErrorCode.PASSWORD_POLICY_INVALID_KEY, req.getKey()));
		validateValueFormat(key, req.getValue());
		// Platform itself must respect the key's own hard floor (catches typos like
		// min_length=0).
		if (key.getPlatformFloor() != null) {
			int v = Integer.parseInt(req.getValue());
			if (v < key.getPlatformFloor()) {
				throw new BusinessException(ErrorCode.PASSWORD_POLICY_BELOW_PLATFORM_MINIMUM,
						key.getKey() + " 不可低於 " + key.getPlatformFloor());
			}
		}
		dao.upsert(PasswordPolicyResolver.PLATFORM_SENTINEL, key.getKey(), req.getValue(), "平台預設：" + key.getKey());
		log.info("Platform password policy updated: {} = {}", key.getKey(), req.getValue());
	}

	@Transactional
	public void updateTenantOverride(String tenantId, UpdatePasswordPolicyRequest req) {
		if (tenantId == null || tenantId.isBlank()) {
			throw new BusinessException(ErrorCode.PERMISSION_DENIED, "tenantId required");
		}
		PasswordPolicyKey key = PasswordPolicyKey.fromKey(req.getKey())
			.orElseThrow(() -> new BusinessException(ErrorCode.PASSWORD_POLICY_INVALID_KEY, req.getKey()));
		validateValueFormat(key, req.getValue());

		// Enforce platform lower bound for INT keys (spec D-4: platform sets only
		// floors).
		if (key.getType() == PasswordPolicyKey.PolicyType.INT) {
			int proposed = Integer.parseInt(req.getValue());
			int platformMin = readPlatformIntOrDefault(key);
			if (proposed < platformMin) {
				throw new BusinessException(ErrorCode.PASSWORD_POLICY_BELOW_PLATFORM_MINIMUM,
						key.getKey() + " 不可低於平台下限 " + platformMin);
			}
		}
		dao.upsert(tenantId, key.getKey(), req.getValue(), "租戶覆寫：" + key.getKey());
		log.info("Tenant {} password policy updated: {} = {}", tenantId, key.getKey(), req.getValue());
	}

	@Transactional
	public void deleteTenantOverride(String tenantId, String rawKey) {
		if (tenantId == null || tenantId.isBlank()) {
			throw new BusinessException(ErrorCode.PERMISSION_DENIED, "tenantId required");
		}
		PasswordPolicyKey key = PasswordPolicyKey.fromKey(rawKey)
			.orElseThrow(() -> new BusinessException(ErrorCode.PASSWORD_POLICY_INVALID_KEY, rawKey));
		dao.delete(tenantId, key.getKey());
		log.info("Tenant {} password policy override deleted: {}", tenantId, key.getKey());
	}

	// ── helpers ─────────────────────────────────────────────────────────

	private int readPlatformIntOrDefault(PasswordPolicyKey key) {
		String raw = resolver.readRawPlatformDefaults().getOrDefault(key.getKey(), key.getDefaultValue());
		try {
			return Integer.parseInt(raw);
		}
		catch (NumberFormatException e) {
			return Integer.parseInt(key.getDefaultValue());
		}
	}

	private void validateValueFormat(PasswordPolicyKey key, String value) {
		switch (key.getType()) {
			case INT -> {
				try {
					Integer.parseInt(value);
				}
				catch (NumberFormatException e) {
					throw new BusinessException(ErrorCode.PASSWORD_POLICY_INVALID_VALUE, key.getKey() + " 必須為整數");
				}
			}
			case BOOL -> {
				if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)) {
					throw new BusinessException(ErrorCode.PASSWORD_POLICY_INVALID_VALUE,
							key.getKey() + " 必須為 true 或 false");
				}
			}
		}
	}

	private PasswordPolicyDto toDto(PasswordPolicy p) {
		return PasswordPolicyDto.builder()
			.minLength(p.getMinLength())
			.requireUppercase(p.isRequireUppercase())
			.requireLowercase(p.isRequireLowercase())
			.requireDigit(p.isRequireDigit())
			.requireSpecial(p.isRequireSpecial())
			.historyCount(p.getHistoryCount())
			.maxLength(p.getMaxLength())
			.minSpecialChars(p.getMinSpecialChars())
			.minDigits(p.getMinDigits())
			.minUppercase(p.getMinUppercase())
			.minLowercase(p.getMinLowercase())
			.notContainsUsername(p.isNotContainsUsername())
			.expireDays(p.getExpireDays())
			.forceChangeOnFirstLogin(p.isForceChangeOnFirstLogin())
			.forceChangeOnAdminReset(p.isForceChangeOnAdminReset())
			.describe(p.describe())
			.build();
	}

}
