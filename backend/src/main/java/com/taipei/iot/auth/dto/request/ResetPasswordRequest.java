package com.taipei.iot.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResetPasswordRequest {

	@NotBlank(message = "token is required")
	@Size(max = 512, message = "token must not exceed 512 characters")
	private String token;

	@NotBlank(message = "newPassword is required")
	@Size(max = 128, message = "newPassword must not exceed 128 characters")
	private String newPassword;

}
