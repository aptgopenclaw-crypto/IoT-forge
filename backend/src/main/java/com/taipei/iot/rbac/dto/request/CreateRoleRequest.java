package com.taipei.iot.rbac.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
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
public class CreateRoleRequest {

	@NotBlank
	@Size(max = 50)
	private String code;

	@NotBlank
	@Size(max = 100)
	private String name;

	@Size(max = 500)
	private String description;

	@Pattern(regexp = "ALL|THIS_LEVEL|THIS_LEVEL_AND_BELOW",
			message = "dataScope 必須為 ALL、THIS_LEVEL 或 THIS_LEVEL_AND_BELOW")
	private String dataScope;

}
