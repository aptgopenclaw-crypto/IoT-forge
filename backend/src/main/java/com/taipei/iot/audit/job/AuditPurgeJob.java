package com.taipei.iot.audit.job;

import com.taipei.iot.audit.repository.UserEventLogRepository;
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
public class AuditPurgeJob {

	private final UserEventLogRepository userEventLogRepository;

	private final TenantRepository tenantRepository;

	private final SystemSettingRepository systemSettingRepository;

	static final int DEFAULT_RETENTION_DAYS = Integer.parseInt(SettingKey.AUDIT_RETENTION_DAYS.getDefaultValue());

	@Scheduled(cron = "0 0 2 * * ?")
	@Transactional
	@RunInSystemTenantContext
	public void purgeOldAuditLogs() {
		// [Tenant v2 T-13] @RunInSystemTenantContext 包整段；本 method 內各 tenant 迭代時
		// 仍會用 setCurrentTenantId/setSystemContext 切換以讀取 per-tenant 設定，但離開
		// 本 method 時 aspect 會把 context 還原至呼叫端原本的狀態（排程觸發時為 null）。
		List<TenantEntity> tenants = tenantRepository.findByEnabledTrue();
		int totalDeleted = 0;

		for (TenantEntity tenant : tenants) {
			int retentionDays = getRetentionDays(tenant.getTenantId());
			LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);
			int deleted = userEventLogRepository.deleteByTenantIdAndCreateTimeBefore(tenant.getTenantId(), cutoff);
			if (deleted > 0) {
				log.info("AuditPurgeJob: tenant={} retentionDays={} deleted={} cutoff={}", tenant.getTenantId(),
						retentionDays, deleted, cutoff);
			}
			totalDeleted += deleted;
		}

		// 清除無租戶的孤立紀錄（保留預設天數）
		LocalDateTime defaultCutoff = LocalDateTime.now().minusDays(DEFAULT_RETENTION_DAYS);
		int orphanDeleted = userEventLogRepository.deleteByTenantIdNullAndCreateTimeBefore(defaultCutoff);

		log.info("AuditPurgeJob: completed — total deleted={} (orphan={})", totalDeleted + orphanDeleted,
				orphanDeleted);
	}

	private int getRetentionDays(String tenantId) {
		TenantContext.setCurrentTenantId(tenantId);
		try {
			return systemSettingRepository.findBySettingKey(SettingKey.AUDIT_RETENTION_DAYS.getKey())
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
