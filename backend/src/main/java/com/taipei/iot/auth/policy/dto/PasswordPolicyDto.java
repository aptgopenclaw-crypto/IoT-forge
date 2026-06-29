package com.taipei.iot.auth.policy.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * Effective (merged tenant ∪ platform-default) password policy returned to API consumers.
 * Phase 1 fields only.
 */
@Value
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PasswordPolicyDto {

	int minLength;

	boolean requireUppercase;

	boolean requireLowercase;

	boolean requireDigit;

	boolean requireSpecial;

	int historyCount;

	int maxLength;

	int minSpecialChars;

	int minDigits;

	int minUppercase;

	int minLowercase;

	boolean notContainsUsername;

	int expireDays;

	boolean forceChangeOnFirstLogin;

	boolean forceChangeOnAdminReset;

	List<String> describe;

}
