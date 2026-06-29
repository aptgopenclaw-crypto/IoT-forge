package com.taipei.iot.auth.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserInfoDto {

	private String userId;

	private String email;

	private String displayName;

	private String tenantId;

	private String tenantName;

	private List<String> roles;

	private String deptId;

	private String deptName;

	private List<String> permissions;

	@JsonProperty("isSuperAdmin")
	private boolean isSuperAdmin;

	private List<TenantOption> availableTenants;

}
