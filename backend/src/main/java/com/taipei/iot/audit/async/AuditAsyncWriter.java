package com.taipei.iot.audit.async;

import com.taipei.iot.audit.entity.UserEventLogEntity;
import com.taipei.iot.audit.repository.UserEventLogRepository;
import com.taipei.iot.auth.entity.UserEntity;
import com.taipei.iot.auth.repository.UserRepository;
import com.taipei.iot.tenant.RunInSystemTenantContext;
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

	private final UserRepository userRepository;

	@Async("auditExecutor")
	@RunInSystemTenantContext
	public void saveAsync(String tenantId, String userId, String username, String eventType, String eventDesc,
			String apiEndpoint, String payload, String errorCode, String ipAddress, String userAgent,
			long executionTime, Long deptId, String impersonatedBy) {
		// [Tenant v2 T-13] @RunInSystemTenantContext 已把整段包進 SYSTEM context；
		// @Async 在新執行緒執行（ThreadLocal 不繼承），aspect 也能正確初始化並 cleanup。
		try {
			// 查詢使用者 displayName 與 email（best-effort）
			String userLabel = null;
			String email = null;
			if (userId != null) {
				UserEntity user = userRepository.findById(userId).orElse(null);
				if (user != null) {
					userLabel = user.getDisplayName();
					email = user.getEmail();
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
