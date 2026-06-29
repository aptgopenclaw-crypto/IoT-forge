package com.taipei.iot.config;

import com.taipei.iot.auth.security.JwtUtil;
import com.taipei.iot.common.interceptor.RateLimitInterceptor;
import com.taipei.iot.tenant.TenantEnabledCache;
import com.taipei.iot.tenant.TenantInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

/**
 * 驗證 SecurityConfig 設定的 CSP (Content-Security-Policy) header 包含所有必要指令。 [Config v2 N-1]
 */
@WebMvcTest(SecurityConfigCspTest.NoopController.class)
@Import({ SecurityConfig.class, CorsProperties.class })
@TestPropertySource(properties = "cors.allowed-origins=https://test.example.com")
class SecurityConfigCspTest {

	@RestController
	static class NoopController {

		@GetMapping("/v1/noauth/csp-test")
		ResponseEntity<String> cspTest() {
			return ResponseEntity.ok("ok");
		}

	}

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private JwtUtil jwtUtil;

	@MockitoBean
	private StringRedisTemplate stringRedisTemplate;

	@MockitoBean
	private TenantEnabledCache tenantEnabledCache;

	@MockitoBean
	private RateLimitInterceptor rateLimitInterceptor;

	@MockitoBean
	private TenantInterceptor tenantInterceptor;

	@Test
	void csp_containsFrameAncestorsNone() throws Exception {
		mockMvc.perform(get("/v1/noauth/csp-test"))
			.andExpect(header().string("Content-Security-Policy", containsString("frame-ancestors 'none'")));
	}

	@Test
	void csp_containsObjectSrcNone() throws Exception {
		mockMvc.perform(get("/v1/noauth/csp-test"))
			.andExpect(header().string("Content-Security-Policy", containsString("object-src 'none'")));
	}

	@Test
	void csp_containsBaseUriSelf() throws Exception {
		mockMvc.perform(get("/v1/noauth/csp-test"))
			.andExpect(header().string("Content-Security-Policy", containsString("base-uri 'self'")));
	}

	@Test
	void csp_containsFormActionSelf() throws Exception {
		mockMvc.perform(get("/v1/noauth/csp-test"))
			.andExpect(header().string("Content-Security-Policy", containsString("form-action 'self'")));
	}

	@Test
	void csp_containsDefaultSrcSelf() throws Exception {
		mockMvc.perform(get("/v1/noauth/csp-test"))
			.andExpect(header().string("Content-Security-Policy", containsString("default-src 'self'")));
	}

	@Test
	void csp_containsScriptSrcSelf() throws Exception {
		mockMvc.perform(get("/v1/noauth/csp-test"))
			.andExpect(header().string("Content-Security-Policy", containsString("script-src 'self'")));
	}

	// [N-7] X-XSS-Protection: 0
	@Test
	void xssProtection_shouldBeDisabled() throws Exception {
		mockMvc.perform(get("/v1/noauth/csp-test")).andExpect(header().string("X-XSS-Protection", "0"));
	}

}
