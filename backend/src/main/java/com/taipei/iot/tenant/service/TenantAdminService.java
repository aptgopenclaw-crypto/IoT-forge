package com.taipei.iot.tenant.service;

import com.taipei.iot.common.auth.port.TenantAuthConfigProvisioner;
import com.taipei.iot.common.enums.AuthType;
import com.taipei.iot.common.enums.ErrorCode;
import com.taipei.iot.common.exception.BusinessException;
import com.taipei.iot.common.user.port.TenantAdminProvisioner;
import com.taipei.iot.setting.entity.SystemSettingEntity;
import com.taipei.iot.setting.enums.SettingKey;
import com.taipei.iot.setting.repository.SystemSettingRepository;
import com.taipei.iot.tenant.cache.TenantEnabledCache;
import com.taipei.iot.tenant.entity.TenantEntity;
import com.taipei.iot.tenant.repository.TenantRepository;
import com.taipei.iot.tenant.dto.CreateTenantRequest;
import com.taipei.iot.tenant.dto.DeploymentMode;
import com.taipei.iot.tenant.dto.TenantDto;
import com.taipei.iot.tenant.dto.UpdateTenantRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TenantAdminService {

	private final TenantRepository tenantRepository;

	private final TenantEnabledCache tenantEnabledCache;

	private final SystemSettingRepository systemSettingRepository;

	private final TenantAdminProvisioner tenantAdminProvisioner;

	private final TenantAuthConfigProvisioner tenantAuthConfigProvisioner;

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
		tenantAuthConfigProvisioner.seedDefaultAuthConfig(tenantId, initialAuthMethod);

		// 若同時提供初始管理員資料，則建立帳號（密碼政策驗證於 adapter 內處理）
		tenantAdminProvisioner.provisionTenantAdmin(new TenantAdminProvisioner.TenantAdminSpec(tenantId,
				req.getAdminEmail(), req.getAdminPassword(), req.getAdminDisplayName()));

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
