package com.taipei.iot.config;

import com.taipei.iot.common.interceptor.DeprecatedApiInterceptor;
import com.taipei.iot.common.interceptor.RateLimitInterceptor;
import com.taipei.iot.tenant.TenantInterceptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.handler.MappedInterceptor;
import org.springframework.web.util.ServletRequestPathUtils;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * 單元測試 [Tenant v2 T-10]：驗證 {@link WebMvcConfig#addInterceptors} 將 {@code /v1/noauth/**} 自
 * {@link TenantInterceptor} 排除，並仍涵蓋業務 API。
 */
class WebMvcConfigTest {

	private RateLimitInterceptor rateLimitInterceptor;

	private TenantInterceptor tenantInterceptor;

	private DeprecatedApiInterceptor deprecatedApiInterceptor;

	private CorsProperties corsProperties;

	private WebMvcConfig config;

	@BeforeEach
	void setUp() {
		rateLimitInterceptor = mock(RateLimitInterceptor.class);
		tenantInterceptor = mock(TenantInterceptor.class);
		deprecatedApiInterceptor = mock(DeprecatedApiInterceptor.class);
		corsProperties = mock(CorsProperties.class);
		config = new WebMvcConfig(rateLimitInterceptor, tenantInterceptor, deprecatedApiInterceptor, corsProperties);
	}

	private MappedInterceptor mappedFor(Object target) throws Exception {
		InterceptorRegistry registry = new InterceptorRegistry();
		config.addInterceptors(registry);

		// InterceptorRegistry#getInterceptors 為 protected，透過 reflection 取得
		Method m = InterceptorRegistry.class.getDeclaredMethod("getInterceptors");
		m.setAccessible(true);
		@SuppressWarnings("unchecked")
		List<Object> interceptors = (List<Object>) m.invoke(registry);

		for (Object obj : interceptors) {
			if (obj instanceof MappedInterceptor mi && mi.getInterceptor() == target) {
				return mi;
			}
		}
		throw new AssertionError("MappedInterceptor not found for target " + target);
	}

	private static MockHttpServletRequest req(String path) {
		MockHttpServletRequest r = new MockHttpServletRequest("GET", path);
		r.setRequestURI(path);
		// MappedInterceptor.matches() uses pre-parsed RequestPath; we must populate it.
		ServletRequestPathUtils.parseAndCache(r);
		return r;
	}

	// ──────────────── TenantInterceptor mapping ────────────────

	@Test
	void tenantInterceptor_attachedToV1() throws Exception {
		MappedInterceptor mapped = mappedFor(tenantInterceptor);

		assertThat(mapped.matches(req("/v1/auth/sessions"))).isTrue();
		assertThat(mapped.matches(req("/v1/announcement/list"))).isTrue();
		assertThat(mapped.matches(req("/v1/platform/tenants"))).isTrue();
	}

	@Test
	void tenantInterceptor_excludesNoauthPaths() throws Exception {
		MappedInterceptor mapped = mappedFor(tenantInterceptor);

		// T-10 核心斷言：所有 /v1/noauth/** 路徑都不應被 TenantInterceptor 攔截
		assertThat(mapped.matches(req("/v1/noauth/login"))).isFalse();
		assertThat(mapped.matches(req("/v1/noauth/captcha"))).isFalse();
		assertThat(mapped.matches(req("/v1/noauth/token/refresh"))).isFalse();
		assertThat(mapped.matches(req("/v1/noauth/user/forgot-password"))).isFalse();
		assertThat(mapped.matches(req("/v1/noauth/user/force-change-password"))).isFalse();
		assertThat(mapped.matches(req("/v1/noauth/turnstile/config"))).isFalse();
		assertThat(mapped.matches(req("/v1/noauth/password-policy/describe"))).isFalse();
	}

	@Test
	void tenantInterceptor_doesNotAttachToNonV1Paths() throws Exception {
		MappedInterceptor mapped = mappedFor(tenantInterceptor);

		// Swagger / actuator / WebSocket 等天然不在 /v1/** 範圍
		assertThat(mapped.matches(req("/v3/api-docs"))).isFalse();
		assertThat(mapped.matches(req("/swagger-ui/index.html"))).isFalse();
		assertThat(mapped.matches(req("/actuator/health"))).isFalse();
		assertThat(mapped.matches(req("/ws/notifications"))).isFalse();
	}

	// ──────────────── RateLimitInterceptor mapping (unchanged) ────────────────

	@Test
	void rateLimitInterceptor_coversAllV1_includingNoauth() throws Exception {
		// RateLimit 仍應涵蓋公開端點，否則 /v1/noauth/login 會被 brute-force 攻擊
		MappedInterceptor mapped = mappedFor(rateLimitInterceptor);

		assertThat(mapped.matches(req("/v1/noauth/login"))).isTrue();
		assertThat(mapped.matches(req("/v1/auth/sessions"))).isTrue();
	}

	// ──────────────── [F-1] Health/Probe path exclusions ────────────────

	@Test
	void rateLimitInterceptor_excludesHealthProbePaths() throws Exception {
		MappedInterceptor mapped = mappedFor(rateLimitInterceptor);

		assertThat(mapped.matches(req("/v1/health"))).isFalse();
		assertThat(mapped.matches(req("/v1/healthz"))).isFalse();
		assertThat(mapped.matches(req("/v1/ready"))).isFalse();
	}

	@Test
	void tenantInterceptor_excludesHealthProbePaths() throws Exception {
		MappedInterceptor mapped = mappedFor(tenantInterceptor);

		assertThat(mapped.matches(req("/v1/health"))).isFalse();
		assertThat(mapped.matches(req("/v1/healthz"))).isFalse();
		assertThat(mapped.matches(req("/v1/ready"))).isFalse();
	}

}
