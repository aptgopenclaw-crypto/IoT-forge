package com.taipei.iot.audit.job;

import com.taipei.iot.audit.repository.UserEventLogRepository;
import com.taipei.iot.setting.entity.SystemSettingEntity;
import com.taipei.iot.setting.repository.SystemSettingRepository;
import com.taipei.iot.tenant.TenantEntity;
import com.taipei.iot.tenant.TenantRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditPurgeJobTest {

	@Mock
	private UserEventLogRepository userEventLogRepository;

	@Mock
	private TenantRepository tenantRepository;

	@Mock
	private SystemSettingRepository systemSettingRepository;

	@InjectMocks
	private AuditPurgeJob auditPurgeJob;

	@AfterEach
	void tearDown() {
		com.taipei.iot.tenant.TenantContext.clear();
	}

	@Test
	void purgeOldAuditLogs_shouldUsePerTenantRetentionDays() {
		TenantEntity tenantA = new TenantEntity();
		tenantA.setTenantId("TENANT_A");
		TenantEntity tenantB = new TenantEntity();
		tenantB.setTenantId("TENANT_B");
		when(tenantRepository.findByEnabledTrue()).thenReturn(List.of(tenantA, tenantB));

		// TENANT_A: custom 90 days
		SystemSettingEntity settingA = SystemSettingEntity.builder()
			.settingKey("audit_retention_days")
			.settingValue("90")
			.build();
		// TENANT_B: no setting → use default 180
		when(systemSettingRepository.findBySettingKey("audit_retention_days")).thenReturn(Optional.of(settingA)) // first
																													// call
																													// for
																													// TENANT_A
			.thenReturn(Optional.empty()); // second call for TENANT_B

		when(userEventLogRepository.deleteByTenantIdAndCreateTimeBefore(anyString(), any())).thenReturn(50);
		when(userEventLogRepository.deleteByTenantIdNullAndCreateTimeBefore(any())).thenReturn(2);

		auditPurgeJob.purgeOldAuditLogs();

		// Verify TENANT_A: cutoff ~90 days ago
		ArgumentCaptor<LocalDateTime> cutoffCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
		verify(userEventLogRepository).deleteByTenantIdAndCreateTimeBefore(eq("TENANT_A"), cutoffCaptor.capture());
		LocalDateTime cutoffA = cutoffCaptor.getValue();
		assertTrue(cutoffA.isAfter(LocalDateTime.now().minusDays(91)));
		assertTrue(cutoffA.isBefore(LocalDateTime.now().minusDays(89)));

		// Verify TENANT_B: cutoff ~180 days ago (default)
		verify(userEventLogRepository).deleteByTenantIdAndCreateTimeBefore(eq("TENANT_B"), cutoffCaptor.capture());
		LocalDateTime cutoffB = cutoffCaptor.getValue();
		assertTrue(cutoffB.isAfter(LocalDateTime.now().minusDays(181)));
		assertTrue(cutoffB.isBefore(LocalDateTime.now().minusDays(179)));

		// Verify orphan cleanup
		verify(userEventLogRepository).deleteByTenantIdNullAndCreateTimeBefore(any());
	}

	@Test
	void purgeOldAuditLogs_shouldHandleNoTenants() {
		when(tenantRepository.findByEnabledTrue()).thenReturn(List.of());
		when(userEventLogRepository.deleteByTenantIdNullAndCreateTimeBefore(any())).thenReturn(0);

		assertDoesNotThrow(() -> auditPurgeJob.purgeOldAuditLogs());
		verify(userEventLogRepository, never()).deleteByTenantIdAndCreateTimeBefore(anyString(), any());
		verify(userEventLogRepository).deleteByTenantIdNullAndCreateTimeBefore(any());
	}

	@Test
	void purgeOldAuditLogs_shouldUseDefaultForInvalidSettingValue() {
		TenantEntity tenant = new TenantEntity();
		tenant.setTenantId("TENANT_X");
		when(tenantRepository.findByEnabledTrue()).thenReturn(List.of(tenant));

		SystemSettingEntity badSetting = SystemSettingEntity.builder()
			.settingKey("audit_retention_days")
			.settingValue("not-a-number")
			.build();
		when(systemSettingRepository.findBySettingKey("audit_retention_days")).thenReturn(Optional.of(badSetting));

		when(userEventLogRepository.deleteByTenantIdAndCreateTimeBefore(anyString(), any())).thenReturn(0);
		when(userEventLogRepository.deleteByTenantIdNullAndCreateTimeBefore(any())).thenReturn(0);

		auditPurgeJob.purgeOldAuditLogs();

		// Should fallback to default (180 days)
		ArgumentCaptor<LocalDateTime> cutoffCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
		verify(userEventLogRepository).deleteByTenantIdAndCreateTimeBefore(eq("TENANT_X"), cutoffCaptor.capture());
		LocalDateTime cutoff = cutoffCaptor.getValue();
		assertTrue(cutoff.isAfter(LocalDateTime.now().minusDays(181)));
		assertTrue(cutoff.isBefore(LocalDateTime.now().minusDays(179)));
	}

}
