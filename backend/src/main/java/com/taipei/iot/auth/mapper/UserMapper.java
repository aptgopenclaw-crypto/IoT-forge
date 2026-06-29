package com.taipei.iot.auth.mapper;

import com.taipei.iot.auth.dto.response.UserInfoDto;
import com.taipei.iot.auth.entity.UserEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface UserMapper {

	@Mapping(source = "user.userId", target = "userId")
	@Mapping(source = "user.email", target = "email")
	@Mapping(source = "user.displayName", target = "displayName")
	@Mapping(source = "user.isSuperAdmin", target = "isSuperAdmin")
	@Mapping(source = "tenantId", target = "tenantId")
	@Mapping(source = "tenantName", target = "tenantName")
	@Mapping(source = "roles", target = "roles")
	@Mapping(source = "deptId", target = "deptId")
	@Mapping(source = "deptName", target = "deptName")
	@Mapping(source = "permissions", target = "permissions")
	@Mapping(target = "availableTenants", ignore = true)
	UserInfoDto toUserInfoDto(UserEntity user, String tenantId, String tenantName, List<String> roles, String deptId,
			String deptName, List<String> permissions);

}
