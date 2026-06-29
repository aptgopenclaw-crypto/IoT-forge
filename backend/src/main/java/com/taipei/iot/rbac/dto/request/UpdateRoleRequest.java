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
public class UpdateRoleRequest {

	@NotBlank
	@Size(max = 100)
	private String name;

	@Size(max = 500)
	private String description;

	private boolean enabled;

	@NotBlank(message = "dataScope 不能為空")
	@Pattern(regexp = "ALL|THIS_LEVEL|THIS_LEVEL_AND_BELOW",
			message = "dataScope 必須為 ALL、THIS_LEVEL 或 THIS_LEVEL_AND_BELOW")
	private String dataScope;

}
