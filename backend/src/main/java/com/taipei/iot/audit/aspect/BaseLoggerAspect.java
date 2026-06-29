package com.taipei.iot.audit.aspect;

import com.taipei.iot.audit.annotation.AuditEvent;
import com.taipei.iot.audit.async.AuditAsyncWriter;
import com.taipei.iot.audit.enums.AuditEventType;
import com.taipei.iot.audit.util.PayloadSanitizer;
import com.taipei.iot.common.exception.BusinessException;
import com.taipei.iot.common.dto.UserInfo;
import com.taipei.iot.common.util.SecurityContextUtils;
import com.taipei.iot.tenant.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Aspect
@Component
@RequiredArgsConstructor
public class BaseLoggerAspect {

	private final AuditAsyncWriter auditAsyncWriter;

	@Around("@annotation(auditEvent)")
	public Object logApiCall(ProceedingJoinPoint pjp, AuditEvent auditEvent) throws Throwable {
		long start = System.currentTimeMillis();
		String errorCode = "00000";
		Object result = null;

		// ThreadLocal values must be captured on the main thread
		String tenantId = TenantContext.getCurrentTenantId();
		String userId = SecurityContextUtils.getCurrentUserId();
		String username = SecurityContextUtils.getCurrentUsername();
		UserInfo userInfo = SecurityContextUtils.getUserInfo();
		Long deptId = userInfo != null ? userInfo.getDeptId() : null;
		// [Phase B] 代操者 userId（SUPER_ADMIN 在 tenant context 下才有值）
		String impersonatedBy = TenantContext.getImpersonator();
		String uri = getRequestUri();
		String ip = getClientIp();
		String ua = getUserAgent();

		try {
			result = pjp.proceed();
		}
		catch (BusinessException e) {
			errorCode = e.getErrorCode().getCode();
			throw e;
		}
		catch (Exception e) {
			errorCode = "99999";
			throw e;
		}
		finally {
			long executionTime = System.currentTimeMillis() - start;
			String payload = PayloadSanitizer.sanitize(pjp.getArgs());
			AuditEventType eventType = auditEvent.value();

			auditAsyncWriter.saveAsync(tenantId, userId, username, eventType.getValue(),
					eventType.getCategory().getValue(), uri, payload, errorCode, ip, ua, executionTime, deptId,
					impersonatedBy);
		}
		return result;
	}

	private String getRequestUri() {
		HttpServletRequest req = getRequest();
		return req != null ? req.getRequestURI() : null;
	}

	private String getClientIp() {
		HttpServletRequest req = getRequest();
		if (req == null)
			return null;
		// 直接使用 TCP 連線來源 IP，不信任可偽造的 X-Forwarded-For header。
		// 與 RateLimitInterceptor 保持一致策略。
		return req.getRemoteAddr();
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
