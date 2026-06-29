package com.taipei.iot.auth.policy.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Request to upsert a single password-policy key for a given scope (platform default or
 * tenant override).
 *
 * <p>
 * Value is transmitted as a raw string and validated against the key's
 * {@link com.taipei.iot.auth.policy.PasswordPolicyKey} type by the service.
 */
@Data
public class UpdatePasswordPolicyRequest {

	@NotBlank
	@Size(max = 100)
	private String key;

	@NotBlank
	@Size(max = 64)
	private String value;

}
