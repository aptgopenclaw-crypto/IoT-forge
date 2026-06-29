package com.taipei.iot.tenant.service;

import com.taipei.iot.auth.repository.UserRepository;
import com.taipei.iot.auth.repository.UserTenantMappingRepository;
import com.taipei.iot.auth.provider.config.repository.TenantAuthConfigRepository;
import com.taipei.iot.setting.entity.SystemSettingEntity;
import com.taipei.iot.setting.enums.SettingKey;
import com.taipei.iot.setting.repository.SystemSettingRepository;
import com.taipei.iot.tenant.TenantEnabledCache;
import com.taipei.iot.tenant.TenantRepository;
import com.taipei.iot.tenant.dto.CreateTenantRequest;
import com.taipei.iot.user.service.PasswordValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * [Setting N-2] 驗證 createTenant 時自動 seed 全部 SettingKey 預設值。
 */
@ExtendWith(MockitoExtension.class)
class TenantAdminServiceSeedSettingsTest {

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
		baseRequest.setTenantCode("NEW_TENANT");
		baseRequest.setTenantName("New Tenant");
		baseRequest.setDeploymentMode("CLOUD");
	}

	@Test
	void createTenant_shouldSeedAllSettingKeys() {
		when(tenantRepository.findByTenantCode("NEW_TENANT")).thenReturn(Optional.empty());

		service.createTenant(baseRequest);

		ArgumentCaptor<SystemSettingEntity> captor = ArgumentCaptor.forClass(SystemSettingEntity.class);
		verify(systemSettingRepository, times(SettingKey.values().length)).save(captor.capture());

		List<SystemSettingEntity> saved = captor.getAllValues();
		assertThat(saved).hasSize(SettingKey.values().length);

		for (SettingKey sk : SettingKey.values()) {
			assertThat(saved)
				.anyMatch(e -> e.getSettingKey().equals(sk.getKey()) && e.getSettingValue().equals(sk.getDefaultValue())
						&& e.getDescription().equals(sk.getDescription()));
		}
	}

	@Test
	void createTenant_seededSettings_shouldHaveCorrectTenantId() {
		when(tenantRepository.findByTenantCode("NEW_TENANT")).thenReturn(Optional.empty());

		service.createTenant(baseRequest);

		ArgumentCaptor<SystemSettingEntity> captor = ArgumentCaptor.forClass(SystemSettingEntity.class);
		verify(systemSettingRepository, atLeastOnce()).save(captor.capture());

		// All seeded entities should have the newly created tenantId (starts with T_)
		for (SystemSettingEntity entity : captor.getAllValues()) {
			assertThat(entity.getTenantId()).startsWith("T_");
		}
	}

	@Test
	void createTenant_shouldSeedIdleTimeoutWithDefault15() {
		when(tenantRepository.findByTenantCode("NEW_TENANT")).thenReturn(Optional.empty());

		service.createTenant(baseRequest);

		ArgumentCaptor<SystemSettingEntity> captor = ArgumentCaptor.forClass(SystemSettingEntity.class);
		verify(systemSettingRepository, atLeastOnce()).save(captor.capture());

		SystemSettingEntity idleTimeout = captor.getAllValues()
			.stream()
			.filter(e -> "idle_timeout_minutes".equals(e.getSettingKey()))
			.findFirst()
			.orElseThrow();

		assertThat(idleTimeout.getSettingValue()).isEqualTo("15");
		assertThat(idleTimeout.getDescription()).isEqualTo("使用者閒置自動登出時間（分鐘）");
	}

	@Test
	void createTenant_shouldSeedFrontendBaseUrl() {
		when(tenantRepository.findByTenantCode("NEW_TENANT")).thenReturn(Optional.empty());

		service.createTenant(baseRequest);

		ArgumentCaptor<SystemSettingEntity> captor = ArgumentCaptor.forClass(SystemSettingEntity.class);
		verify(systemSettingRepository, atLeastOnce()).save(captor.capture());

		SystemSettingEntity frontendUrl = captor.getAllValues()
			.stream()
			.filter(e -> "frontend_base_url".equals(e.getSettingKey()))
			.findFirst()
			.orElseThrow();

		assertThat(frontendUrl.getSettingValue()).isEqualTo("http://localhost:5173");
	}

	@Test
	void createTenant_seedShouldHappenBeforeAdminCreation() {
		baseRequest.setAdminEmail("admin@new.test");
		baseRequest.setAdminPassword("StrongP@ssw0rd!");
		baseRequest.setAdminDisplayName("Admin");

		when(tenantRepository.findByTenantCode("NEW_TENANT")).thenReturn(Optional.empty());
		when(userRepository.existsByEmail("admin@new.test")).thenReturn(false);
		when(passwordEncoder.encode(any())).thenReturn("hashed");

		service.createTenant(baseRequest);

		// Settings should be seeded
		verify(systemSettingRepository, times(SettingKey.values().length)).save(any(SystemSettingEntity.class));
		// Admin should also be created
		verify(userRepository).save(any());
	}

}
