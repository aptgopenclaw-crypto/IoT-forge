package com.taipei.iot.audit.listener;

import com.taipei.iot.audit.async.AuditAsyncWriter;
import com.taipei.iot.audit.enums.AuditEventType;
import com.taipei.iot.common.dto.UserInfo;
import com.taipei.iot.common.event.VirusScanAuditEvent;
import com.taipei.iot.common.util.SecurityContextUtils;
import com.taipei.iot.tenant.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 將 {@link VirusScanAuditEvent} 翻譯為 {@code user_event_log} 紀錄，供 SIEM 撈取。
 *
 * <p>
 * 採用 Spring {@code @EventListener} 解耦：common 模組以
 * {@link org.springframework.context.ApplicationEventPublisher} 發送事件， 由本 listener 在 audit
 * 模組內訂閱並透過 {@link AuditAsyncWriter} 寫入。 如此 common 模組不需依賴 audit 模組即可達成可觀測性需求。
 *
 * <p>
 * [common v2 F-3] 對應「檔案掃毒結果寫入 audit_log」需求。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class VirusScanAuditListener {

	private final AuditAsyncWriter auditAsyncWriter;

	@EventListener
	public void onVirusScanAudit(VirusScanAuditEvent event) {
		try {
			AuditEventType eventType = switch (event.result()) {
				case INFECTED -> AuditEventType.FILE_VIRUS_INFECTED;
				case ERROR -> AuditEventType.FILE_SCAN_ERROR;
			};

			// 在事件發布的呼叫端執行緒（仍在 request scope 內）擷取 ThreadLocal 值，
			// 避免 @Async 切換執行緒後讀不到 tenant / security context。
			String tenantId = TenantContext.getCurrentTenantId();
			String userId = SecurityContextUtils.getCurrentUserId();
			String username = SecurityContextUtils.getCurrentUsername();
			UserInfo userInfo = SecurityContextUtils.getUserInfo();
			Long deptId = userInfo != null ? userInfo.getDeptId() : null;
			String impersonatedBy = TenantContext.getImpersonator();
			String uri = getRequestUri();
			String ip = getClientIp();
			String ua = getUserAgent();

			String message = String.format("result=%s; subDir=%s; relativePath=%s; originalFileName=%s; size=%d",
					event.result(), event.subDir(), event.relativePath(), event.originalFileName(), event.size());

			auditAsyncWriter.saveAsync(tenantId, userId, username, eventType.getValue(),
					eventType.getCategory().getValue(), uri, message, eventType.errorCode(), ip, ua, 0L, deptId,
					impersonatedBy);
		}
		catch (Exception ex) {
			// best-effort：審計失敗不影響掃毒主流程
			log.error("VirusScanAuditListener failed to record event: {}", ex.getMessage(), ex);
		}
	}

	private String getRequestUri() {
		HttpServletRequest req = getRequest();
		return req != null ? req.getRequestURI() : null;
	}

	private String getClientIp() {
		HttpServletRequest req = getRequest();
		return req != null ? req.getRemoteAddr() : null;
	}

	private String getUserAgent() {
		HttpServletRequest req = getRequest();
		return req != null ? req.getHeader("User-Agent") : null;
	}

	private HttpServletRequest getRequest() {
		ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
		return attrs != null ? attrs.getRequest() : null;
	}

}
