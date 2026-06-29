package com.taipei.iot.rbac.dto.response;

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
public class RolePermissionListDto {

	private String roleId;

	private String roleCode;

	private List<PermissionDto> permissions;

}
