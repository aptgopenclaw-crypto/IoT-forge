package com.taipei.iot.common.util;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.taipei.iot.common.dto.UserInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SecurityContextUtilsTest {

	private ListAppender<ILoggingEvent> appender;

	private Logger utilLogger;

	@BeforeEach
	void attachAppender() {
		utilLogger = (Logger) LoggerFactory.getLogger(SecurityContextUtils.class);
		appender = new ListAppender<>();
		appender.start();
		utilLogger.addAppender(appender);
		SecurityContextUtils.resetOffRequestWarnedForTest();
	}

	@AfterEach
	void clearContext() {
		SecurityContextHolder.clearContext();
		RequestContextHolder.resetRequestAttributes();
		utilLogger.detachAppender(appender);
	}

	// ─── getCurrentUserId ─────────────────────────────────────────────────────

	@Test
	void getCurrentUserId_whenAuthenticated_returnsUserId() {
		setAuthentication("user-001", null);

		assertEquals("user-001", SecurityContextUtils.getCurrentUserId());
	}

	@Test
	void getCurrentUserId_whenUnauthenticated_returnsNull() {
		SecurityContextHolder.clearContext();

		assertNull(SecurityContextUtils.getCurrentUserId());
	}

	@Test
	void getCurrentUserId_whenAnonymous_returnsNull() {
		setAnonymousAuthentication();

		assertNull(SecurityContextUtils.getCurrentUserId(), "anonymousUser 不應被視為有效的已登入使用者");
	}

	// ─── getCurrentUsername ───────────────────────────────────────────────────

	@Test
	void getCurrentUsername_whenAuthenticated_returnsUsername() {
		setAuthentication("user-001", null);

		// UsernamePasswordAuthenticationToken.getName() 回傳 principal.toString() 即 userId
		assertEquals("user-001", SecurityContextUtils.getCurrentUsername());
	}

	@Test
	void getCurrentUsername_whenAnonymous_returnsNull() {
		setAnonymousAuthentication();

		assertNull(SecurityContextUtils.getCurrentUsername());
	}

	// ─── getUserInfo ──────────────────────────────────────────────────────────

	@Test
	void getUserInfo_withFullDetails_returnsCompleteUserInfo() {
		Map<String, Object> details = new HashMap<>();
		details.put(JwtClaimKeys.TENANT_ID, "tenant-99");
		details.put(JwtClaimKeys.DEPT_ID, 5L);
		details.put(JwtClaimKeys.DATA_SCOPE, "DEPT");
		setAuthentication("user-001", details);

		UserInfo info = SecurityContextUtils.getUserInfo();

		assertNotNull(info);
		assertEquals("user-001", info.getUserId());
		assertEquals("user-001", info.getUsername()); // getName() 回傳 principal，與 userId
														// 相同
		assertEquals("tenant-99", info.getTenantId());
		assertEquals(5L, info.getDeptId());
		assertEquals("DEPT", info.getDataScope());
	}

	@Test
	void getUserInfo_deptIdAsString_parsedToLong() {
		Map<String, Object> details = new HashMap<>();
		details.put(JwtClaimKeys.DEPT_ID, "12");
		setAuthentication("user-002", details);

		UserInfo info = SecurityContextUtils.getUserInfo();

		assertNotNull(info);
		assertEquals(12L, info.getDeptId());
	}

	@Test
	void getUserInfo_deptIdInvalidString_deptIdIsNull() {
		Map<String, Object> details = new HashMap<>();
		details.put(JwtClaimKeys.DEPT_ID, "not-a-number");
		setAuthentication("user-003", details);

		UserInfo info = SecurityContextUtils.getUserInfo();

		assertNotNull(info);
		assertNull(info.getDeptId(), "無法解析的 deptId 應回傳 null 而非拋出例外");
	}

	@Test
	void getUserInfo_withoutMapDetails_returnsBasicInfo() {
		UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken("user-004", null,
				List.of(new SimpleGrantedAuthority("ROLE_USER")));
		auth.setDetails("plain-string-detail");
		SecurityContextHolder.getContext().setAuthentication(auth);

		UserInfo info = SecurityContextUtils.getUserInfo();

		assertNotNull(info);
		assertEquals("user-004", info.getUserId());
		assertEquals("user-004", info.getUsername());
		assertNull(info.getTenantId());
		assertNull(info.getDeptId());
	}

	@Test
	void getUserInfo_whenAnonymous_returnsNull() {
		setAnonymousAuthentication();

		assertNull(SecurityContextUtils.getUserInfo());
	}

	@Test
	void getUserInfo_whenUnauthenticated_returnsNull() {
		SecurityContextHolder.clearContext();

		assertNull(SecurityContextUtils.getUserInfo());
	}

	// ─── hasAnyAuthority ───────────────────────────────────────────────────

	@Test
	void hasAnyAuthority_whenMatches_returnsTrue() {
		UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken("user-001", null,
				List.of(new SimpleGrantedAuthority("ROLE_ADMIN"), new SimpleGrantedAuthority("AUDIT_LIST")));
		SecurityContextHolder.getContext().setAuthentication(auth);

		assertTrue(SecurityContextUtils.hasAnyAuthority("ROLE_ADMIN", "ROLE_SUPER_ADMIN"));
	}

	@Test
	void hasAnyAuthority_whenNoMatch_returnsFalse() {
		UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken("user-001", null,
				List.of(new SimpleGrantedAuthority("ROLE_USER")));
		SecurityContextHolder.getContext().setAuthentication(auth);

		assertFalse(SecurityContextUtils.hasAnyAuthority("ROLE_ADMIN", "ROLE_SUPER_ADMIN"));
	}

	@Test
	void hasAnyAuthority_whenUnauthenticated_returnsFalse() {
		SecurityContextHolder.clearContext();

		assertFalse(SecurityContextUtils.hasAnyAuthority("ROLE_ADMIN"));
	}

	@Test
	void hasAnyAuthority_whenAnonymous_returnsFalse() {
		setAnonymousAuthentication();

		assertFalse(SecurityContextUtils.hasAnyAuthority("ROLE_ADMIN"));
	}

	// ─── helpers ─────────────────────────────────────────────────────────────

	private void setAuthentication(String userId, Map<String, Object> details) {
		UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(userId, null,
				List.of(new SimpleGrantedAuthority("ROLE_USER")));
		if (details != null) {
			auth.setDetails(details);
		}
		SecurityContextHolder.getContext().setAuthentication(auth);
	}

	private void setAnonymousAuthentication() {
		AnonymousAuthenticationToken anon = new AnonymousAuthenticationToken("key", "anonymousUser",
				List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS")));
		SecurityContextHolder.getContext().setAuthentication(anon);
	}

	// ─── N-9: requireCurrentUserIdStrict ───────────────────────────────────

	@Test
	void requireCurrentUserIdStrict_whenAuthenticated_returnsUserId() {
		setAuthentication("user-strict", null);

		assertEquals("user-strict", SecurityContextUtils.requireCurrentUserIdStrict());
	}

	@Test
	void requireCurrentUserIdStrict_whenUnauthenticated_throws() {
		SecurityContextHolder.clearContext();

		IllegalStateException ex = assertThrows(IllegalStateException.class,
				SecurityContextUtils::requireCurrentUserIdStrict);
		assertTrue(ex.getMessage().contains("No authenticated user"), "exception message should explain the contract");
	}

	@Test
	void requireCurrentUserIdStrict_whenAnonymous_throws() {
		setAnonymousAuthentication();

		assertThrows(IllegalStateException.class, SecurityContextUtils::requireCurrentUserIdStrict);
	}

	// ─── N-9: off-request-thread WARN once ─────────────────────────────────

	@Test
	void getCurrentUserId_offRequestThread_warnsOnceAndReturnsNull() {
		// no request attributes, no auth → off-request thread
		RequestContextHolder.resetRequestAttributes();
		SecurityContextHolder.clearContext();

		assertNull(SecurityContextUtils.getCurrentUserId());
		assertNull(SecurityContextUtils.getCurrentUserId());
		assertNull(SecurityContextUtils.getCurrentUserId());

		long warnCount = appender.list.stream()
			.filter(e -> e.getLevel() == Level.WARN)
			.filter(e -> e.getFormattedMessage().contains("non-request thread"))
			.count();
		assertEquals(1, warnCount, "WARN must be emitted exactly once per JVM");
	}

	@Test
	void getCurrentUserId_inRequestThread_doesNotWarn() {
		// simulate a Servlet request thread but with no authentication
		RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
		SecurityContextHolder.clearContext();

		assertNull(SecurityContextUtils.getCurrentUserId());

		boolean anyWarn = appender.list.stream().anyMatch(e -> e.getLevel() == Level.WARN);
		assertFalse(anyWarn, "anonymous / unauthenticated HTTP requests must not emit the off-thread WARN");
	}

}
