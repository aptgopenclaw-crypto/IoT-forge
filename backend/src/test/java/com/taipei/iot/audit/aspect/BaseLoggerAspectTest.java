package com.taipei.iot.audit.aspect;

import com.taipei.iot.audit.annotation.AuditEvent;
import com.taipei.iot.audit.async.AuditAsyncWriter;
import com.taipei.iot.audit.enums.AuditEventType;
import com.taipei.iot.common.exception.BusinessException;
import com.taipei.iot.common.enums.ErrorCode;
import com.taipei.iot.tenant.TenantContext;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BaseLoggerAspectTest {

	@Mock
	private AuditAsyncWriter auditAsyncWriter;

	@InjectMocks
	private BaseLoggerAspect baseLoggerAspect;

	@Mock
	private ProceedingJoinPoint pjp;

	@Mock
	private AuditEvent auditEvent;

	@BeforeEach
	void setUp() {
		// Set up SecurityContext
		var auth = new UsernamePasswordAuthenticationToken("u-admin-001", null, Collections.emptyList());
		SecurityContextHolder.getContext().setAuthentication(auth);

		// Set up TenantContext
		TenantContext.setCurrentTenantId("tenant-100");

		// Set up mock request
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setRequestURI("/v1/auth/user");
		request.setRemoteAddr("192.168.1.100");
		request.addHeader("User-Agent", "TestAgent/1.0");
		RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
	}

	@AfterEach
	void tearDown() {
		SecurityContextHolder.clearContext();
		TenantContext.clear();
		RequestContextHolder.resetRequestAttributes();
	}

	@Test
	void logApiCall_shouldDelegateToAsyncWriter() throws Throwable {
		when(auditEvent.value()).thenReturn(AuditEventType.CREATE_USER);
		when(pjp.proceed()).thenReturn("result");
		when(pjp.getArgs()).thenReturn(new Object[] { "arg1" });

		Object result = baseLoggerAspect.logApiCall(pjp, auditEvent);

		assertEquals("result", result);
		verify(auditAsyncWriter).saveAsync(eq("tenant-100"), eq("u-admin-001"), eq("u-admin-001"), eq("CREATE_USER"),
				eq("ACCOUNT"), eq("/v1/auth/user"), anyString(), eq("00000"), eq("192.168.1.100"), eq("TestAgent/1.0"),
				anyLong(), isNull(), isNull());
	}

	@Test
	void logApiCall_shouldCaptureErrorCodeOnBusinessException() throws Throwable {
		when(auditEvent.value()).thenReturn(AuditEventType.LOGOUT);
		when(pjp.proceed()).thenThrow(new BusinessException(ErrorCode.LOGIN_FAIL));
		when(pjp.getArgs()).thenReturn(new Object[] {});

		assertThrows(BusinessException.class, () -> baseLoggerAspect.logApiCall(pjp, auditEvent));

		verify(auditAsyncWriter).saveAsync(anyString(), anyString(), anyString(), eq("LOGOUT"), eq("USER_AUTH"),
				anyString(), any(), eq("10013"), anyString(), anyString(), anyLong(), isNull(), isNull());
	}

	@Test
	void logApiCall_shouldUseDefaultErrorCodeOnGenericException() throws Throwable {
		when(auditEvent.value()).thenReturn(AuditEventType.CREATE_USER);
		when(pjp.proceed()).thenThrow(new RuntimeException("unexpected"));
		when(pjp.getArgs()).thenReturn(new Object[] {});

		assertThrows(RuntimeException.class, () -> baseLoggerAspect.logApiCall(pjp, auditEvent));

		verify(auditAsyncWriter).saveAsync(anyString(), anyString(), anyString(), eq("CREATE_USER"), eq("ACCOUNT"),
				anyString(), any(), eq("99999"), anyString(), anyString(), anyLong(), isNull(), isNull());
	}

	@Test
	void logApiCall_shouldUseRemoteAddrIgnoringXForwardedFor() throws Throwable {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setRequestURI("/v1/auth/user");
		request.setRemoteAddr("192.168.1.100");
		request.addHeader("X-Forwarded-For", "10.0.0.1, 172.16.0.1");
		request.addHeader("User-Agent", "TestAgent/1.0");
		RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

		when(auditEvent.value()).thenReturn(AuditEventType.CREATE_USER);
		when(pjp.proceed()).thenReturn("ok");
		when(pjp.getArgs()).thenReturn(new Object[] {});

		baseLoggerAspect.logApiCall(pjp, auditEvent);

		verify(auditAsyncWriter).saveAsync(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(),
				any(), anyString(), eq("192.168.1.100"), anyString(), anyLong(), isNull(), isNull());
	}

}
