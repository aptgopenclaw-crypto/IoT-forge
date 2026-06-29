package com.taipei.iot.tenant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateTenantRequest {

	@NotBlank
	@Size(max = 200)
	private String tenantName;

	private String deploymentMode;

}
