package com.taipei.iot.audit.async;

import com.taipei.iot.audit.entity.UserEventLogEntity;
import com.taipei.iot.audit.repository.UserEventLogRepository;
import com.taipei.iot.common.audit.port.UserDisplayInfo;
import com.taipei.iot.common.audit.port.UserDisplayInfoProvider;
import com.taipei.iot.common.tenant.RunInSystemTenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditAsyncWriter {

	private final UserEventLogRepository userEventLogRepository;

	private final UserDisplayInfoProvider userDisplayInfoProvider;

	@Async("auditExecutor")
	@RunInSystemTenantContext
	public void saveAsync(String tenantId, String userId, String username, String eventType, String eventDesc,
			String apiEndpoint, String payload, String errorCode, String ipAddress, String userAgent,
			long executionTime, Long deptId, String impersonatedBy) {
		// [Tenant v2 T-13] @RunInSystemTenantContext 已把整段包進 SYSTEM context；
		// @Async 在新執行緒執行（ThreadLocal 不繼承），aspect 也能正確初始化並 cleanup。
		try {
			// 查詢使用者 displayName 與 email（best-effort，透過 Port 解除 audit→auth 直接依賴）
			String userLabel = null;
			String email = null;
			if (userId != null) {
				UserDisplayInfo info = userDisplayInfoProvider.findByUserId(userId).orElse(null);
				if (info != null) {
					userLabel = info.displayName();
					email = info.email();
				}
			}

			UserEventLogEntity entity = new UserEventLogEntity();
			entity.setTenantId(tenantId);
			entity.setUserId(userId);
			entity.setUsername(email != null ? email : username);
			entity.setUserLabel(userLabel);
			entity.setEmail(email);
			entity.setEventType(eventType);
			entity.setEventDesc(eventDesc);
			entity.setApiEndpoint(apiEndpoint);
			entity.setPayload(payload);
			entity.setErrorCode(errorCode);
			entity.setIpAddress(ipAddress);
			entity.setUserAgent(userAgent);
			entity.setExecutionTime(executionTime);
			entity.setDeptId(deptId);
			entity.setImpersonatedBy(impersonatedBy);
			entity.setCreateTime(LocalDateTime.now());

			userEventLogRepository.save(entity);
		}
		catch (Exception ex) {
			// TODO: 透過 ELK 關鍵字告警規則監控此 log，觸發 email 通知
			// Alert condition: message contains "Audit async write failed"
			log.error("Audit async write failed: {} {} {}", eventType, apiEndpoint, ex.getMessage());
			// best-effort: do not rethrow, do not affect business logic
		}
	}

}
