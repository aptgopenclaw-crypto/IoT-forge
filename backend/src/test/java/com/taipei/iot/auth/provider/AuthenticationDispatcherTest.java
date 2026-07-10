package com.taipei.iot.auth.provider;

import com.taipei.iot.common.enums.AuthType;
import com.taipei.iot.auth.provider.config.entity.TenantAuthConfigEntity;
import com.taipei.iot.auth.provider.config.repository.TenantAuthConfigRepository;
import com.taipei.iot.auth.provider.crypto.AuthConfigEncryptor;
import com.taipei.iot.user.repository.UserRepository;
import com.taipei.iot.common.enums.ErrorCode;
import com.taipei.iot.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthenticationDispatcherTest {

	private AuthenticationDispatcher dispatcher;

	@Mock
	private AuthenticationProvider localProvider;

	@Mock
	private TenantAuthConfigRepository configRepository;

	@Mock
	private AuthConfigEncryptor encryptor;

	@Mock
	private UserRepository userRepository;

	@BeforeEach
	void setUp() {
		when(localProvider.getType()).thenReturn(AuthType.LOCAL);
		dispatcher = new AuthenticationDispatcher(List.of(localProvider), configRepository, encryptor, userRepository);
	}

	@Test
	void dispatch_noConfig_fallbackToLocal() {
		AuthenticationRequest request = AuthenticationRequest.builder()
			.identifier("test@example.com")
			.credential("password")
			.tenantId("TENANT_A")
			.build();

		when(configRepository.findByTenantId("TENANT_A")).thenReturn(Optional.empty());
		when(localProvider.authenticate(any(), isNull()))
			.thenReturn(AuthenticationResult.builder().localUserId("user-1").email("test@example.com").build());

		AuthenticationResult result = dispatcher.dispatch(request);

		assertNotNull(result);
		assertEquals("user-1", result.getLocalUserId());
		verify(localProvider).authenticate(any(), isNull());
	}

	@Test
	void dispatch_configDisabled_fallbackToLocal() {
		AuthenticationRequest request = AuthenticationRequest.builder()
			.identifier("test@example.com")
			.credential("password")
			.tenantId("TENANT_A")
			.build();

		TenantAuthConfigEntity config = TenantAuthConfigEntity.builder()
			.tenantId("TENANT_A")
			.authType(AuthType.LDAP)
			.enabled(false)
			.build();
		when(configRepository.findByTenantId("TENANT_A")).thenReturn(Optional.of(config));
		when(localProvider.authenticate(any(), isNull()))
			.thenReturn(AuthenticationResult.builder().localUserId("user-1").email("test@example.com").build());

		AuthenticationResult result = dispatcher.dispatch(request);

		assertNotNull(result);
		assertEquals("user-1", result.getLocalUserId());
	}

	@Test
	void dispatch_configEnabledLocal_routesToLocal() {
		AuthenticationRequest request = AuthenticationRequest.builder()
			.identifier("test@example.com")
			.credential("password")
			.tenantId("TENANT_A")
			.build();

		TenantAuthConfigEntity config = TenantAuthConfigEntity.builder()
			.tenantId("TENANT_A")
			.authType(AuthType.LOCAL)
			.enabled(true)
			.configJson(null)
			.build();
		when(configRepository.findByTenantId("TENANT_A")).thenReturn(Optional.of(config));
		when(localProvider.authenticate(any(), isNull()))
			.thenReturn(AuthenticationResult.builder().localUserId("user-1").email("test@example.com").build());

		AuthenticationResult result = dispatcher.dispatch(request);

		assertNotNull(result);
		verify(localProvider).authenticate(any(), isNull());
	}

	@Test
	void dispatch_noTenantId_fallbackToLocal() {
		AuthenticationRequest request = AuthenticationRequest.builder()
			.identifier("test@example.com")
			.credential("password")
			.build();

		when(localProvider.authenticate(any(), isNull()))
			.thenReturn(AuthenticationResult.builder().localUserId("user-1").email("test@example.com").build());

		AuthenticationResult result = dispatcher.dispatch(request);

		assertNotNull(result);
		verify(configRepository, never()).findByTenantId(any());
	}

	@Test
	void dispatch_unsupportedAuthType_throwsException() {
		AuthenticationRequest request = AuthenticationRequest.builder()
			.identifier("test@example.com")
			.credential("password")
			.tenantId("TENANT_A")
			.build();

		TenantAuthConfigEntity config = TenantAuthConfigEntity.builder()
			.tenantId("TENANT_A")
			.authType(AuthType.LDAP) // No LDAP provider registered
			.enabled(true)
			.build();
		when(configRepository.findByTenantId("TENANT_A")).thenReturn(Optional.of(config));

		assertThrows(BusinessException.class, () -> dispatcher.dispatch(request));
	}

	@Test
	void dispatch_externalProviderFails_fallbackLocal_enabled() {
		// Register a mock LDAP provider
		AuthenticationProvider ldapProvider = mock(AuthenticationProvider.class);
		when(ldapProvider.getType()).thenReturn(AuthType.LDAP);
		dispatcher = new AuthenticationDispatcher(List.of(localProvider, ldapProvider), configRepository, encryptor,
				userRepository);

		AuthenticationRequest request = AuthenticationRequest.builder()
			.identifier("test@example.com")
			.credential("password")
			.tenantId("TENANT_A")
			.build();

		TenantAuthConfigEntity config = TenantAuthConfigEntity.builder()
			.tenantId("TENANT_A")
			.authType(AuthType.LDAP)
			.enabled(true)
			.fallbackLocal(true)
			.configJson("encrypted-config")
			.build();
		when(configRepository.findByTenantId("TENANT_A")).thenReturn(Optional.of(config));
		when(encryptor.decrypt("encrypted-config")).thenReturn("{\"url\":\"ldap://host\"}");
		when(ldapProvider.authenticate(any(), eq("{\"url\":\"ldap://host\"}")))
			.thenThrow(new BusinessException(ErrorCode.LDAP_SERVICE_UNAVAILABLE));
		when(localProvider.authenticate(any(), isNull()))
			.thenReturn(AuthenticationResult.builder().localUserId("user-1").email("test@example.com").build());

		AuthenticationResult result = dispatcher.dispatch(request);

		assertNotNull(result);
		assertEquals("user-1", result.getLocalUserId());
		verify(ldapProvider).authenticate(any(), any());
		verify(localProvider).authenticate(any(), isNull());
	}

	@Test
	void dispatch_externalProviderFails_credentialError_doesNotFallback() {
		// Credential errors (LOGIN_FAIL, LDAP_AUTH_FAILED, etc.) must NEVER trigger
		// fallback to LOCAL, even when fallbackLocal=true.
		AuthenticationProvider ldapProvider = mock(AuthenticationProvider.class);
		when(ldapProvider.getType()).thenReturn(AuthType.LDAP);
		dispatcher = new AuthenticationDispatcher(List.of(localProvider, ldapProvider), configRepository, encryptor,
				userRepository);

		AuthenticationRequest request = AuthenticationRequest.builder()
			.identifier("test@example.com")
			.credential("password")
			.tenantId("TENANT_A")
			.build();

		TenantAuthConfigEntity config = TenantAuthConfigEntity.builder()
			.tenantId("TENANT_A")
			.authType(AuthType.LDAP)
			.enabled(true)
			.fallbackLocal(true)
			.configJson("encrypted-config")
			.build();
		when(configRepository.findByTenantId("TENANT_A")).thenReturn(Optional.of(config));
		when(encryptor.decrypt("encrypted-config")).thenReturn("{\"url\":\"ldap://host\"}");
		when(ldapProvider.authenticate(any(), eq("{\"url\":\"ldap://host\"}")))
			.thenThrow(new BusinessException(ErrorCode.LOGIN_FAIL));

		assertThrows(BusinessException.class, () -> dispatcher.dispatch(request));

		verify(ldapProvider).authenticate(any(), any());
		verify(localProvider, never()).authenticate(any(), any());
	}

	@Test
	void testConnection_delegatesToProvider() {
		when(localProvider.testConnection(any())).thenReturn(true);

		boolean result = dispatcher.testConnection(AuthType.LOCAL, "{}");

		assertTrue(result);
		verify(localProvider).testConnection("{}");
	}

}
