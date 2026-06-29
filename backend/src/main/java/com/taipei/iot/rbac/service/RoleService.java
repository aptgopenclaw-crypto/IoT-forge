package com.taipei.iot.rbac.service;

import com.taipei.iot.auth.entity.RoleEntity;
import com.taipei.iot.auth.repository.RoleRepository;
import com.taipei.iot.common.dto.UserInfo;
import com.taipei.iot.common.enums.ErrorCode;
import com.taipei.iot.common.exception.BusinessException;
import com.taipei.iot.common.util.SecurityContextUtils;
import com.taipei.iot.dept.enums.DataScopeEnum;
import com.taipei.iot.rbac.dto.request.AssignRolePermissionsRequest;
import com.taipei.iot.rbac.dto.request.CreateRoleRequest;
import com.taipei.iot.rbac.dto.request.UpdateRoleRequest;
import com.taipei.iot.rbac.dto.response.PermissionDto;
import com.taipei.iot.rbac.dto.response.RoleDto;
import com.taipei.iot.rbac.dto.response.RolePermissionListDto;
import com.taipei.iot.rbac.entity.RolePermissionEntity;
import com.taipei.iot.rbac.repository.PermissionRepository;
import com.taipei.iot.rbac.repository.RolePermissionRepository;
import com.taipei.iot.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RoleService {

	private static final String SUPER_ADMIN_ROLE_ID = "ROLE_SUPER_ADMIN";

	private final RoleRepository roleRepository;

	private final RolePermissionRepository rolePermissionRepository;

	private final PermissionRepository permissionRepository;

	public List<RoleDto> listRoles() {
		return roleRepository.findAllByOrderByBuiltInDescCreateTimeDesc()
			.stream()
			.filter(role -> !SUPER_ADMIN_ROLE_ID.equals(role.getRoleId()))
			.map(this::toRoleDto)
			.collect(Collectors.toList());
	}

	/**
	 * 依據當前使用者的 DataScope 回傳可指派的角色清單。 ALL scope → 可指派所有啟用角色。 THIS_LEVEL /
	 * THIS_LEVEL_AND_BELOW → 只能指派 scope 非 ALL 的角色。
	 */
	public List<RoleDto> listAssignableRoles() {
		UserInfo user = SecurityContextUtils.getUserInfo();
		DataScopeEnum callerScope = (user != null && user.getDataScope() != null)
				? DataScopeEnum.fromString(user.getDataScope()) : DataScopeEnum.ALL;

		return roleRepository.findAllByOrderByBuiltInDescCreateTimeDesc()
			.stream()
			.filter(RoleEntity::getEnabled)
			.filter(role -> !SUPER_ADMIN_ROLE_ID.equals(role.getRoleId()))
			.filter(role -> {
				if (callerScope == DataScopeEnum.ALL) {
					return true;
				}
				// 非 ALL scope 的操作者只能指派 scope 不為 ALL 的角色
				DataScopeEnum roleScope = DataScopeEnum.fromString(role.getDataScope());
				return roleScope != DataScopeEnum.ALL;
			})
			.map(this::toRoleDto)
			.collect(Collectors.toList());
	}

	/**
	 * 檢查目標 roleId 是否為當前使用者可指派的角色。
	 */
	public boolean isRoleAssignable(String roleId) {
		if (SUPER_ADMIN_ROLE_ID.equals(roleId))
			return false;
		RoleEntity role = roleRepository.findById(roleId).orElse(null);
		if (role == null || !role.getEnabled())
			return false;
		UserInfo user = SecurityContextUtils.getUserInfo();
		DataScopeEnum callerScope = (user != null && user.getDataScope() != null)
				? DataScopeEnum.fromString(user.getDataScope()) : DataScopeEnum.ALL;
		if (callerScope == DataScopeEnum.ALL)
			return true;
		return DataScopeEnum.fromString(role.getDataScope()) != DataScopeEnum.ALL;
	}

	@Transactional
	public RoleDto createRole(CreateRoleRequest request) {
		if (roleRepository.existsByCode(request.getCode())) {
			throw new BusinessException(ErrorCode.ROLE_CODE_DUPLICATE);
		}

		RoleEntity entity = RoleEntity.builder()
			.roleId("ROLE_" + UUID.randomUUID().toString().replace("-", "").toUpperCase())
			.code(request.getCode())
			.name(request.getName())
			.description(request.getDescription())
			.builtIn(false)
			.enabled(true)
			.dataScope(request.getDataScope() != null ? request.getDataScope() : "ALL")
			.build();

		RoleEntity saved = roleRepository.save(entity);
		return toRoleDto(saved);
	}

	@Transactional
	public RoleDto updateRole(String roleId, UpdateRoleRequest request) {
		RoleEntity entity = roleRepository.findById(roleId)
			.orElseThrow(() -> new BusinessException(ErrorCode.ROLE_NOT_FOUND));

		if (Boolean.TRUE.equals(entity.getBuiltIn())) {
			throw new BusinessException(ErrorCode.ROLE_BUILTIN_READONLY);
		}

		if (!isCurrentUserSuperAdmin() && !isRoleAssignable(roleId)) {
			throw new BusinessException(ErrorCode.PERMISSION_DENIED);
		}

		entity.setName(request.getName());
		entity.setDescription(request.getDescription());
		entity.setEnabled(request.isEnabled());
		entity.setDataScope(request.getDataScope());

		RoleEntity saved = roleRepository.save(entity);
		return toRoleDto(saved);
	}

	@Transactional
	public void toggleEnabled(String roleId, boolean enabled) {
		RoleEntity entity = roleRepository.findById(roleId)
			.orElseThrow(() -> new BusinessException(ErrorCode.ROLE_NOT_FOUND));
		if (Boolean.TRUE.equals(entity.getBuiltIn())) {
			throw new BusinessException(ErrorCode.ROLE_BUILTIN_READONLY);
		}
		if (!isCurrentUserSuperAdmin() && !isRoleAssignable(roleId)) {
			throw new BusinessException(ErrorCode.PERMISSION_DENIED);
		}
		entity.setEnabled(enabled);
		roleRepository.save(entity);
	}

	@Transactional
	public RolePermissionListDto assignPermissions(String roleId, AssignRolePermissionsRequest request) {
		RoleEntity role = roleRepository.findById(roleId)
			.orElseThrow(() -> new BusinessException(ErrorCode.ROLE_NOT_FOUND));

		if (SUPER_ADMIN_ROLE_ID.equals(roleId)) {
			throw new BusinessException(ErrorCode.ROLE_BUILTIN_READONLY);
		}

		// DataScope 二次檢查：非 SUPER_ADMIN 不可操作超出自身 scope 的角色
		if (!isCurrentUserSuperAdmin() && !isRoleAssignable(roleId)) {
			throw new BusinessException(ErrorCode.PERMISSION_DENIED);
		}

		// 非 SUPER_ADMIN 呼叫者不可指派超越自身擁有的權限
		if (!isCurrentUserSuperAdmin()) {
			Set<String> callerPermIds = resolveCallerPermissionIds();
			List<String> unauthorized = request.getPermissionIds()
				.stream()
				.filter(id -> !callerPermIds.contains(id))
				.toList();
			if (!unauthorized.isEmpty()) {
				throw new BusinessException(ErrorCode.PERMISSION_DENIED);
			}
		}

		// 驗證所有 permissionId 皆存在
		Set<String> validPermIds = permissionRepository.findAllById(request.getPermissionIds())
			.stream()
			.map(com.taipei.iot.rbac.entity.PermissionEntity::getPermissionId)
			.collect(Collectors.toSet());
		List<String> invalidIds = request.getPermissionIds().stream().filter(id -> !validPermIds.contains(id)).toList();
		if (!invalidIds.isEmpty()) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR);
		}

		// Remove all existing permissions for this role (global scope only)
		rolePermissionRepository.deleteByRoleId(roleId);

		// Insert new permissions
		List<RolePermissionEntity> newRps = request.getPermissionIds()
			.stream()
			.map(permId -> RolePermissionEntity.builder().roleId(roleId).permissionId(permId).tenantId(null).build())
			.collect(Collectors.toList());
		rolePermissionRepository.saveAll(newRps);

		// Return updated permission list
		List<PermissionDto> permissions = permissionRepository.findAllById(request.getPermissionIds())
			.stream()
			.map(this::toPermissionDto)
			.collect(Collectors.toList());

		return RolePermissionListDto.builder()
			.roleId(role.getRoleId())
			.roleCode(role.getCode())
			.permissions(permissions)
			.build();
	}

	public RolePermissionListDto getRolePermissions(String roleId) {
		return getRolePermissions(roleId, null);
	}

	public RolePermissionListDto getRolePermissions(String roleId, String tenantId) {
		RoleEntity role = roleRepository.findById(roleId)
			.orElseThrow(() -> new BusinessException(ErrorCode.ROLE_NOT_FOUND));

		List<RolePermissionEntity> rps = (tenantId != null)
				? rolePermissionRepository.findByRoleIdAndTenantScope(roleId, tenantId)
				: rolePermissionRepository.findByRoleId(roleId);
		List<String> permIds = rps.stream().map(RolePermissionEntity::getPermissionId).collect(Collectors.toList());

		List<PermissionDto> permissions = permissionRepository.findAllById(permIds)
			.stream()
			.map(this::toPermissionDto)
			.collect(Collectors.toList());

		return RolePermissionListDto.builder()
			.roleId(role.getRoleId())
			.roleCode(role.getCode())
			.permissions(permissions)
			.build();
	}

	private RoleDto toRoleDto(RoleEntity entity) {
		return RoleDto.builder()
			.roleId(entity.getRoleId())
			.code(entity.getCode())
			.name(entity.getName())
			.description(entity.getDescription())
			.builtIn(entity.getBuiltIn())
			.enabled(entity.getEnabled())
			.dataScope(entity.getDataScope())
			.build();
	}

	private PermissionDto toPermissionDto(com.taipei.iot.rbac.entity.PermissionEntity entity) {
		return PermissionDto.builder()
			.permissionId(entity.getPermissionId())
			.code(entity.getCode())
			.name(entity.getName())
			.groupName(entity.getGroupName())
			.build();
	}

	private boolean isCurrentUserSuperAdmin() {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth == null)
			return false;
		return auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).anyMatch("ROLE_SUPER_ADMIN"::equals);
	}

	private Set<String> resolveCallerPermissionIds() {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth == null)
			return Set.of();
		List<String> callerRoleIds = auth.getAuthorities()
			.stream()
			.map(GrantedAuthority::getAuthority)
			.collect(Collectors.toList());
		String tenantId = TenantContext.getCurrentTenantId();
		return rolePermissionRepository.findByRoleIdInAndTenantScope(callerRoleIds, tenantId)
			.stream()
			.map(RolePermissionEntity::getPermissionId)
			.collect(Collectors.toSet());
	}

}
