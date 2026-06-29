package com.taipei.iot.user.dto.response;

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
public class UserTenantMappingDto {

	private Long mappingId;

	private String tenantId;

	private String tenantName;

	private String roleId;

	private String roleCode;

	private String roleName;

	private Long deptId;

	private String deptName;

	private boolean enabled;

}
