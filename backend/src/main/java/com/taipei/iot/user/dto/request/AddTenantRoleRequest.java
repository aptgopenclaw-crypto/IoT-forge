package com.taipei.iot.user.dto.request;

import jakarta.validation.constraints.NotBlank;
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
public class AddTenantRoleRequest {

	@NotBlank(message = "tenantId is required")
	private String tenantId;

	@NotBlank
	private String roleId;

	private Long deptId;

}
