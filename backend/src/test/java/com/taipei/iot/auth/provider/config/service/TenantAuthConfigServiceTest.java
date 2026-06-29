package com.taipei.iot.auth.provider.config.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taipei.iot.auth.provider.AuthType;
import com.taipei.iot.auth.provider.AuthenticationDispatcher;
import com.taipei.iot.auth.provider.config.dto.TenantAuthConfigRequest;
import com.taipei.iot.auth.provider.config.dto.TenantAuthConfigResponse;
import com.taipei.iot.auth.provider.config.entity.TenantAuthConfigEntity;
import com.taipei.iot.auth.provider.config.repository.TenantAuthConfigRepository;
import com.taipei.iot.auth.provider.config.service.impl.TenantAuthConfigServiceImpl;
import com.taipei.iot.auth.provider.crypto.AuthConfigEncryptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TenantAuthConfigServiceTest {

	@InjectMocks
	private TenantAuthConfigServiceImpl service;

	@Mock
	private TenantAuthConfigRepository repository;

	@Mock
	private AuthConfigEncryptor encryptor;

	@Mock
	private AuthenticationDispatcher dispatcher;

	@Spy
	private ObjectMapper objectMapper = new ObjectMapper();

	@Test
	void getByTenantId_noConfig_returnsDefaultLocal() {
		when(repository.findByTenantId("TENANT_A")).thenReturn(Optional.empty());

		TenantAuthConfigResponse response = service.getByTenantId("TENANT_A");

		assertNotNull(response);
		assertEquals("TENANT_A", response.getTenantId());
		assertEquals(AuthType.LOCAL, response.getAuthType());
		assertTrue(response.getEnabled());
		assertTrue(response.getFallbackLocal());
	}

	@Test
	void getByTenantId_existingConfig_returnsSanitized() {
		TenantAuthConfigEntity entity = TenantAuthConfigEntity.builder()
			.id(1L)
			.tenantId("TENANT_A")
			.authType(AuthType.LDAP)
			.enabled(true)
			.configJson("encrypted-json")
			.fallbackLocal(true)
			.createdAt(LocalDateTime.now())
			.updatedAt(LocalDateTime.now())
			.build();

		when(repository.findByTenantId("TENANT_A")).thenReturn(Optional.of(entity));
		when(encryptor.decrypt("encrypted-json"))
			.thenReturn("{\"url\":\"ldap://host\",\"bindPassword\":\"secret123\"}");

		TenantAuthConfigResponse response = service.getByTenantId("TENANT_A");

		assertNotNull(response);
		assertEquals(AuthType.LDAP, response.getAuthType());
		// Password should be sanitized
		assertEquals("***", response.getConfig().get("bindPassword"));
		assertEquals("ldap://host", response.getConfig().get("url"));
	}

	@Test
	void createOrUpdate_newConfig_createsEntity() {
		when(repository.findByTenantId("TENANT_A")).thenReturn(Optional.empty());
		when(encryptor.encrypt(any())).thenReturn("encrypted-result");
		when(repository.save(any())).thenAnswer(inv -> {
			TenantAuthConfigEntity e = inv.getArgument(0);
			e.setId(1L);
			e.setCreatedAt(LocalDateTime.now());
			e.setUpdatedAt(LocalDateTime.now());
			return e;
		});

		TenantAuthConfigRequest request = new TenantAuthConfigRequest();
		request.setAuthType(AuthType.LDAP);
		request.setConfig(Map.of("url", "ldap://host", "bindPassword", "secret"));
		request.setFallbackLocal(true);

		// Mock decrypt for the response building
		when(encryptor.decrypt("encrypted-result")).thenReturn("{\"url\":\"ldap://host\",\"bindPassword\":\"secret\"}");

		TenantAuthConfigResponse response = service.createOrUpdate("TENANT_A", request);

		assertNotNull(response);
		assertEquals(AuthType.LDAP, response.getAuthType());

		ArgumentCaptor<TenantAuthConfigEntity> captor = ArgumentCaptor.forClass(TenantAuthConfigEntity.class);
		verify(repository).save(captor.capture());
		assertEquals("TENANT_A", captor.getValue().getTenantId());
		assertEquals("encrypted-result", captor.getValue().getConfigJson());
	}

	@Test
	void createOrUpdate_localType_nullifiesConfig() {
		when(repository.findByTenantId("TENANT_A")).thenReturn(Optional.empty());
		when(repository.save(any())).thenAnswer(inv -> {
			TenantAuthConfigEntity e = inv.getArgument(0);
			e.setId(1L);
			e.setCreatedAt(LocalDateTime.now());
			e.setUpdatedAt(LocalDateTime.now());
			return e;
		});

		TenantAuthConfigRequest request = new TenantAuthConfigRequest();
		request.setAuthType(AuthType.LOCAL);
		request.setConfig(null);

		TenantAuthConfigResponse response = service.createOrUpdate("TENANT_A", request);

		assertNotNull(response);
		assertEquals(AuthType.LOCAL, response.getAuthType());

		ArgumentCaptor<TenantAuthConfigEntity> captor = ArgumentCaptor.forClass(TenantAuthConfigEntity.class);
		verify(repository).save(captor.capture());
		assertNull(captor.getValue().getConfigJson());
	}

	@Test
	void deleteByTenantId_callsRepository() {
		service.deleteByTenantId("TENANT_A");

		verify(repository).deleteByTenantId("TENANT_A");
	}

	@Test
	void testConnection_local_returnsTrue() {
		TenantAuthConfigRequest request = new TenantAuthConfigRequest();
		request.setAuthType(AuthType.LOCAL);

		boolean result = service.testConnection("TENANT_A", request);

		assertTrue(result);
	}

	@Test
	void testConnection_nonLocal_delegatesToDispatcher() {
		TenantAuthConfigRequest request = new TenantAuthConfigRequest();
		request.setAuthType(AuthType.LDAP);
		request.setConfig(Map.of("url", "ldap://host"));

		when(dispatcher.testConnection(eq(AuthType.LDAP), any())).thenReturn(true);

		boolean result = service.testConnection("TENANT_A", request);

		assertTrue(result);
		verify(dispatcher).testConnection(eq(AuthType.LDAP), any());
	}

}
