package com.taipei.iot.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * [Phase 3] Body for {@code POST /v1/noauth/auth/force-change-password}. The bearer
 * header must carry a {@code purpose=password_change} temporary token issued by the login
 * flow when expiry / forced-change was detected.
 */
@Data
public class ForceChangePasswordRequest {

	@NotBlank
	@Size(max = 128, message = "newPassword must not exceed 128 characters")
	private String newPassword;

}
