package com.taipei.iot.user.service;

import com.taipei.iot.common.enums.ErrorCode;
import com.taipei.iot.common.exception.BusinessException;
import com.taipei.iot.common.user.port.TenantAdminProvisioner.TenantAdminSpec;
import com.taipei.iot.user.entity.UserEntity;
import com.taipei.iot.user.entity.UserTenantMappingEntity;
import com.taipei.iot.user.repository.UserRepository;
import com.taipei.iot.user.repository.UserTenantMappingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 單元測試 {@link UserProvisioningAdapter}。
 *
 * <p>
 * 重點：T-4 — provision 前必須呼叫 {@link PasswordValidator#validate}，確保初始管理員 密碼不能繞過密碼政策；且 blank
 * email/password 應略過建立。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class UserProvisioningAdapterTest {

	@Mock
	private UserRepository userRepository;

	@Mock
	private UserTenantMappingRepository userTenantMappingRepository;

	@Mock
	private PasswordEncoder passwordEncoder;

	@Mock
	private PasswordValidator passwordValidator;

	@InjectMocks
	private UserProvisioningAdapter adapter;

	private TenantAdminSpec spec(String email, String password) {
		return new TenantAdminSpec("T_ACME000001", email, password, "Admin");
	}

	@Test
	void provisionTenantAdmin_invokesPasswordValidator_beforeEncoding() {
		when(userRepository.existsByEmail("admin@acme.test")).thenReturn(false);
		when(passwordEncoder.encode(any())).thenReturn("hashed");

		adapter.provisionTenantAdmin(spec("admin@acme.test", "StrongP@ssw0rd"));

		ArgumentCaptor<PasswordValidator.UserContext> ctxCaptor = ArgumentCaptor
			.forClass(PasswordValidator.UserContext.class);
		verify(passwordValidator).validate(eq("T_ACME000001"), eq("StrongP@ssw0rd"), ctxCaptor.capture());

		// UserContext 應含 email（username 尚未產生因此為 null）
		assertThat(ctxCaptor.getValue().username()).isNull();
		assertThat(ctxCaptor.getValue().email()).isEqualTo("admin@acme.test");

		// 確認流程：validate → encode → save
		verify(passwordEncoder).encode("StrongP@ssw0rd");
		verify(userRepository).save(any(UserEntity.class));
	}

	@Test
	void provisionTenantAdmin_weakPassword_validatorThrows_userNotCreated() {
		when(userRepository.existsByEmail("admin@acme.test")).thenReturn(false);
		org.mockito.Mockito.doThrow(new BusinessException(ErrorCode.RESET_PASSWORD_ERROR, "密碼長度至少 12 字元"))
			.when(passwordValidator)
			.validate(any(), eq("password"), any());

		assertThatThrownBy(() -> adapter.provisionTenantAdmin(spec("admin@acme.test", "password")))
			.isInstanceOf(BusinessException.class)
			.hasMessageContaining("密碼長度至少 12 字元");

		// 應在 encode / save user 之前就拋出，避免弱密碼被持久化
		verify(passwordEncoder, never()).encode(any());
		verify(userRepository, never()).save(any(UserEntity.class));
		verify(userTenantMappingRepository, never()).save(any());
	}

	@Test
	void provisionTenantAdmin_duplicateEmail_throws_andValidatorNotInvoked() {
		when(userRepository.existsByEmail("admin@acme.test")).thenReturn(true);

		assertThatThrownBy(() -> adapter.provisionTenantAdmin(spec("admin@acme.test", "StrongP@ssw0rd")))
			.isInstanceOf(BusinessException.class)
			.extracting("errorCode")
			.isEqualTo(ErrorCode.USER_ALREADY_EXISTS);

		// email 重複時應在密碼驗證之前就攔截（成本較低的檢查先做）
		verify(passwordValidator, never()).validate(any(), any(), any());
	}

	@Test
	void provisionTenantAdmin_savesMapping_withAdminRole() {
		when(userRepository.existsByEmail("admin@acme.test")).thenReturn(false);
		when(passwordEncoder.encode(any())).thenReturn("hashed");

		adapter.provisionTenantAdmin(spec("admin@acme.test", "StrongP@ssw0rd"));

		ArgumentCaptor<UserTenantMappingEntity> mappingCaptor = ArgumentCaptor.forClass(UserTenantMappingEntity.class);
		verify(userTenantMappingRepository).save(mappingCaptor.capture());
		assertThat(mappingCaptor.getValue().getRoleId()).isEqualTo("ROLE_ADMIN");
		assertThat(mappingCaptor.getValue().getEnabled()).isTrue();
		assertThat(mappingCaptor.getValue().getTenantId()).isEqualTo("T_ACME000001");
	}

	@Test
	void provisionTenantAdmin_blankEmail_isNoOp() {
		adapter.provisionTenantAdmin(spec(null, null));

		verify(passwordValidator, never()).validate(any(), any(), any());
		verify(userRepository, never()).save(any(UserEntity.class));
		verify(userTenantMappingRepository, never()).save(any());
	}

}
