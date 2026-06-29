package com.taipei.iot.rbac.dto.request;

import jakarta.validation.constraints.NotEmpty;
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
public class AssignRolePermissionsRequest {

	@NotEmpty(message = "permissionIds 不能為空")
	private List<String> permissionIds;

}
