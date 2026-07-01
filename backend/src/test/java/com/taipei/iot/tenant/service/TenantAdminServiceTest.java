package com.taipei.iot.tenant.service;

import com.taipei.iot.common.auth.port.TenantAuthConfigProvisioner;
import com.taipei.iot.common.enums.AuthType;
import com.taipei.iot.common.enums.ErrorCode;
import com.taipei.iot.common.exception.BusinessException;
import com.taipei.iot.common.user.port.TenantAdminProvisioner;
import com.taipei.iot.setting.repository.SystemSettingRepository;
import com.taipei.iot.tenant.cache.TenantEnabledCache;
import com.taipei.iot.tenant.entity.TenantEntity;
import com.taipei.iot.tenant.repository.TenantRepository;
import com.taipei.iot.tenant.dto.CreateTenantRequest;
import com.taipei.iot.tenant.dto.UpdateTenantRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 單元測試 {@link TenantAdminService}。
 *
 * <p>
 * createTenant 將初始管理員與 auth config 佈建委派給 {@link TenantAdminProvisioner}、
 * {@link TenantAuthConfigProvisioner}（依賴反轉，避免 tenant→user / tenant→auth 反向依賴）。 本測試只驗證
 * tenant 層職責與委派；佈建細節由 adapter 自身測試覆蓋。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class TenantAdminServiceTest {

	@Mock
	private TenantRepository tenantRepository;

	@Mock
	private TenantEnabledCache tenantEnabledCache;

	@Mock
	private SystemSettingRepository systemSettingRepository;

	@Mock
	private TenantAdminProvisioner tenantAdminProvisioner;

	@Mock
	private TenantAuthConfigProvisioner tenantAuthConfigProvisioner;

	@InjectMocks
	private TenantAdminService service;

	private CreateTenantRequest baseRequest;

	@BeforeEach
	void setUp() {
		baseRequest = new CreateTenantRequest();
		baseRequest.setTenantCode("ACME");
		baseRequest.setTenantName("Acme Corp");
		baseRequest.setDeploymentMode("CLOUD");
	}

	private CreateTenantRequest withAdmin(String email, String password) {
		baseRequest.setAdminEmail(email);
		baseRequest.setAdminPassword(password);
		baseRequest.setAdminDisplayName("Admin");
		return baseRequest;
	}

	// ───────── 委派佈建 ─────────

	@Test
	void createTenant_seedsAuthConfig_andDelegatesAdminProvisioning() {
		when(tenantRepository.findByTenantCode("ACME")).thenReturn(Optional.empty());

		service.createTenant(withAdmin("admin@acme.test", "StrongP@ssw0rd"));

		// auth config 以預設 LOCAL 佈建
		ArgumentCaptor<String> authTenantId = ArgumentCaptor.forClass(String.class);
		verify(tenantAuthConfigProvisioner).seedDefaultAuthConfig(authTenantId.capture(), eq(AuthType.LOCAL));
		assertThat(authTenantId.getValue()).startsWith("T_");

		// 管理員佈建委派，spec 帶入剛建立的 tenantId 與 request 內容
		ArgumentCaptor<TenantAdminProvisioner.TenantAdminSpec> specCaptor = ArgumentCaptor
			.forClass(TenantAdminProvisioner.TenantAdminSpec.class);
		verify(tenantAdminProvisioner).provisionTenantAdmin(specCaptor.capture());
		TenantAdminProvisioner.TenantAdminSpec spec = specCaptor.getValue();
		assertThat(spec.tenantId()).startsWith("T_");
		assertThat(spec.email()).isEqualTo("admin@acme.test");
		assertThat(spec.rawPassword()).isEqualTo("StrongP@ssw0rd");
		assertThat(spec.displayName()).isEqualTo("Admin");
	}

	@Test
	void createTenant_withoutAdmin_stillDelegates_withBlankSpec() {
		when(tenantRepository.findByTenantCode("ACME")).thenReturn(Optional.empty());

		service.createTenant(baseRequest); // admin* fields all null

		// 仍委派（由 adapter 判斷 blank 後略過建立帳號）
		ArgumentCaptor<TenantAdminProvisioner.TenantAdminSpec> specCaptor = ArgumentCaptor
			.forClass(TenantAdminProvisioner.TenantAdminSpec.class);
		verify(tenantAdminProvisioner).provisionTenantAdmin(specCaptor.capture());
		assertThat(specCaptor.getValue().email()).isNull();
		assertThat(specCaptor.getValue().rawPassword()).isNull();
	}

	// ───────── 基本流程 ─────────

	@Test
	void createTenant_duplicateCode_throws() {
		when(tenantRepository.findByTenantCode("ACME")).thenReturn(Optional.of(new TenantEntity()));

		assertThatThrownBy(() -> service.createTenant(baseRequest)).isInstanceOf(BusinessException.class)
			.extracting("errorCode")
			.isEqualTo(ErrorCode.TENANT_CODE_DUPLICATE);

		verify(tenantRepository, never()).save(any());
		verify(tenantAdminProvisioner, never()).provisionTenantAdmin(any());
	}

	// ───────── updateTenant ─────────

	@Test
	void updateTenant_updatesName_andPersists() {
		TenantEntity existing = new TenantEntity();
		existing.setTenantId("T_X");
		existing.setTenantCode("ACME");
		existing.setTenantName("Old");
		existing.setEnabled(true);
		when(tenantRepository.findById("T_X")).thenReturn(Optional.of(existing));
		when(tenantRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

		UpdateTenantRequest req = new UpdateTenantRequest();
		req.setTenantName("New");
		service.updateTenant("T_X", req);

		assertThat(existing.getTenantName()).isEqualTo("New");
		verify(tenantRepository).save(existing);
	}

	@Test
	void updateTenant_notFound_throws() {
		when(tenantRepository.findById("T_X")).thenReturn(Optional.empty());

		UpdateTenantRequest req = new UpdateTenantRequest();
		req.setTenantName("New");

		assertThatThrownBy(() -> service.updateTenant("T_X", req)).isInstanceOf(BusinessException.class)
			.extracting("errorCode")
			.isEqualTo(ErrorCode.TENANT_NOT_FOUND);
	}

	// ───────── toggleEnabled ─────────

	@Test
	void toggleEnabled_disable_persistsAndUpdatesCache() {
		TenantEntity tenant = new TenantEntity();
		tenant.setTenantId("T_X");
		tenant.setEnabled(true);
		when(tenantRepository.findById("T_X")).thenReturn(Optional.of(tenant));

		service.toggleEnabled("T_X", false);

		assertThat(tenant.getEnabled()).isFalse();
		verify(tenantRepository).save(tenant);
		verify(tenantEnabledCache).markDisabled("T_X");
		verify(tenantEnabledCache, never()).markEnabled(any());
	}

	@Test
	void toggleEnabled_enable_persistsAndUpdatesCache() {
		TenantEntity tenant = new TenantEntity();
		tenant.setTenantId("T_X");
		tenant.setEnabled(false);
		when(tenantRepository.findById("T_X")).thenReturn(Optional.of(tenant));

		service.toggleEnabled("T_X", true);

		assertThat(tenant.getEnabled()).isTrue();
		verify(tenantRepository).save(tenant);
		verify(tenantEnabledCache).markEnabled("T_X");
		verify(tenantEnabledCache, never()).markDisabled(any());
	}

	@Test
	void toggleEnabled_notFound_throws_andCacheNotTouched() {
		when(tenantRepository.findById("T_X")).thenReturn(Optional.empty());
		// 不允許任何 cache 操作
		lenient().doThrow(new AssertionError("cache should not be touched"))
			.when(tenantEnabledCache)
			.markDisabled(any());

		assertThatThrownBy(() -> service.toggleEnabled("T_X", false)).isInstanceOf(BusinessException.class);

		verify(tenantEnabledCache, never()).markDisabled(any());
		verify(tenantEnabledCache, never()).markEnabled(any());
	}

}
