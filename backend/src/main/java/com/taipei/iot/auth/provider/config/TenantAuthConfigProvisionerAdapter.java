package com.taipei.iot.auth.provider.config;

import com.taipei.iot.auth.provider.config.entity.TenantAuthConfigEntity;
import com.taipei.iot.auth.provider.config.repository.TenantAuthConfigRepository;
import com.taipei.iot.common.auth.port.TenantAuthConfigProvisioner;
import com.taipei.iot.common.context.TenantContext;
import com.taipei.iot.common.enums.AuthType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * {@code auth}-module adapter for {@link TenantAuthConfigProvisioner}. Owns the default
 * {@code tenant_auth_config} seeding that used to live in the {@code tenant} module.
 */
@Component
@RequiredArgsConstructor
public class TenantAuthConfigProvisionerAdapter implements TenantAuthConfigProvisioner {

	private final TenantAuthConfigRepository tenantAuthConfigRepository;

	@Override
	public void seedDefaultAuthConfig(String tenantId, AuthType authType) {
		TenantAuthConfigEntity authConfig = TenantAuthConfigEntity.builder()
			.tenantId(tenantId)
			.authType(authType)
			.enabled(true)
			.fallbackLocal(true)
			.build();
		TenantContext.runInSystemContext(() -> tenantAuthConfigRepository.save(authConfig));
	}

}
