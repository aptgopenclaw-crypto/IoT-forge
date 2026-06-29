package com.taipei.iot.audit.service;

import com.taipei.iot.audit.dto.AuditQueryRequest;
import com.taipei.iot.audit.dto.UserEventLogDto;
import com.taipei.iot.audit.entity.UserEventLogEntity;
import com.taipei.iot.audit.repository.UserEventLogRepository;
import com.taipei.iot.dept.service.DataScopeHelper;
import com.taipei.iot.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

	@Mock
	private UserEventLogRepository userEventLogRepository;

	@Mock
	private DataScopeHelper dataScopeHelper;

	@InjectMocks
	private AuditService auditService;

	@BeforeEach
	void setUp() {
		var auth = new UsernamePasswordAuthenticationToken("u-admin-001", null, Collections.emptyList());
		SecurityContextHolder.getContext().setAuthentication(auth);
		TenantContext.setCurrentTenantId("tenant-100");
	}

	@AfterEach
	void tearDown() {
		SecurityContextHolder.clearContext();
		TenantContext.clear();
	}

	@Test
	void getUserUsageHistory_shouldReturnPagedResults() {
		UserEventLogEntity entity = buildEntity("LOGIN", "USER_AUTH");
		Page<UserEventLogEntity> page = new PageImpl<>(List.of(entity));
		when(userEventLogRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

		AuditQueryRequest request = new AuditQueryRequest();
		Page<UserEventLogDto> result = auditService.getUserUsageHistory(request, true, PageRequest.of(0, 20));

		assertNotNull(result);
		assertEquals(1, result.getTotalElements());
		assertEquals("LOGIN", result.getContent().get(0).getEventType());
	}

	@Test
	void getUserUsageHistory_shouldFilterByUserName() {
		when(userEventLogRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());

		AuditQueryRequest request = new AuditQueryRequest();
		request.setUserName("admin");
		Page<UserEventLogDto> result = auditService.getUserUsageHistory(request, true, PageRequest.of(0, 20));

		assertNotNull(result);
		verify(userEventLogRepository).findAll(any(Specification.class), any(Pageable.class));
	}

	@Test
	void getUserUsageHistory_shouldFilterByEventDesc() {
		when(userEventLogRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());

		AuditQueryRequest request = new AuditQueryRequest();
		request.setEventDesc("USER_AUTH");
		Page<UserEventLogDto> result = auditService.getUserUsageHistory(request, true, PageRequest.of(0, 20));

		assertNotNull(result);
		verify(userEventLogRepository).findAll(any(Specification.class), any(Pageable.class));
	}

	@Test
	void getMyEventLogs_shouldFilterByCurrentUser() {
		UserEventLogEntity entity = buildEntity("LOGIN", "USER_AUTH");
		Page<UserEventLogEntity> page = new PageImpl<>(List.of(entity));
		when(userEventLogRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

		Page<UserEventLogDto> result = auditService.getMyEventLogs(null, null, null, PageRequest.of(0, 20));

		assertNotNull(result);
		assertEquals(1, result.getTotalElements());
		verify(userEventLogRepository).findAll(any(Specification.class), any(Pageable.class));
	}

	@Test
	void getMyEventLogs_shouldFilterByEventType() {
		when(userEventLogRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());

		Page<UserEventLogDto> result = auditService.getMyEventLogs("LOGIN", null, null, PageRequest.of(0, 20));

		assertNotNull(result);
		verify(userEventLogRepository).findAll(any(Specification.class), any(Pageable.class));
	}

	@Test
	void getMyEventLogs_shouldFilterByTimeRange() {
		when(userEventLogRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());

		LocalDateTime start = LocalDateTime.of(2026, 4, 1, 0, 0);
		LocalDateTime end = LocalDateTime.of(2026, 4, 30, 23, 59, 59);

		Page<UserEventLogDto> result = auditService.getMyEventLogs(null, start, end, PageRequest.of(0, 20));

		assertNotNull(result);
		verify(userEventLogRepository).findAll(any(Specification.class), any(Pageable.class));
	}

	private UserEventLogEntity buildEntity(String eventType, String eventDesc) {
		UserEventLogEntity entity = new UserEventLogEntity();
		entity.setUserEventLogPk(1L);
		entity.setTenantId("tenant-100");
		entity.setUserId("u-admin-001");
		entity.setUsername("admin@test.com");
		entity.setUserLabel("Admin User");
		entity.setEmail("admin@test.com");
		entity.setEventType(eventType);
		entity.setEventDesc(eventDesc);
		entity.setApiEndpoint("/v1/noauth/token");
		entity.setPayload("{\"email\":\"admin@test.com\",\"secret\":\"***\"}");
		entity.setErrorCode("00000");
		entity.setMessage("SUCCESS");
		entity.setIpAddress("192.168.1.100");
		entity.setUserAgent("Mozilla/5.0");
		entity.setExecutionTime(120L);
		entity.setCreateTime(LocalDateTime.of(2026, 4, 1, 9, 0));
		return entity;
	}

	// ── Export CSV ────────────────────────────────────────────────────────

	@Test
	void exportCsv_shouldContainHeadersAndData() throws Exception {
		UserEventLogDto dto = UserEventLogDto.builder()
			.username("admin@test.com")
			.userLabel("Admin User")
			.eventType("LOGIN")
			.eventDesc("USER_AUTH")
			.apiEndpoint("/v1/noauth/login")
			.errorCode("00000")
			.ipAddress("192.168.1.100")
			.executionTime(120L)
			.createTime(LocalDateTime.of(2026, 4, 1, 9, 0))
			.build();

		java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
		auditService.exportCsv(List.of(dto), out);
		String csv = out.toString(java.nio.charset.StandardCharsets.UTF_8);

		assertTrue(csv.contains("帳號,使用者,事件類型,事件描述,API,結果碼,IP,耗時(ms),時間"));
		assertTrue(csv.contains("admin@test.com"));
		assertTrue(csv.contains("2026-04-01 09:00:00"));
	}

	@Test
	void exportCsv_emptyList_shouldHaveHeaderOnly() throws Exception {
		java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
		auditService.exportCsv(List.of(), out);
		String csv = out.toString(java.nio.charset.StandardCharsets.UTF_8);

		assertTrue(csv.contains("帳號"));
		// BOM + header line only, no data lines
		String[] lines = csv.strip().split("\n");
		assertEquals(1, lines.length);
	}

	// ── Export XLSX ───────────────────────────────────────────────────────

	@Test
	void exportXlsx_shouldProduceNonEmptyOutput() throws Exception {
		UserEventLogDto dto = UserEventLogDto.builder()
			.username("admin@test.com")
			.userLabel("Admin User")
			.eventType("LOGIN")
			.eventDesc("USER_AUTH")
			.apiEndpoint("/v1/noauth/login")
			.errorCode("00000")
			.ipAddress("192.168.1.100")
			.executionTime(120L)
			.createTime(LocalDateTime.of(2026, 4, 1, 9, 0))
			.build();

		java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
		auditService.exportXlsx(List.of(dto), out);

		assertTrue(out.size() > 0);
	}

	@Test
	void exportCsv_shouldEscapeCommasAndQuotes() throws Exception {
		UserEventLogDto dto = UserEventLogDto.builder()
			.username("user,with\"special")
			.userLabel("Label")
			.eventType("LOGIN")
			.eventDesc("USER_AUTH")
			.apiEndpoint("/api")
			.errorCode("00000")
			.ipAddress("10.0.0.1")
			.executionTime(50L)
			.createTime(LocalDateTime.of(2026, 4, 1, 10, 0))
			.build();

		java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
		auditService.exportCsv(List.of(dto), out);
		String csv = out.toString(java.nio.charset.StandardCharsets.UTF_8);

		// RFC 4180: commas/quotes must be enclosed in double quotes, inner quotes doubled
		assertTrue(csv.contains("\"user,with\"\"special\""));
	}

	// ── A-1: sortBy whitelist ──────────────────────────────────────────────

	@Test
	void getUserUsageHistory_shouldFallbackToCreateTime_whenSortByInvalid() {
		when(userEventLogRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());

		AuditQueryRequest request = new AuditQueryRequest();
		request.setSortBy("userId.password"); // malicious sortBy
		auditService.getUserUsageHistory(request, true, PageRequest.of(0, 20));

		var captor = org.mockito.ArgumentCaptor.forClass(Pageable.class);
		verify(userEventLogRepository).findAll(any(Specification.class), captor.capture());
		assertEquals("createTime", captor.getValue().getSort().iterator().next().getProperty());
	}

	@Test
	void getUserUsageHistory_shouldAcceptAllowedSortField() {
		when(userEventLogRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());

		AuditQueryRequest request = new AuditQueryRequest();
		request.setSortBy("executionTime");
		auditService.getUserUsageHistory(request, true, PageRequest.of(0, 20));

		var captor = org.mockito.ArgumentCaptor.forClass(Pageable.class);
		verify(userEventLogRepository).findAll(any(Specification.class), captor.capture());
		assertEquals("executionTime", captor.getValue().getSort().iterator().next().getProperty());
	}

	@Test
	void allowedSortFields_shouldContainExpectedFields() {
		assertTrue(AuditService.ALLOWED_SORT_FIELDS.contains("createTime"));
		assertTrue(AuditService.ALLOWED_SORT_FIELDS.contains("username"));
		assertTrue(AuditService.ALLOWED_SORT_FIELDS.contains("eventType"));
		assertFalse(AuditService.ALLOWED_SORT_FIELDS.contains("payload"));
		assertFalse(AuditService.ALLOWED_SORT_FIELDS.contains("tenantId"));
	}

	// ── A-2: CSV formula injection ─────────────────────────────────────────

	@Test
	void csvEscape_shouldNeutralizeFormulaInjection_equalsSign() {
		String result = AuditService.csvEscape("=CMD('calc')");
		assertFalse(result.startsWith("="));
		assertTrue(result.contains("'=CMD"));
	}

	@Test
	void csvEscape_shouldNeutralizeFormulaInjection_plusSign() {
		String result = AuditService.csvEscape("+cmd|'/C calc'!A0");
		assertFalse(result.startsWith("+"));
		assertTrue(result.contains("'+cmd"));
	}

	@Test
	void csvEscape_shouldNeutralizeFormulaInjection_minusSign() {
		String result = AuditService.csvEscape("-1+1");
		assertFalse(result.startsWith("-"));
		assertTrue(result.contains("'-1+1"));
	}

	@Test
	void csvEscape_shouldNeutralizeFormulaInjection_atSign() {
		String result = AuditService.csvEscape("@SUM(A1:A10)");
		assertFalse(result.startsWith("@"));
		assertTrue(result.contains("'@SUM"));
	}

	@Test
	void csvEscape_shouldNotModifyNormalValues() {
		assertEquals("hello world", AuditService.csvEscape("hello world"));
		assertEquals("", AuditService.csvEscape(null));
		assertEquals("12345", AuditService.csvEscape("12345"));
	}

	@Test
	void exportCsv_shouldNeutralizeFormulaInDisplayName() throws Exception {
		UserEventLogDto dto = UserEventLogDto.builder()
			.username("attacker@evil.com")
			.userLabel("=CMD('pwned')")
			.eventType("LOGIN")
			.eventDesc("USER_AUTH")
			.apiEndpoint("/api")
			.errorCode("00000")
			.ipAddress("10.0.0.1")
			.executionTime(50L)
			.createTime(LocalDateTime.of(2026, 4, 1, 10, 0))
			.build();

		java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
		auditService.exportCsv(List.of(dto), out);
		String csv = out.toString(java.nio.charset.StandardCharsets.UTF_8);

		// The exported CSV should NOT have a raw formula-starting cell
		assertFalse(csv.contains(",=CMD"));
		assertTrue(csv.contains("'=CMD"));
	}

}
