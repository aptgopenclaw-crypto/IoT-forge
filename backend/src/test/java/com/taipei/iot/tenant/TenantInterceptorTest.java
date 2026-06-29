package com.taipei.iot.tenant;

import com.taipei.iot.common.enums.ErrorCode;
import com.taipei.iot.common.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TenantInterceptorTest {

	@AfterEach
	void cleanup() {
		TenantContext.clear();
	}

	private static TenantInterceptor newInterceptor(String mode, String defaultId) {
		TenantProperties props = new TenantProperties();
		props.setMode(mode);
		props.setDefaultId(defaultId);
		return new TenantInterceptor(props);
	}

	@Test
	void singleMode_shouldSetDefaultTenant() throws Exception {
		TenantInterceptor interceptor = newInterceptor("single", "DEFAULT");
		interceptor.preHandle(mock(HttpServletRequest.class), mock(HttpServletResponse.class), new Object());

		assertEquals("DEFAULT", TenantContext.getCurrentTenantId());
	}

	@Test
	void multiMode_shouldNotSetTenant() throws Exception {
		TenantInterceptor interceptor = newInterceptor("multi", null);
		interceptor.preHandle(mock(HttpServletRequest.class), mock(HttpServletResponse.class), new Object());

		assertNull(TenantContext.getCurrentTenantId());
	}

	@Test
	void afterCompletion_shouldClearContext() throws Exception {
		TenantInterceptor interceptor = newInterceptor("multi", null);
		TenantContext.setCurrentTenantId("T1");

		interceptor.afterCompletion(mock(HttpServletRequest.class), mock(HttpServletResponse.class), new Object(),
				null);

		assertNull(TenantContext.getCurrentTenantId());
	}

	// ──────────────── [T-5] single mode tenant mismatch ────────────────

	@Test
	void singleMode_jwtTenantMatchesDefault_passes() {
		TenantInterceptor interceptor = newInterceptor("single", "DEFAULT");
		TenantContext.setCurrentTenantId("DEFAULT"); // JWT 已設定一致的 tenant

		boolean ok = interceptor.preHandle(mock(HttpServletRequest.class), mock(HttpServletResponse.class),
				new Object());

		assertTrue(ok);
		assertEquals("DEFAULT", TenantContext.getCurrentTenantId());
	}

	@Test
	void singleMode_jwtTenantMismatch_throws403_andClearsContext() {
		TenantInterceptor interceptor = newInterceptor("single", "DEFAULT");
		TenantContext.setCurrentTenantId("OTHER_TENANT"); // 與 default 不一致

		HttpServletRequest req = mock(HttpServletRequest.class);
		when(req.getRemoteAddr()).thenReturn("10.0.0.1");
		when(req.getRequestURI()).thenReturn("/v1/admin/users");

		assertThatThrownBy(() -> interceptor.preHandle(req, mock(HttpServletResponse.class), new Object()))
			.isInstanceOf(BusinessException.class)
			.extracting("errorCode")
			.isEqualTo(ErrorCode.TENANT_MODE_MISMATCH);

		// 失敗時應清掉 context 避免後續流程誤用 OTHER_TENANT
		assertNull(TenantContext.getCurrentTenantId());
	}

	@Test
	void singleMode_systemContext_isExempt() throws Exception {
		TenantInterceptor interceptor = newInterceptor("single", "DEFAULT");
		TenantContext.setSystemContext(); // SYSTEM context 應放行（內部排程 / 跨租戶工具）

		boolean ok = interceptor.preHandle(mock(HttpServletRequest.class), mock(HttpServletResponse.class),
				new Object());

		assertTrue(ok);
		// SYSTEM context 仍被覆寫為 defaultId（single 模式的契約）
		assertEquals("DEFAULT", TenantContext.getCurrentTenantId());
	}

	@Test
	void singleMode_noJwtTenant_passes_publicEndpoint() throws Exception {
		// /v1/auth/login 等公開端點：JwtAuthenticationFilter 不會設 tenantId
		TenantInterceptor interceptor = newInterceptor("single", "DEFAULT");
		assertNull(TenantContext.getCurrentTenantId());

		boolean ok = interceptor.preHandle(mock(HttpServletRequest.class), mock(HttpServletResponse.class),
				new Object());

		assertTrue(ok);
		assertEquals("DEFAULT", TenantContext.getCurrentTenantId());
	}

	// ──────────────── boot-time observability ────────────────

	@Test
	void logMode_doesNotThrow_forBothModes() {
		// 啟動 log 不應丟例外（不檢查 log 內容，僅驗證可呼叫）
		newInterceptor("single", "DEFAULT").logMode();
		newInterceptor("multi", null).logMode();
	}

}
