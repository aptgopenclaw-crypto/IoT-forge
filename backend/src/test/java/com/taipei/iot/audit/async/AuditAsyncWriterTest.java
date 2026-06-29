package com.taipei.iot.audit.async;

import com.taipei.iot.audit.entity.UserEventLogEntity;
import com.taipei.iot.audit.repository.UserEventLogRepository;
import com.taipei.iot.auth.entity.UserEntity;
import com.taipei.iot.auth.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditAsyncWriterTest {

	@Mock
	private UserEventLogRepository userEventLogRepository;

	@Mock
	private UserRepository userRepository;

	@InjectMocks
	private AuditAsyncWriter auditAsyncWriter;

	@Test
	void saveAsync_shouldPersistEntity() {
		UserEntity user = UserEntity.builder()
			.userId("u-admin-001")
			.displayName("Admin User")
			.email("admin@example.com")
			.passwordHash("hash")
			.build();
		when(userRepository.findById("u-admin-001")).thenReturn(Optional.of(user));
		when(userEventLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

		auditAsyncWriter.saveAsync("tenant-100", "u-admin-001", "admin@test.com", "LOGIN", "USER_AUTH",
				"/v1/noauth/token", "{}", "00000", "192.168.1.100", "Mozilla/5.0", 120L, 100L, null);

		ArgumentCaptor<UserEventLogEntity> captor = ArgumentCaptor.forClass(UserEventLogEntity.class);
		verify(userEventLogRepository).save(captor.capture());

		UserEventLogEntity saved = captor.getValue();
		assertEquals("tenant-100", saved.getTenantId());
		assertEquals("u-admin-001", saved.getUserId());
		assertEquals("admin@example.com", saved.getUsername());
		assertEquals("LOGIN", saved.getEventType());
		assertEquals("USER_AUTH", saved.getEventDesc());
		assertEquals("/v1/noauth/token", saved.getApiEndpoint());
		assertEquals("00000", saved.getErrorCode());
		assertEquals("192.168.1.100", saved.getIpAddress());
		assertEquals("Mozilla/5.0", saved.getUserAgent());
		assertEquals(120L, saved.getExecutionTime());
		assertEquals(100L, saved.getDeptId());
		assertEquals("Admin User", saved.getUserLabel());
		assertEquals("admin@example.com", saved.getEmail());
		assertNotNull(saved.getCreateTime());
	}

	@Test
	void saveAsync_shouldNotThrowOnDbFailure() {
		when(userEventLogRepository.save(any())).thenThrow(new RuntimeException("DB connection lost"));

		// best-effort: should not throw
		assertDoesNotThrow(() -> auditAsyncWriter.saveAsync("tenant-100", "u-admin-001", "admin@test.com", "LOGIN",
				"USER_AUTH", "/v1/noauth/token", "{}", "00000", "192.168.1.100", "Mozilla/5.0", 120L, 100L, null));
	}

	@Test
	void saveAsync_shouldHandleNullTenantId() {
		when(userEventLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

		auditAsyncWriter.saveAsync(null, "u-super-001", "super@test.com", "CREATE_MENU", "SYSTEM", "/v1/auth/menus",
				"{}", "00000", "10.0.0.1", "Mozilla/5.0", 80L, null, null);

		ArgumentCaptor<UserEventLogEntity> captor = ArgumentCaptor.forClass(UserEventLogEntity.class);
		verify(userEventLogRepository).save(captor.capture());
		assertNull(captor.getValue().getTenantId());
	}

}
