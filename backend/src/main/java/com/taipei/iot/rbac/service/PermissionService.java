package com.taipei.iot.rbac.service;

import com.taipei.iot.rbac.dto.response.PermissionDto;
import com.taipei.iot.rbac.entity.PermissionEntity;
import com.taipei.iot.rbac.entity.RolePermissionEntity;
import com.taipei.iot.rbac.repository.PermissionRepository;
import com.taipei.iot.rbac.repository.RolePermissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PermissionService {

	private final PermissionRepository permissionRepository;

	private final RolePermissionRepository rolePermissionRepository;

	public List<PermissionDto> listPermissions() {
		return permissionRepository.findAllByOrderByGroupNameAscSortOrderAsc()
			.stream()
			.map(this::toDto)
			.collect(Collectors.toList());
	}

	public List<PermissionDto> getPermissionsByRole(String roleId, String tenantId) {
		List<RolePermissionEntity> rps = rolePermissionRepository.findByRoleIdAndTenantScope(roleId, tenantId);
		List<String> permIds = rps.stream().map(RolePermissionEntity::getPermissionId).collect(Collectors.toList());

		return permissionRepository.findAllById(permIds).stream().map(this::toDto).collect(Collectors.toList());
	}

	private PermissionDto toDto(PermissionEntity entity) {
		return PermissionDto.builder()
			.permissionId(entity.getPermissionId())
			.code(entity.getCode())
			.name(entity.getName())
			.groupName(entity.getGroupName())
			.build();
	}

}
