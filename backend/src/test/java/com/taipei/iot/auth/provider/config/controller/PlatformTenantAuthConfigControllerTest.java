package com.taipei.iot.auth.provider.config.controller;

import com.taipei.iot.auth.provider.config.dto.TenantAuthConfigRequest;
import com.taipei.iot.auth.provider.config.dto.TenantAuthConfigResponse;
import com.taipei.iot.auth.provider.config.service.TenantAuthConfigService;
import com.taipei.iot.common.response.BaseResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * [2.1.2] Unit test for {@link PlatformTenantAuthConfigController}: verifies the
 * controller delegates to {@link TenantAuthConfigService} using the {@code tenantId} path
 * variable (not {@code TenantContext}) and that the class-level routing / authorisation
 * annotations are set correctly.
 */
@ExtendWith(MockitoExtension.class)
class PlatformTenantAuthConfigControllerTest {

	@Mock
	private TenantAuthConfigService service;

	@InjectMocks
	private PlatformTenantAuthConfigController controller;

	private static final String TENANT = "TENANT_A";

	@Test
	void classLevel_routesUnderPlatformTenantsPath() {
		RequestMapping rm = PlatformTenantAuthConfigController.class.getAnnotation(RequestMapping.class);
		assertThat(rm).isNotNull();
		assertThat(rm.value()).containsExactly("/v1/platform/tenants/{tenantId}/auth-config");
	}

	@Test
	void classLevel_requiresPlatformTenantManagePermission() {
		PreAuthorize pa = PlatformTenantAuthConfigController.class.getAnnotation(PreAuthorize.class);
		assertThat(pa).isNotNull();
		assertThat(pa.value()).isEqualTo("hasAuthority('PLATFORM_TENANT_MANAGE')");
	}

	@Test
	void get_delegatesWithPathVariableTenantId() {
		TenantAuthConfigResponse expected = TenantAuthConfigResponse.builder().build();
		when(service.getByTenantId(TENANT)).thenReturn(expected);

		BaseResponse<TenantAuthConfigResponse> resp = controller.get(TENANT);

		assertThat(resp.getBody()).isSameAs(expected);
		verify(service).getByTenantId(TENANT);
	}

	@Test
	void createOrUpdate_delegatesWithPathVariableTenantId() {
		TenantAuthConfigRequest req = new TenantAuthConfigRequest();
		TenantAuthConfigResponse expected = TenantAuthConfigResponse.builder().build();
		when(service.createOrUpdate(TENANT, req)).thenReturn(expected);

		BaseResponse<TenantAuthConfigResponse> resp = controller.createOrUpdate(TENANT, req);

		assertThat(resp.getBody()).isSameAs(expected);
		verify(service).createOrUpdate(TENANT, req);
	}

	@Test
	void delete_delegatesWithPathVariableTenantId() {
		controller.delete(TENANT);
		verify(service).deleteByTenantId(TENANT);
	}

	@Test
	void testConnection_delegatesWithPathVariableTenantId() {
		TenantAuthConfigRequest req = new TenantAuthConfigRequest();
		when(service.testConnection(TENANT, req)).thenReturn(true);

		BaseResponse<Boolean> resp = controller.testConnection(TENANT, req);

		assertThat(resp.getBody()).isTrue();
		verify(service).testConnection(TENANT, req);
	}

}
