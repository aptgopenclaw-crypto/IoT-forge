package com.taipei.iot.notification.job;

import com.taipei.iot.notification.repository.NotificationRepository;
import com.taipei.iot.setting.enums.SettingKey;
import com.taipei.iot.setting.repository.SystemSettingRepository;
import com.taipei.iot.tenant.RunInSystemTenantContext;
import com.taipei.iot.tenant.TenantContext;
import com.taipei.iot.tenant.TenantEntity;
import com.taipei.iot.tenant.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationPurgeJob {

	private final NotificationRepository notificationRepository;

	private final TenantRepository tenantRepository;

	private final SystemSettingRepository systemSettingRepository;

	static final int DEFAULT_RETENTION_DAYS = Integer
		.parseInt(SettingKey.NOTIFICATION_RETENTION_DAYS.getDefaultValue());

	@Scheduled(cron = "0 30 2 * * ?")
	@Transactional
	@RunInSystemTenantContext
	public void archiveOldNotifications() {
		List<TenantEntity> tenants = tenantRepository.findByEnabledTrue();
		int totalArchived = 0;
		LocalDateTime now = LocalDateTime.now();

		for (TenantEntity tenant : tenants) {
			int retentionDays = getRetentionDays(tenant.getTenantId());
			LocalDateTime cutoff = now.minusDays(retentionDays);
			int archived = notificationRepository.archiveOldReadNotifications(tenant.getTenantId(), cutoff, now);
			if (archived > 0) {
				log.info("NotificationPurgeJob: tenant={} retentionDays={} archived={} cutoff={}", tenant.getTenantId(),
						retentionDays, archived, cutoff);
			}
			totalArchived += archived;
		}

		log.info("NotificationPurgeJob: completed — total archived={}", totalArchived);
	}

	int getRetentionDays(String tenantId) {
		TenantContext.setCurrentTenantId(tenantId);
		try {
			return systemSettingRepository.findBySettingKey(SettingKey.NOTIFICATION_RETENTION_DAYS.getKey())
				.map(e -> parseIntOrDefault(e.getSettingValue(), DEFAULT_RETENTION_DAYS))
				.orElse(DEFAULT_RETENTION_DAYS);
		}
		finally {
			TenantContext.setSystemContext();
		}
	}

	private static int parseIntOrDefault(String value, int defaultValue) {
		try {
			int parsed = Integer.parseInt(value);
			return parsed > 0 ? parsed : defaultValue;
		}
		catch (NumberFormatException e) {
			return defaultValue;
		}
	}

}
