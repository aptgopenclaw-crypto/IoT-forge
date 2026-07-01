package com.taipei.iot.tenant.provider;

import com.taipei.iot.common.tenant.TenantIdProvider;
import com.taipei.iot.tenant.entity.TenantEntity;
import com.taipei.iot.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * {@link TenantIdProvider} 的 tenant 模組實作。
 */
@Component
@RequiredArgsConstructor
public class TenantIdProviderImpl implements TenantIdProvider {

	private final TenantRepository tenantRepository;

	@Override
	public List<String> findEnabledTenantIds() {
		return tenantRepository.findByEnabledTrue().stream().map(TenantEntity::getTenantId).toList();
	}

}
