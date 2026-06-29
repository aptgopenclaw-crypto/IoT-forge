package com.taipei.iot.notification.job;

import com.taipei.iot.notification.repository.NotificationRepository;
import com.taipei.iot.setting.entity.SystemSettingEntity;
import com.taipei.iot.setting.enums.SettingKey;
import com.taipei.iot.setting.repository.SystemSettingRepository;
import com.taipei.iot.tenant.TenantEntity;
import com.taipei.iot.tenant.TenantRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
class NotificationPurgeJobTest {

	@Mock
	private NotificationRepository notificationRepository;

	@Mock
	private TenantRepository tenantRepository;

	@Mock
	private SystemSettingRepository systemSettingRepository;

	@InjectMocks
	private NotificationPurgeJob purgeJob;

	@Test
	void archiveOldNotifications_shouldArchiveForEachTenant() {
		TenantEntity tenant1 = new TenantEntity();
		tenant1.setTenantId("T1");
		tenant1.setEnabled(true);
		TenantEntity tenant2 = new TenantEntity();
		tenant2.setTenantId("T2");
		tenant2.setEnabled(true);
		when(tenantRepository.findByEnabledTrue()).thenReturn(List.of(tenant1, tenant2));

		SystemSettingEntity setting = SystemSettingEntity.builder()
			.settingKey("notification_retention_days")
			.settingValue("60")
			.build();
		when(systemSettingRepository.findBySettingKey("notification_retention_days")).thenReturn(Optional.of(setting));

		when(notificationRepository.archiveOldReadNotifications(eq("T1"), any(LocalDateTime.class),
				any(LocalDateTime.class)))
			.thenReturn(5);
		when(notificationRepository.archiveOldReadNotifications(eq("T2"), any(LocalDateTime.class),
				any(LocalDateTime.class)))
			.thenReturn(3);

		purgeJob.archiveOldNotifications();

		verify(notificationRepository).archiveOldReadNotifications(eq("T1"), any(LocalDateTime.class),
				any(LocalDateTime.class));
		verify(notificationRepository).archiveOldReadNotifications(eq("T2"), any(LocalDateTime.class),
				any(LocalDateTime.class));
	}

	@Test
	void archiveOldNotifications_shouldUseDefaultWhenSettingNotFound() {
		TenantEntity tenant = new TenantEntity();
		tenant.setTenantId("T1");
		tenant.setEnabled(true);
		when(tenantRepository.findByEnabledTrue()).thenReturn(List.of(tenant));
		when(systemSettingRepository.findBySettingKey("notification_retention_days")).thenReturn(Optional.empty());
		when(notificationRepository.archiveOldReadNotifications(anyString(), any(LocalDateTime.class),
				any(LocalDateTime.class)))
			.thenReturn(0);

		purgeJob.archiveOldNotifications();

		verify(notificationRepository).archiveOldReadNotifications(eq("T1"), any(LocalDateTime.class),
				any(LocalDateTime.class));
	}

	@Test
	void archiveOldNotifications_shouldHandleNoTenants() {
		when(tenantRepository.findByEnabledTrue()).thenReturn(List.of());

		purgeJob.archiveOldNotifications();

		verify(notificationRepository, never()).archiveOldReadNotifications(anyString(), any(), any());
	}

	@Test
	void getRetentionDays_shouldParseSettingValue() {
		SystemSettingEntity setting = SystemSettingEntity.builder()
			.settingKey("notification_retention_days")
			.settingValue("45")
			.build();
		when(systemSettingRepository.findBySettingKey("notification_retention_days")).thenReturn(Optional.of(setting));

		int days = purgeJob.getRetentionDays("T1");

		assertEquals(45, days);
	}

	@Test
	void getRetentionDays_shouldReturnDefaultForInvalidValue() {
		SystemSettingEntity setting = SystemSettingEntity.builder()
			.settingKey("notification_retention_days")
			.settingValue("abc")
			.build();
		when(systemSettingRepository.findBySettingKey("notification_retention_days")).thenReturn(Optional.of(setting));

		int days = purgeJob.getRetentionDays("T1");

		assertEquals(NotificationPurgeJob.DEFAULT_RETENTION_DAYS, days);
	}

	@Test
	void getRetentionDays_shouldReturnDefaultForZeroValue() {
		SystemSettingEntity setting = SystemSettingEntity.builder()
			.settingKey("notification_retention_days")
			.settingValue("0")
			.build();
		when(systemSettingRepository.findBySettingKey("notification_retention_days")).thenReturn(Optional.of(setting));

		int days = purgeJob.getRetentionDays("T1");

		assertEquals(NotificationPurgeJob.DEFAULT_RETENTION_DAYS, days);
	}

	@Test
	void defaultRetentionDays_shouldBe90() {
		assertEquals(90, NotificationPurgeJob.DEFAULT_RETENTION_DAYS);
	}

}
