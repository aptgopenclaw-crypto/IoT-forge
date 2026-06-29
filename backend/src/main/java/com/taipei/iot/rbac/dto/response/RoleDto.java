package com.taipei.iot.rbac.dto.response;

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
public class RoleDto {

	private String roleId;

	private String code;

	private String name;

	private String description;

	private boolean builtIn;

	private boolean enabled;

	private String dataScope;

}
