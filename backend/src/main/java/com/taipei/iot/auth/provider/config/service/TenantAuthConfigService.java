package com.taipei.iot.auth.provider.config.service;

import com.taipei.iot.auth.provider.config.dto.TenantAuthConfigRequest;
import com.taipei.iot.auth.provider.config.dto.TenantAuthConfigResponse;

public interface TenantAuthConfigService {

	TenantAuthConfigResponse getByTenantId(String tenantId);

	TenantAuthConfigResponse createOrUpdate(String tenantId, TenantAuthConfigRequest request);

	void deleteByTenantId(String tenantId);

	boolean testConnection(String tenantId, TenantAuthConfigRequest request);

}
