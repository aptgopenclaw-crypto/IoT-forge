package com.taipei.iot.tenant.service;

import com.taipei.iot.auth.entity.UserEntity;
import com.taipei.iot.auth.entity.UserTenantMappingEntity;
import com.taipei.iot.auth.provider.config.repository.TenantAuthConfigRepository;
import com.taipei.iot.auth.repository.UserRepository;
import com.taipei.iot.auth.repository.UserTenantMappingRepository;
import com.taipei.iot.common.enums.ErrorCode;
import com.taipei.iot.common.exception.BusinessException;
import com.taipei.iot.setting.repository.SystemSettingRepository;
import com.taipei.iot.tenant.TenantEnabledCache;
import com.taipei.iot.tenant.TenantEntity;
import com.taipei.iot.tenant.TenantRepository;
import com.taipei.iot.tenant.dto.CreateTenantRequest;
import com.taipei.iot.tenant.dto.UpdateTenantRequest;
import com.taipei.iot.user.service.PasswordValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

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
 * 重點：T-4 修復後 createTenant 必須在編碼密碼前呼叫 {@link PasswordValidator#validate}，
 * 確保初始管理員密碼不能繞過密碼政策。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class TenantAdminServiceTest {

	@Mock
	private TenantRepository tenantRepository;

	@Mock
	private UserRepository userRepository;

	@Mock
	private UserTenantMappingRepository userTenantMappingRepository;

	@Mock
	private PasswordEncoder passwordEncoder;

	@Mock
	private TenantEnabledCache tenantEnabledCache;

	@Mock
	private PasswordValidator passwordValidator;

	@Mock
	private SystemSettingRepository systemSettingRepository;

	@Mock
	private TenantAuthConfigRepository tenantAuthConfigRepository;

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

	// ───────── T-4: password policy enforcement ─────────

	@Nested
	class PasswordPolicyEnforcement {

		@Test
		void createTenant_withAdmin_invokesPasswordValidator_beforeEncoding() {
			when(tenantRepository.findByTenantCode("ACME")).thenReturn(Optional.empty());
			when(userRepository.existsByEmail("admin@acme.test")).thenReturn(false);
			when(passwordEncoder.encode(any())).thenReturn("hashed");

			service.createTenant(withAdmin("admin@acme.test", "StrongP@ssw0rd"));

			ArgumentCaptor<String> tenantIdCaptor = ArgumentCaptor.forClass(String.class);
			ArgumentCaptor<PasswordValidator.UserContext> ctxCaptor = ArgumentCaptor
				.forClass(PasswordValidator.UserContext.class);
			verify(passwordValidator).validate(tenantIdCaptor.capture(), eq("StrongP@ssw0rd"), ctxCaptor.capture());

			// 應傳入剛建立的 tenantId（非 null、非 platform-fallback null）
			assertThat(tenantIdCaptor.getValue()).startsWith("T_");
			// UserContext 應含 email（username 尚未產生因此為 null）
			assertThat(ctxCaptor.getValue().username()).isNull();
			assertThat(ctxCaptor.getValue().email()).isEqualTo("admin@acme.test");

			// 確認流程：validate → encode → save
			verify(passwordEncoder).encode("StrongP@ssw0rd");
			verify(userRepository).save(any(UserEntity.class));
		}

		@Test
		void createTenant_weakPassword_validatorThrows_userNotCreated() {
			when(tenantRepository.findByTenantCode("ACME")).thenReturn(Optional.empty());
			when(userRepository.existsByEmail("admin@acme.test")).thenReturn(false);
			// 密碼政策拒絕
			org.mockito.Mockito.doThrow(new BusinessException(ErrorCode.RESET_PASSWORD_ERROR, "密碼長度至少 12 字元"))
				.when(passwordValidator)
				.validate(any(), eq("password"), any());

			assertThatThrownBy(() -> service.createTenant(withAdmin("admin@acme.test", "password")))
				.isInstanceOf(BusinessException.class)
				.hasMessageContaining("密碼長度至少 12 字元");

			// 應在 encode / save user 之前就拋出，避免弱密碼被持久化
			verify(passwordEncoder, never()).encode(any());
			verify(userRepository, never()).save(any(UserEntity.class));
			verify(userTenantMappingRepository, never()).save(any());
		}

		@Test
		void createTenant_withoutAdmin_doesNotInvokeValidator() {
			when(tenantRepository.findByTenantCode("ACME")).thenReturn(Optional.empty());

			service.createTenant(baseRequest); // admin* fields all null

			verify(passwordValidator, never()).validate(any(), any(), any());
			verify(userRepository, never()).save(any(UserEntity.class));
		}

	}

	// ───────── 基本流程 ─────────

	@Test
	void createTenant_duplicateCode_throws() {
		when(tenantRepository.findByTenantCode("ACME")).thenReturn(Optional.of(new TenantEntity()));

		assertThatThrownBy(() -> service.createTenant(baseRequest)).isInstanceOf(BusinessException.class)
			.extracting("errorCode")
			.isEqualTo(ErrorCode.TENANT_CODE_DUPLICATE);

		verify(tenantRepository, never()).save(any());
	}

	@Test
	void createTenant_duplicateEmail_throws_andValidatorNotInvoked() {
		when(tenantRepository.findByTenantCode("ACME")).thenReturn(Optional.empty());
		when(userRepository.existsByEmail("admin@acme.test")).thenReturn(true);

		assertThatThrownBy(() -> service.createTenant(withAdmin("admin@acme.test", "StrongP@ssw0rd")))
			.isInstanceOf(BusinessException.class)
			.extracting("errorCode")
			.isEqualTo(ErrorCode.USER_ALREADY_EXISTS);

		// email 重複時應在密碼驗證之前就攔截（成本較低的檢查先做）
		verify(passwordValidator, never()).validate(any(), any(), any());
	}

	@Test
	void createTenant_savesAdminMapping_withCorrectRole() {
		when(tenantRepository.findByTenantCode("ACME")).thenReturn(Optional.empty());
		when(userRepository.existsByEmail("admin@acme.test")).thenReturn(false);
		when(passwordEncoder.encode(any())).thenReturn("hashed");

		service.createTenant(withAdmin("admin@acme.test", "StrongP@ssw0rd"));

		ArgumentCaptor<UserTenantMappingEntity> mappingCaptor = ArgumentCaptor.forClass(UserTenantMappingEntity.class);
		verify(userTenantMappingRepository).save(mappingCaptor.capture());
		assertThat(mappingCaptor.getValue().getRoleId()).isEqualTo("ROLE_ADMIN");
		assertThat(mappingCaptor.getValue().getEnabled()).isTrue();
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
