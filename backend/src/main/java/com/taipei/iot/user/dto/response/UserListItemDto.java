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
public class UserListItemDto {

	private String userId;

	private String email;

	private String displayName;

	private String phone;

	private boolean enabled;

	private boolean locked;

	private Boolean deleted;

	private String roleId;

	private String roleCode;

	private String roleName;

	private Long deptId;

	private String deptName;

	private Long mappingId;

	private boolean mappingEnabled;

}
