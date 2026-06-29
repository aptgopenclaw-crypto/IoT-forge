package com.taipei.iot.auth.policy;

import lombok.Builder;
import lombok.Value;

import java.util.ArrayList;
import java.util.List;

/**
 * Resolved password policy for a given tenant.
 *
 * <p>
 * Immutable value object combining tenant overrides on top of platform defaults (see
 * {@link PasswordPolicyResolver}). Phase 1 only covers the basic-complexity keys; Phase
 * 2/3 fields are left in the builder shape for forward-compatibility but currently fall
 * back to safe defaults.
 */
@Value
@Builder
public class PasswordPolicy {

	// ── Phase 1 ──────────────────────────────────────────────────────────
	int minLength;

	boolean requireUppercase;

	boolean requireLowercase;

	boolean requireDigit;

	boolean requireSpecial;

	int historyCount;

	// ── Phase 2 ──────────────────────────────────────────────────────────
	/** Maximum password length; defends against DoS via huge inputs. */
	int maxLength;

	int minSpecialChars;

	int minDigits;

	int minUppercase;

	int minLowercase;

	/** Reject passwords containing username or email local-part (case-insensitive). */
	boolean notContainsUsername;

	// ── Phase 3 ──────────────────────────────────────────────────────────
	/** Password validity in days; 0 = never expires. */
	int expireDays;

	boolean forceChangeOnFirstLogin;

	boolean forceChangeOnAdminReset;

	/**
	 * Human-readable rule list, used by the public describe endpoint and by password
	 * input components to render real-time hints.
	 */
	public List<String> describe() {
		List<String> out = new ArrayList<>();
		out.add("密碼長度至少 " + minLength + " 字元");
		if (maxLength > 0) {
			out.add("密碼長度最多 " + maxLength + " 字元");
		}
		List<String> mustHave = new ArrayList<>();
		if (requireUppercase) {
			mustHave.add(minUppercase > 1 ? ("至少 " + minUppercase + " 個大寫英文字母") : "大寫英文字母");
		}
		if (requireLowercase) {
			mustHave.add(minLowercase > 1 ? ("至少 " + minLowercase + " 個小寫英文字母") : "小寫英文字母");
		}
		if (requireDigit) {
			mustHave.add(minDigits > 1 ? ("至少 " + minDigits + " 個數字") : "數字");
		}
		if (requireSpecial) {
			mustHave.add(minSpecialChars > 1 ? ("至少 " + minSpecialChars + " 個特殊字元") : "特殊字元");
		}
		if (!mustHave.isEmpty()) {
			out.add("須包含：" + String.join("、", mustHave));
		}
		if (notContainsUsername) {
			out.add("不可包含使用者名稱或電子郵件");
		}
		if (historyCount > 0) {
			out.add("不可與前 " + historyCount + " 次密碼相同");
		}
		if (expireDays > 0) {
			out.add("每 " + expireDays + " 天需更換密碼");
		}
		return out;
	}

}
