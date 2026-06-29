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
public class ChangePasswordRequest {

	@NotBlank(message = "oldPassword is required")
	@Size(max = 128, message = "oldPassword must not exceed 128 characters")
	private String oldPassword;

	@NotBlank(message = "newPassword is required")
	@Size(max = 128, message = "newPassword must not exceed 128 characters")
	private String newPassword;

}
