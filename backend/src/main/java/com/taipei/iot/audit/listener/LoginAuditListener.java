package com.taipei.iot.audit.listener;

import com.taipei.iot.audit.entity.UserEventLogEntity;
import com.taipei.iot.audit.repository.UserEventLogRepository;
import com.taipei.iot.common.audit.enums.AuditEventType;
import com.taipei.iot.common.context.TenantContext;
import com.taipei.iot.common.event.LoginAuditEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 訂閱 {@link LoginAuditEvent} 並將登入/登出稽核記錄非同步寫入 {@code user_event_log}。
 *
 * <p>
 * 使用 {@code @EventListener}（而非 {@code @TransactionalEventListener}）： 登入流程本身不一定在
 * transaction 中執行，直接監聽可確保無論是否有外層交易都能寫入。 非同步寫入（auditExecutor）使登入回應不受 I/O 延遲影響。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LoginAuditListener {

	private final UserEventLogRepository userEventLogRepository;

	@Async("auditExecutor")
	@EventListener
	public void onLoginAudit(LoginAuditEvent event) {
		try {
			AuditEventType eventType = event.eventType();

			UserEventLogEntity entity = new UserEventLogEntity();
			entity.setTenantId(event.tenantId());
			entity.setUserId(event.userId());
			entity.setUsername(event.email());
			entity.setUserLabel(event.displayName());
			entity.setEmail(event.email());
			entity.setEventType(eventType.getValue());
			entity.setEventDesc(eventType.getCategory().getValue());
			entity.setApiEndpoint(event.apiEndpoint());
			entity.setErrorCode(eventType.errorCode());
			entity.setMessage(event.detail());
			entity.setIpAddress(event.ipAddress());
			entity.setUserAgent(event.userAgent());
			entity.setExecutionTime(0L);
			entity.setDeptId(event.deptId());
			entity.setCreateTime(LocalDateTime.now());

			// 登入時可能尚無 tenant context，使用 SYSTEM context 繞過 @Filter
			TenantContext.runInSystemContext(() -> userEventLogRepository.save(entity));
		}
		catch (Exception ex) {
			log.error("LoginAuditListener: failed to write audit log for event={} userId={}: {}", event.eventType(),
					event.userId(), ex.getMessage());
		}
	}

}
