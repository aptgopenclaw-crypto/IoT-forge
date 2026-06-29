package com.taipei.iot.auth.dto.response;

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
public class TenantOption {

	private String tenantId;

	private String tenantCode;

	private String tenantName;

	private String roleName;

	private String deptName;

}
