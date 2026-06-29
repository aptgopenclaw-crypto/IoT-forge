package com.taipei.iot.auth.dto.request;

import jakarta.validation.constraints.Email;
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
public class ForgotPasswordRequest {

	@NotBlank(message = "email is required")
	@Email(message = "email must be a valid address")
	@Size(max = 254, message = "email must not exceed 254 characters")
	private String email;

}
