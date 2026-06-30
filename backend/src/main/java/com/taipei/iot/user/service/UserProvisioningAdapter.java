package com.taipei.iot.user.service;

import com.taipei.iot.common.context.TenantContext;
import com.taipei.iot.common.enums.ErrorCode;
import com.taipei.iot.common.exception.BusinessException;
import com.taipei.iot.common.user.port.TenantAdminProvisioner;
import com.taipei.iot.user.entity.UserEntity;
import com.taipei.iot.user.entity.UserTenantMappingEntity;
import com.taipei.iot.user.repository.UserRepository;
import com.taipei.iot.user.repository.UserTenantMappingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * {@code user}-module adapter for {@link TenantAdminProvisioner}. Owns the initial admin
 * account provisioning logic that used to live in the {@code tenant} module.
 */
@Component
@RequiredArgsConstructor
public class UserProvisioningAdapter implements TenantAdminProvisioner {

	private static final String ADMIN_ROLE_ID = "ROLE_ADMIN";

	private final UserRepository userRepository;

	private final UserTenantMappingRepository userTenantMappingRepository;

	private final PasswordEncoder passwordEncoder;

	private final PasswordValidator passwordValidator;

	@Override
	public void provisionTenantAdmin(TenantAdminSpec spec) {
		if (spec.email() == null || spec.email().isBlank() || spec.rawPassword() == null
				|| spec.rawPassword().isBlank()) {
			return;
		}

		if (userRepository.existsByEmail(spec.email())) {
			throw new BusinessException(ErrorCode.USER_ALREADY_EXISTS);
		}

		// [Tenant v2 T-4] 套用密碼政策：避免初始管理員密碼繞過 PasswordValidator
		// （複雜度、長度、不含使用者郵箱等規則）。新場域剛建立完，policyResolver 會
		// fallback 至 platform 預設政策。username 尚未產生，僅傳入 email 讓
		// not_contains_username 規則覆蓋郵箱比對。
		passwordValidator.validate(spec.tenantId(), spec.rawPassword(),
				new PasswordValidator.UserContext(null, spec.email()));

		String userId = UUID.randomUUID().toString();
		UserEntity adminUser = UserEntity.builder()
			.userId(userId)
			.email(spec.email())
			.displayName(spec.displayName() != null ? spec.displayName() : spec.email())
			.passwordHash(passwordEncoder.encode(spec.rawPassword()))
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
				.tenantId(spec.tenantId())
				.roleId(ADMIN_ROLE_ID)
				.enabled(true)
				.build();
			userTenantMappingRepository.save(mapping);
		});
	}

}
