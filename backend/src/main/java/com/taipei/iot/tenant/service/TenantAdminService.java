package com.taipei.iot.tenant.service;

import com.taipei.iot.auth.entity.UserEntity;
import com.taipei.iot.auth.entity.UserTenantMappingEntity;
import com.taipei.iot.auth.provider.AuthType;
import com.taipei.iot.auth.provider.config.entity.TenantAuthConfigEntity;
import com.taipei.iot.auth.provider.config.repository.TenantAuthConfigRepository;
import com.taipei.iot.auth.repository.UserRepository;
import com.taipei.iot.auth.repository.UserTenantMappingRepository;
import com.taipei.iot.common.enums.ErrorCode;
import com.taipei.iot.common.exception.BusinessException;
import com.taipei.iot.setting.entity.SystemSettingEntity;
import com.taipei.iot.setting.enums.SettingKey;
import com.taipei.iot.setting.repository.SystemSettingRepository;
import com.taipei.iot.tenant.TenantEnabledCache;
import com.taipei.iot.tenant.TenantContext;
import com.taipei.iot.tenant.TenantEntity;
import com.taipei.iot.tenant.TenantRepository;
import com.taipei.iot.tenant.dto.CreateTenantRequest;
import com.taipei.iot.tenant.dto.DeploymentMode;
import com.taipei.iot.tenant.dto.TenantDto;
import com.taipei.iot.tenant.dto.UpdateTenantRequest;
import com.taipei.iot.user.service.PasswordValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.data.domain.Sort;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TenantAdminService {

	private static final String ADMIN_ROLE_ID = "ROLE_ADMIN";

	private final TenantRepository tenantRepository;

	private final UserRepository userRepository;

	private final UserTenantMappingRepository userTenantMappingRepository;

	private final PasswordEncoder passwordEncoder;

	private final TenantEnabledCache tenantEnabledCache;

	private final PasswordValidator passwordValidator;

	private final SystemSettingRepository systemSettingRepository;

	private final TenantAuthConfigRepository tenantAuthConfigRepository;

	@Transactional(readOnly = true)
	public List<TenantDto> listTenants() {
		return tenantRepository.findAll(Sort.by(Sort.Direction.DESC, "createTime"))
			.stream()
			.map(this::toDto)
			.collect(Collectors.toList());
	}

	@Transactional
	public TenantDto createTenant(CreateTenantRequest req) {
		if (tenantRepository.findByTenantCode(req.getTenantCode()).isPresent()) {
			throw new BusinessException(ErrorCode.TENANT_CODE_DUPLICATE);
		}

		String tenantId = "T_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();

		TenantEntity tenant = new TenantEntity();
		tenant.setTenantId(tenantId);
		tenant.setTenantCode(req.getTenantCode());
		tenant.setTenantName(req.getTenantName());
		String mode = resolveDeploymentMode(req.getDeploymentMode());
		tenant.setDeploymentMode(mode);
		tenant.setEnabled(true);
		tenantRepository.save(tenant);

		// Seed all system settings with defaults for the new tenant
		seedDefaultSettings(tenantId);

		// Seed tenant_auth_config (預設 LOCAL，可由 request 指定)
		AuthType initialAuthMethod = req.getInitialAuthMethod() != null ? req.getInitialAuthMethod() : AuthType.LOCAL;
		TenantAuthConfigEntity authConfig = TenantAuthConfigEntity.builder()
			.tenantId(tenantId)
			.authType(initialAuthMethod)
			.enabled(true)
			.fallbackLocal(true)
			.build();
		TenantContext.runInSystemContext(() -> tenantAuthConfigRepository.save(authConfig));

		// 若同時提供初始管理員資料，則建立帳號
		if (req.getAdminEmail() != null && !req.getAdminEmail().isBlank() && req.getAdminPassword() != null
				&& !req.getAdminPassword().isBlank()) {

			if (userRepository.existsByEmail(req.getAdminEmail())) {
				throw new BusinessException(ErrorCode.USER_ALREADY_EXISTS);
			}

			// [Tenant v2 T-4] 套用密碼政策：避免初始管理員密碼繞過 PasswordValidator
			// （複雜度、長度、不含使用者郵箱等規則）。新場域剛建立完，policyResolver 會
			// fallback 至 platform 預設政策。username 尚未產生，僅傳入 email 讓
			// not_contains_username 規則覆蓋郵箱比對。
			passwordValidator.validate(tenantId, req.getAdminPassword(),
					new PasswordValidator.UserContext(null, req.getAdminEmail()));

			String userId = UUID.randomUUID().toString();
			UserEntity adminUser = UserEntity.builder()
				.userId(userId)
				.email(req.getAdminEmail())
				.displayName(req.getAdminDisplayName() != null ? req.getAdminDisplayName() : req.getAdminEmail())
				.passwordHash(passwordEncoder.encode(req.getAdminPassword()))
				.passwordChangedAt(LocalDateTime.now())
				.enabled(true)
				.locked(false)
				.loginFailCount(0)
				.isSuperAdmin(false)
				.build();
			userRepository.save(adminUser);

			// 以 SYSTEM context 儲存 mapping，繞過 TenantFilterAspect 的 tenantId 限制
			TenantContext.runInSystemContext(() -> {
				UserTenantMappingEntity mapping = UserTenantMappingEntity.builder()
					.userId(userId)
					.tenantId(tenantId)
					.roleId(ADMIN_ROLE_ID)
					.enabled(true)
					.build();
				userTenantMappingRepository.save(mapping);
			});
		}

		return toDto(tenant);
	}

	@Transactional
	public TenantDto updateTenant(String tenantId, UpdateTenantRequest req) {
		TenantEntity tenant = tenantRepository.findById(tenantId)
			.orElseThrow(() -> new BusinessException(ErrorCode.TENANT_NOT_FOUND));

		tenant.setTenantName(req.getTenantName());
		if (req.getDeploymentMode() != null && !req.getDeploymentMode().isBlank()) {
			String mode = resolveDeploymentMode(req.getDeploymentMode());
			tenant.setDeploymentMode(mode);
		}
		return toDto(tenantRepository.save(tenant));
	}

	@Transactional
	public void toggleEnabled(String tenantId, boolean enabled) {
		TenantEntity tenant = tenantRepository.findById(tenantId)
			.orElseThrow(() -> new BusinessException(ErrorCode.TENANT_NOT_FOUND));
		tenant.setEnabled(enabled);
		tenantRepository.save(tenant);

		// 即時更新記憶體快取，使 JwtAuthenticationFilter 立即拒絕已停用場域的請求
		if (enabled) {
			tenantEnabledCache.markEnabled(tenantId);
		}
		else {
			tenantEnabledCache.markDisabled(tenantId);
		}
	}

	private TenantDto toDto(TenantEntity e) {
		return TenantDto.builder()
			.tenantId(e.getTenantId())
			.tenantCode(e.getTenantCode())
			.tenantName(e.getTenantName())
			.deploymentMode(e.getDeploymentMode())
			.enabled(e.getEnabled())
			.createTime(e.getCreateTime())
			.build();
	}

	private String resolveDeploymentMode(String raw) {
		DeploymentMode dm = DeploymentMode.fromString(raw);
		return dm != null ? dm.name() : DeploymentMode.CLOUD.name();
	}

	private void seedDefaultSettings(String tenantId) {
		for (SettingKey sk : SettingKey.values()) {
			SystemSettingEntity entity = SystemSettingEntity.builder()
				.tenantId(tenantId)
				.settingKey(sk.getKey())
				.settingValue(sk.getDefaultValue())
				.description(sk.getDescription())
				.build();
			systemSettingRepository.save(entity);
		}
	}

}
