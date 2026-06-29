package com.taipei.iot.user.service;

import com.taipei.iot.auth.entity.RoleEntity;
import com.taipei.iot.auth.entity.UserEntity;
import com.taipei.iot.auth.entity.UserTenantMappingEntity;
import com.taipei.iot.auth.repository.RoleRepository;
import com.taipei.iot.auth.repository.UserRepository;
import com.taipei.iot.auth.repository.UserTenantMappingRepository;
import com.taipei.iot.common.enums.ErrorCode;
import com.taipei.iot.common.exception.BusinessException;
import com.taipei.iot.dept.repository.DeptInfoRepository;
import com.taipei.iot.dept.service.DataScopeHelper;
import com.taipei.iot.rbac.service.RoleService;
import com.taipei.iot.tenant.TenantContext;
import com.taipei.iot.tenant.TenantRepository;
import com.taipei.iot.user.dto.request.AddTenantRoleRequest;
import com.taipei.iot.user.dto.request.CreateUserRequest;
import com.taipei.iot.user.dto.request.UpdateUserRequest;
import com.taipei.iot.common.dto.PageResponse;
import com.taipei.iot.common.util.PageConversionHelper;
import com.taipei.iot.user.dto.response.UserListItemDto;
import com.taipei.iot.user.dto.response.UserTenantMappingDto;
import com.taipei.iot.user.entity.PasswordHistoryEntity;
import com.taipei.iot.user.repository.PasswordHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserAdminService {

	private final UserRepository userRepository;

	private final UserTenantMappingRepository userTenantMappingRepository;

	private final RoleRepository roleRepository;

	private final PasswordEncoder passwordEncoder;

	private final PasswordValidator passwordValidator;

	private final PasswordHistoryRepository passwordHistoryRepository;

	private final UserAuditService userAuditService;

	private final DataScopeHelper dataScopeHelper;

	private final DeptInfoRepository deptInfoRepository;

	private final RoleService roleService;

	private final TenantRepository tenantRepository;

	private final com.taipei.iot.auth.policy.PasswordPolicyResolver passwordPolicyResolver;

	@Transactional(readOnly = true)
	public UserListItemDto getUser(String userId) {
		String tenantId = TenantContext.getCurrentTenantId();
		UserTenantMappingEntity mapping;

		if (TenantContext.isSystemContext()) {
			mapping = userTenantMappingRepository.findByUserId(userId)
				.stream()
				.findFirst()
				.orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
		}
		else {
			mapping = userTenantMappingRepository.findByUserIdAndTenantId(userId, tenantId)
				.orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

			if (!dataScopeHelper.isDeptInScope(mapping.getDeptId())) {
				throw new BusinessException(ErrorCode.PERMISSION_DENIED, "無權檢視該使用者");
			}
		}

		return toUserListItemDto(mapping);
	}

	@Transactional(readOnly = true)
	public PageResponse<UserListItemDto> listUsers(int page, int size, String keyword, Long deptId) {
		String tenantId = TenantContext.getCurrentTenantId();
		Pageable pageable = PageRequest.of(page, size);
		String kw = (keyword != null && !keyword.isBlank()) ? keyword.trim() : null;

		Page<UserTenantMappingEntity> mappingPage;
		if (TenantContext.isSystemContext()) {
			mappingPage = userTenantMappingRepository.findAllActive(kw, pageable);
		}
		else {
			List<Long> visibleDeptIds = dataScopeHelper.getVisibleDeptIds();
			// 若呼叫方明確指定 deptId，以指定值為優先（需在可見範圍內）
			if (deptId != null) {
				mappingPage = userTenantMappingRepository.findActiveByTenantIdAndDeptId(tenantId, deptId, kw, pageable);
			}
			else if (visibleDeptIds.isEmpty()) {
				// ALL scope → 不限制部門
				mappingPage = userTenantMappingRepository.findActiveByTenantId(tenantId, kw, pageable);
			}
			else if (visibleDeptIds.size() == 1) {
				mappingPage = userTenantMappingRepository.findActiveByTenantIdAndDeptId(tenantId, visibleDeptIds.get(0),
						kw, pageable);
			}
			else {
				mappingPage = userTenantMappingRepository.findActiveByTenantIdAndDeptIdIn(tenantId, visibleDeptIds, kw,
						pageable);
			}
		}

		Map<Long, String> deptNameMap = resolveDeptNameMap(mappingPage.getContent());
		List<UserListItemDto> items = mappingPage.getContent()
			.stream()
			.map(m -> toUserListItemDto(m, deptNameMap))
			.collect(Collectors.toList());

		return PageConversionHelper.from(items, mappingPage);
	}

	/**
	 * N-2: 針對列表查詢先 batch fetch 部門名稱，避免在 toUserListItemDto 迴圈中逐筆 findById 造成 N+1。不動 entity
	 * 結構，以保持 auth.entity 與 dept 模組的低耦合。
	 */
	private Map<Long, String> resolveDeptNameMap(List<UserTenantMappingEntity> mappings) {
		Set<Long> deptIds = mappings.stream()
			.map(UserTenantMappingEntity::getDeptId)
			.filter(java.util.Objects::nonNull)
			.collect(Collectors.toSet());
		if (deptIds.isEmpty()) {
			return java.util.Collections.emptyMap();
		}
		return deptInfoRepository.findAllById(deptIds)
			.stream()
			.collect(Collectors.toMap(com.taipei.iot.dept.entity.DeptInfoEntity::getDeptId,
					com.taipei.iot.dept.entity.DeptInfoEntity::getDeptName));
	}

	@Transactional
	public UserListItemDto createUser(String adminUserId, CreateUserRequest req) {
		// DataScope 檢核：操作者只能在自己的部門範圍內建立帳號
		if (!dataScopeHelper.isDeptInScope(req.getDeptId())) {
			throw new BusinessException(ErrorCode.PERMISSION_DENIED, "無權在該部門建立帳號");
		}

		// 角色指派檢核：操作者只能指派自己權限範圍內的角色
		if (!roleService.isRoleAssignable(req.getRoleId())) {
			throw new BusinessException(ErrorCode.PERMISSION_DENIED, "無權指派該角色");
		}

		if (userRepository.existsByEmail(req.getEmail())) {
			throw new BusinessException(ErrorCode.USER_ALREADY_EXISTS);
		}

		com.taipei.iot.auth.provider.AuthType authType = req.getAuthType() != null ? req.getAuthType()
				: com.taipei.iot.auth.provider.AuthType.LOCAL;

		String userId = UUID.randomUUID().toString();
		String passwordHash;
		boolean forceChange = false;

		if (authType == com.taipei.iot.auth.provider.AuthType.LOCAL) {
			if (req.getInitialPassword() == null || req.getInitialPassword().isBlank()) {
				throw new BusinessException(ErrorCode.VALIDATION_ERROR, "LOCAL 帳號必須提供初始密碼");
			}
			passwordValidator.validate(com.taipei.iot.tenant.TenantContext.getCurrentTenantId(),
					req.getInitialPassword(), new PasswordValidator.UserContext(req.getEmail(), req.getEmail()));
			passwordHash = passwordEncoder.encode(req.getInitialPassword());
			// [Phase 3] Honour password.force_change_on_first_login policy
			com.taipei.iot.auth.policy.PasswordPolicy policy = passwordPolicyResolver
				.resolve(com.taipei.iot.tenant.TenantContext.getCurrentTenantId());
			forceChange = policy.isForceChangeOnFirstLogin();
		}
		else {
			// LDAP / OIDC / SAML: 不使用平台密碼
			passwordHash = "AD_AUTH";
		}

		UserEntity user = UserEntity.builder()
			.userId(userId)
			.email(req.getEmail())
			.displayName(req.getDisplayName())
			.phone(req.getPhone())
			.passwordHash(passwordHash)
			.authType(authType)
			.enabled(true)
			.locked(false)
			.loginFailCount(0)
			.isSuperAdmin(false)
			.passwordChangedAt(java.time.LocalDateTime.now())
			.forceChangePassword(forceChange)
			.build();
		try {
			userRepository.save(user);
		}
		catch (DataIntegrityViolationException e) {
			throw new BusinessException(ErrorCode.USER_ALREADY_EXISTS);
		}

		String tenantId = req.getTenantId() != null ? req.getTenantId() : TenantContext.getCurrentTenantId();

		UserTenantMappingEntity mapping = UserTenantMappingEntity.builder()
			.userId(userId)
			.tenantId(tenantId)
			.roleId(req.getRoleId())
			.deptId(req.getDeptId())
			.enabled(true)
			.build();
		UserTenantMappingEntity savedMapping = userTenantMappingRepository.save(mapping);

		if (authType == com.taipei.iot.auth.provider.AuthType.LOCAL) {
			passwordHistoryRepository
				.save(PasswordHistoryEntity.builder().userId(userId).passwordHash(passwordHash).build());
		}

		userAuditService.logAction("CREATE", adminUserId, userId, "建立帳號 + 指派場域");

		return toUserListItemDto(savedMapping);
	}

	@Transactional
	public UserListItemDto updateUser(String adminUserId, String userId, UpdateUserRequest req) {
		UserEntity user = userRepository.findById(userId)
			.orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

		// DataScope 檢核：驗證目標使用者在操作者管轄範圍內
		String tenantId = TenantContext.getCurrentTenantId();
		UserTenantMappingEntity mapping = userTenantMappingRepository.findByUserIdAndTenantId(userId, tenantId)
			.orElse(null);

		if (mapping != null && !dataScopeHelper.isDeptInScope(mapping.getDeptId())) {
			throw new BusinessException(ErrorCode.PERMISSION_DENIED, "無權修改該使用者");
		}

		// Capture before-state for diff
		Map<String, String> before = new LinkedHashMap<>();
		Map<String, String> after = new LinkedHashMap<>();

		if (req.getDisplayName() != null && !req.getDisplayName().equals(user.getDisplayName())) {
			before.put("displayName", user.getDisplayName());
			after.put("displayName", req.getDisplayName());
			user.setDisplayName(req.getDisplayName());
		}
		else if (req.getDisplayName() != null) {
			user.setDisplayName(req.getDisplayName());
		}
		if (req.getPhone() != null && !Objects.equals(req.getPhone(), user.getPhone())) {
			before.put("phone", user.getPhone() != null ? user.getPhone() : "");
			after.put("phone", req.getPhone());
			user.setPhone(req.getPhone());
		}
		else if (req.getPhone() != null) {
			user.setPhone(req.getPhone());
		}

		userRepository.save(user);

		if (mapping != null) {
			boolean mappingChanged = false;
			if (req.getRoleId() != null && !req.getRoleId().equals(mapping.getRoleId())) {
				// 角色指派檢核
				if (!roleService.isRoleAssignable(req.getRoleId())) {
					throw new BusinessException(ErrorCode.PERMISSION_DENIED, "無權指派該角色");
				}
				roleRepository.findById(req.getRoleId())
					.orElseThrow(() -> new BusinessException(ErrorCode.ROLE_NOT_FOUND));
				before.put("roleId", mapping.getRoleId());
				after.put("roleId", req.getRoleId());
				mapping.setRoleId(req.getRoleId());
				mappingChanged = true;
			}
			if (req.getDeptId() != null && !req.getDeptId().equals(mapping.getDeptId())) {
				// DataScope 檢核：新部門也要在範圍內
				if (!dataScopeHelper.isDeptInScope(req.getDeptId())) {
					throw new BusinessException(ErrorCode.PERMISSION_DENIED, "無權指派到該部門");
				}
				before.put("deptId", mapping.getDeptId() != null ? String.valueOf(mapping.getDeptId()) : "");
				after.put("deptId", String.valueOf(req.getDeptId()));
				mapping.setDeptId(req.getDeptId());
				mappingChanged = true;
			}
			if (mappingChanged) {
				userTenantMappingRepository.save(mapping);
			}
			userAuditService.logChange("UPDATE", adminUserId, userId, "管理端更新帳號資料", before, after);
			return toUserListItemDto(mapping);
		}

		userAuditService.logChange("UPDATE", adminUserId, userId, "管理端更新帳號資料", before, after);
		return UserListItemDto.builder()
			.userId(user.getUserId())
			.email(user.getEmail())
			.displayName(user.getDisplayName())
			.phone(user.getPhone())
			.enabled(user.getEnabled())
			.locked(user.getLocked())
			.build();
	}

	@Transactional
	public void disableUser(String adminUserId, String targetUserId) {
		if (adminUserId.equals(targetUserId)) {
			throw new BusinessException(ErrorCode.PERMISSION_DENIED, "不可停用自己的帳號");
		}

		UserEntity user = userRepository.findById(targetUserId)
			.orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

		// DataScope 檢核
		String tenantId = TenantContext.getCurrentTenantId();
		userTenantMappingRepository.findByUserIdAndTenantId(targetUserId, tenantId).ifPresent(mapping -> {
			if (!dataScopeHelper.isDeptInScope(mapping.getDeptId())) {
				throw new BusinessException(ErrorCode.PERMISSION_DENIED, "無權停用該使用者");
			}
		});

		user.setEnabled(false);
		userRepository.save(user);

		userAuditService.logAction("DISABLE", adminUserId, targetUserId, "停用帳號");
	}

	@Transactional
	public void softDeleteUser(String adminUserId, String targetUserId) {
		if (adminUserId.equals(targetUserId)) {
			throw new BusinessException(ErrorCode.PERMISSION_DENIED, "不可刪除自己的帳號");
		}

		UserEntity user = userRepository.findById(targetUserId)
			.orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

		// DataScope 檢核
		String tenantId = TenantContext.getCurrentTenantId();
		userTenantMappingRepository.findByUserIdAndTenantId(targetUserId, tenantId).ifPresent(mapping -> {
			if (!dataScopeHelper.isDeptInScope(mapping.getDeptId())) {
				throw new BusinessException(ErrorCode.PERMISSION_DENIED, "無權刪除該使用者");
			}
		});

		user.setDeleted(true);
		user.setDeletedAt(java.time.LocalDateTime.now());
		user.setEnabled(false);
		userRepository.save(user);

		userAuditService.logAction("DELETE", adminUserId, targetUserId, "軟刪除帳號");
	}

	@Transactional
	public UserTenantMappingDto addTenantRole(String adminUserId, String targetUserId, AddTenantRoleRequest req) {
		userRepository.findById(targetUserId).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

		String tenantId = req.getTenantId() != null ? req.getTenantId() : TenantContext.getCurrentTenantId();

		// 場域存在與啟用檢核（N-3: 與 login gate 對齊，停用場域不可新增 mapping）
		com.taipei.iot.tenant.TenantEntity tenant = tenantRepository.findById(tenantId)
			.orElseThrow(() -> new BusinessException(ErrorCode.TENANT_NOT_FOUND));
		if (!Boolean.TRUE.equals(tenant.getEnabled())) {
			throw new BusinessException(ErrorCode.TENANT_DISABLED);
		}

		// 角色指派檢核：操作者只能指派自己權限範圍內的角色
		if (!roleService.isRoleAssignable(req.getRoleId())) {
			throw new BusinessException(ErrorCode.PERMISSION_DENIED, "無權指派該角色");
		}

		// DataScope 檢核：操作者只能在自己的部門範圍內操作
		if (!dataScopeHelper.isDeptInScope(req.getDeptId())) {
			throw new BusinessException(ErrorCode.PERMISSION_DENIED, "無權在該部門新增場域角色");
		}

		userTenantMappingRepository.findByUserIdAndTenantId(targetUserId, tenantId).ifPresent(existing -> {
			throw new BusinessException(ErrorCode.USER_ALREADY_EXISTS, "該帳號已存在此場域的存取權限");
		});

		UserTenantMappingEntity mapping = UserTenantMappingEntity.builder()
			.userId(targetUserId)
			.tenantId(tenantId)
			.roleId(req.getRoleId())
			.deptId(req.getDeptId())
			.enabled(true)
			.build();
		UserTenantMappingEntity saved = userTenantMappingRepository.save(mapping);

		userAuditService.logAction("ADD_TENANT", adminUserId, targetUserId, "新增場域: " + tenantId);

		return toUserTenantMappingDto(saved);
	}

	@Transactional
	public void removeTenantRole(String adminUserId, String targetUserId, Long mappingId) {
		UserTenantMappingEntity mapping = userTenantMappingRepository.findById(mappingId)
			.orElseThrow(() -> new BusinessException(ErrorCode.MAPPING_NOT_FOUND));

		if (!mapping.getUserId().equals(targetUserId)) {
			throw new BusinessException(ErrorCode.MAPPING_NOT_FOUND);
		}

		userTenantMappingRepository.delete(mapping);

		userAuditService.logAction("REMOVE_TENANT", adminUserId, targetUserId, "移除場域: " + mapping.getTenantId());
	}

	@Transactional(readOnly = true)
	public List<UserTenantMappingDto> getUserTenantMappings(String targetUserId) {
		// N-3: 僅回傳 tenant.enabled = true 的 mapping，與 login gate 對齊
		List<UserTenantMappingEntity> mappings = userTenantMappingRepository.findByUserIdAndTenantEnabled(targetUserId);

		String currentTenantId = TenantContext.getCurrentTenantId();
		boolean isSystem = TenantContext.isSystemContext();

		return mappings.stream()
			.filter(m -> isSystem || m.getTenantId().equals(currentTenantId))
			.map(this::toUserTenantMappingDto)
			.collect(Collectors.toList());
	}

	private UserListItemDto toUserListItemDto(UserTenantMappingEntity mapping) {
		return toUserListItemDto(mapping, java.util.Collections.emptyMap());
	}

	private UserListItemDto toUserListItemDto(UserTenantMappingEntity mapping, Map<Long, String> deptNameMap) {
		UserEntity user = mapping.getUser();
		if (user == null) {
			user = userRepository.findById(mapping.getUserId()).orElse(null);
		}

		RoleEntity role = mapping.getRole();
		if (role == null) {
			role = roleRepository.findById(mapping.getRoleId()).orElse(null);
		}

		String deptName = null;
		if (mapping.getDeptId() != null) {
			deptName = deptNameMap.get(mapping.getDeptId());
			if (deptName == null) {
				// 單筆路徑 fallback（getUser/createUser/updateUser/addTenantRole）
				deptName = deptInfoRepository.findById(mapping.getDeptId())
					.map(dept -> dept.getDeptName())
					.orElse(null);
			}
		}

		UserListItemDto.UserListItemDtoBuilder builder = UserListItemDto.builder()
			.roleId(mapping.getRoleId())
			.deptId(mapping.getDeptId())
			.deptName(deptName)
			.mappingId(mapping.getId())
			.mappingEnabled(mapping.getEnabled());

		if (user != null) {
			builder.userId(user.getUserId())
				.email(user.getEmail())
				.displayName(user.getDisplayName())
				.phone(user.getPhone())
				.enabled(user.getEnabled())
				.locked(user.getLocked())
				.deleted(user.getDeleted());
		}

		if (role != null) {
			builder.roleCode(role.getCode()).roleName(role.getName());
		}

		return builder.build();
	}

	private UserTenantMappingDto toUserTenantMappingDto(UserTenantMappingEntity mapping) {
		RoleEntity role = mapping.getRole();
		if (role == null) {
			role = roleRepository.findById(mapping.getRoleId()).orElse(null);
		}

		String tenantName = null;
		if (mapping.getTenant() != null) {
			tenantName = mapping.getTenant().getTenantName();
		}

		return UserTenantMappingDto.builder()
			.mappingId(mapping.getId())
			.tenantId(mapping.getTenantId())
			.tenantName(tenantName)
			.roleId(mapping.getRoleId())
			.roleCode(role != null ? role.getCode() : null)
			.roleName(role != null ? role.getName() : null)
			.deptId(mapping.getDeptId())
			.enabled(mapping.getEnabled())
			.build();
	}

}
