package com.taipei.iot.user.service;

import com.taipei.iot.auth.policy.PasswordPolicy;
import com.taipei.iot.auth.policy.PasswordPolicyResolver;
import com.taipei.iot.common.enums.ErrorCode;
import com.taipei.iot.common.exception.BusinessException;
import com.taipei.iot.user.entity.PasswordHistoryEntity;
import com.taipei.iot.user.repository.PasswordHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.lang.Nullable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Tenant-aware password complexity & history validator.
 *
 * <p>
 * Phase 1 — reads the effective {@link PasswordPolicy} from
 * {@link PasswordPolicyResolver} (which already applies the tenant→platform→ hardcoded
 * fallback) and validates against that policy. {@link UserContext} is accepted for
 * forward-compatibility with Phase 2's {@code password.not_contains_username} rule but is
 * unused in Phase 1.
 */
@Service
@RequiredArgsConstructor
public class PasswordValidator {

	private final PasswordHistoryRepository passwordHistoryRepository;

	private final PasswordEncoder passwordEncoder;

	private final PasswordPolicyResolver policyResolver;

	static final String SPECIAL_CHARS = "!@#$%^&*()_+-=[]{}|;':,.<>?/~`";

	/**
	 * Hard maximum password length (always enforced regardless of policy settings).
	 * Prevents BCrypt CPU DoS even if policy.maxLength is misconfigured to 0.
	 */
	static final int HARD_MAX = 1024;

	/**
	 * Optional user context used by Phase 2 rules (e.g.
	 * {@code password.not_contains_username}).
	 */
	public record UserContext(String username, String email) {
	}

	/**
	 * Validate the new password against the effective policy for {@code tenantId}.
	 * @param tenantId tenant whose policy should apply; {@code null} → platform default
	 * @param newPassword candidate password
	 * @param userContext optional username/email context for Phase 2 rules (Phase 1:
	 * ignored)
	 */
	public void validate(@Nullable String tenantId, String newPassword, @Nullable UserContext userContext) {
		PasswordPolicy policy = policyResolver.resolve(tenantId);

		if (newPassword == null || newPassword.length() < policy.getMinLength()) {
			throw new BusinessException(ErrorCode.RESET_PASSWORD_ERROR, "密碼長度至少 " + policy.getMinLength() + " 字元");
		}
		// Hard ceiling — always enforced regardless of policy.maxLength setting.
		if (newPassword.length() > HARD_MAX) {
			throw new BusinessException(ErrorCode.RESET_PASSWORD_ERROR, "密碼長度不可超過 " + HARD_MAX + " 字元");
		}
		// [Phase 2] DoS guard — reject oversized inputs before counting characters.
		if (policy.getMaxLength() > 0 && newPassword.length() > policy.getMaxLength()) {
			throw new BusinessException(ErrorCode.RESET_PASSWORD_ERROR, "密碼長度不可超過 " + policy.getMaxLength() + " 字元");
		}
		if (policy.isRequireUppercase() && newPassword.chars().noneMatch(Character::isUpperCase)) {
			throw new BusinessException(ErrorCode.RESET_PASSWORD_ERROR, "密碼必須包含大寫英文字母");
		}
		if (policy.isRequireLowercase() && newPassword.chars().noneMatch(Character::isLowerCase)) {
			throw new BusinessException(ErrorCode.RESET_PASSWORD_ERROR, "密碼必須包含小寫英文字母");
		}
		if (policy.isRequireDigit() && newPassword.chars().noneMatch(Character::isDigit)) {
			throw new BusinessException(ErrorCode.RESET_PASSWORD_ERROR, "密碼必須包含數字");
		}
		if (policy.isRequireSpecial() && newPassword.chars().noneMatch(ch -> SPECIAL_CHARS.indexOf(ch) >= 0)) {
			throw new BusinessException(ErrorCode.RESET_PASSWORD_ERROR, "密碼必須包含特殊字元");
		}
		// [Phase 2] Character-class minimum counts. Only enforce when the corresponding
		// require_* flag is on AND the configured minimum is >1 — otherwise the existing
		// "must contain at least one" check above is sufficient and we avoid
		// double-reporting.
		if (policy.isRequireUppercase() && policy.getMinUppercase() > 1
				&& countMatching(newPassword, Character::isUpperCase) < policy.getMinUppercase()) {
			throw new BusinessException(ErrorCode.RESET_PASSWORD_ERROR,
					"密碼必須包含至少 " + policy.getMinUppercase() + " 個大寫英文字母");
		}
		if (policy.isRequireLowercase() && policy.getMinLowercase() > 1
				&& countMatching(newPassword, Character::isLowerCase) < policy.getMinLowercase()) {
			throw new BusinessException(ErrorCode.RESET_PASSWORD_ERROR,
					"密碼必須包含至少 " + policy.getMinLowercase() + " 個小寫英文字母");
		}
		if (policy.isRequireDigit() && policy.getMinDigits() > 1
				&& countMatching(newPassword, Character::isDigit) < policy.getMinDigits()) {
			throw new BusinessException(ErrorCode.RESET_PASSWORD_ERROR, "密碼必須包含至少 " + policy.getMinDigits() + " 個數字");
		}
		if (policy.isRequireSpecial() && policy.getMinSpecialChars() > 1
				&& countMatching(newPassword, ch -> SPECIAL_CHARS.indexOf(ch) >= 0) < policy.getMinSpecialChars()) {
			throw new BusinessException(ErrorCode.RESET_PASSWORD_ERROR,
					"密碼必須包含至少 " + policy.getMinSpecialChars() + " 個特殊字元");
		}
		// [Phase 2] Reject passwords that contain the username or email local-part
		// (case-insensitive). Skipped when userContext is null — callers that don't have
		// the user identity (e.g. some pre-create flows) opt out by passing null.
		if (policy.isNotContainsUsername() && userContext != null) {
			String lower = newPassword.toLowerCase();
			String username = userContext.username();
			if (username != null && !username.isBlank() && lower.contains(username.toLowerCase())) {
				throw new BusinessException(ErrorCode.RESET_PASSWORD_ERROR, "密碼不可包含使用者名稱");
			}
			String email = userContext.email();
			if (email != null && !email.isBlank()) {
				int at = email.indexOf('@');
				String local = at > 0 ? email.substring(0, at) : email;
				if (!local.isBlank() && lower.contains(local.toLowerCase())) {
					throw new BusinessException(ErrorCode.RESET_PASSWORD_ERROR, "密碼不可包含電子郵件");
				}
			}
		}
	}

	private static int countMatching(String s, java.util.function.IntPredicate p) {
		return (int) s.chars().filter(p).count();
	}

	/**
	 * Reject {@code rawPassword} if it matches any of the user's last N hashed passwords,
	 * where N is the tenant's effective {@code password.history_count}. A history count
	 * of 0 disables this check.
	 */
	public void checkNotRecentlyUsed(@Nullable String tenantId, String userId, String rawPassword) {
		PasswordPolicy policy = policyResolver.resolve(tenantId);
		int historyCount = policy.getHistoryCount();
		if (historyCount <= 0) {
			return;
		}
		List<PasswordHistoryEntity> recent = passwordHistoryRepository.findByUserIdOrderByCreateTimeDesc(userId,
				PageRequest.of(0, historyCount));
		for (PasswordHistoryEntity history : recent) {
			if (passwordEncoder.matches(rawPassword, history.getPasswordHash())) {
				throw new BusinessException(ErrorCode.PASSWORD_RECENTLY_USED);
			}
		}
	}

}
