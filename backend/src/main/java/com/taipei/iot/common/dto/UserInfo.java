package com.taipei.iot.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class UserInfo {

	private String userId;

	private String username;

	private String tenantId;

	private Long deptId;

	private String dataScope;

}
