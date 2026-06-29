package com.taipei.iot.common.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taipei.iot.common.annotation.RateLimit;
import com.taipei.iot.common.annotation.RateLimit.KeyStrategy;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.method.HandlerMethod;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class RateLimitInterceptorTest {

	@Mock
	private StringRedisTemplate redisTemplate;

	@Mock
	private HttpServletRequest request;

	@Mock
	private HttpServletResponse response;

	@Mock
	private HandlerMethod handlerMethod;

	@InjectMocks
	private RateLimitInterceptor interceptor;

	private final ObjectMapper objectMapper = new ObjectMapper();

	@BeforeEach
	void setUp() {
		interceptor = new RateLimitInterceptor(redisTemplate, objectMapper);
	}

	@AfterEach
	void tearDown() {
		SecurityContextHolder.clearContext();
	}

	// ─── Helper: stub @RateLimit annotation on handler ───────────────────────

	private RateLimit mockRateLimit(String key, int limit, int period) {
		RateLimit rateLimit = mock(RateLimit.class);
		when(rateLimit.key()).thenReturn(key);
		lenient().when(rateLimit.limit()).thenReturn(limit);
		lenient().when(rateLimit.period()).thenReturn(period);
		when(handlerMethod.getMethodAnnotation(RateLimit.class)).thenReturn(rateLimit);
		return rateLimit;
	}

	@SuppressWarnings("unchecked")
	private void mockLuaScript(Long returnValue) {
		when(redisTemplate.execute(any(RedisScript.class), anyList(), any(String.class))).thenReturn(returnValue);
	}

	@SuppressWarnings("unchecked")
	private void mockLuaScriptThrows(Exception ex) {
		when(redisTemplate.execute(any(RedisScript.class), anyList(), any(String.class))).thenThrow(ex);
	}

	// ─── 1. No annotation → pass through ────────────────────────────────────

	@Test
	void preHandle_noAnnotation_shouldPassThrough() throws Exception {
		when(handlerMethod.getMethodAnnotation(RateLimit.class)).thenReturn(null);

		boolean result = interceptor.preHandle(request, response, handlerMethod);

		assertTrue(result);
		verifyNoInteractions(redisTemplate);
	}

	// ─── 2. Not HandlerMethod → pass through ────────────────────────────────

	@Test
	void preHandle_notHandlerMethod_shouldPassThrough() throws Exception {
		Object staticHandler = new Object();

		boolean result = interceptor.preHandle(request, response, staticHandler);

		assertTrue(result);
		verifyNoInteractions(redisTemplate);
	}

	// ─── 3. Under limit → allow ─────────────────────────────────────────────

	@Test
	void preHandle_underLimit_shouldAllow() throws Exception {
		mockRateLimit("login", 10, 60);
		when(request.getRemoteAddr()).thenReturn("192.168.1.100");
		mockLuaScript(5L);

		boolean result = interceptor.preHandle(request, response, handlerMethod);

		assertTrue(result);
		verify(response, never()).setStatus(anyInt());
	}

	// ─── 4. First request → Lua script handles EXPIRE atomically ────────────

	@Test
	@SuppressWarnings("unchecked")
	void preHandle_firstRequest_luaScriptCalledWithCorrectArgs() throws Exception {
		mockRateLimit("captcha", 20, 60);
		when(request.getRemoteAddr()).thenReturn("10.0.0.1");
		mockLuaScript(1L);

		boolean result = interceptor.preHandle(request, response, handlerMethod);

		assertTrue(result);
		verify(redisTemplate).execute(any(RedisScript.class), eq(List.of("rate_limit:captcha:10.0.0.1")), eq("60"));
	}

	// ─── 5. Subsequent request → still allowed ──────────────────────────────

	@Test
	void preHandle_subsequentRequest_shouldAllow() throws Exception {
		mockRateLimit("login", 10, 60);
		when(request.getRemoteAddr()).thenReturn("10.0.0.1");
		mockLuaScript(3L);

		boolean result = interceptor.preHandle(request, response, handlerMethod);

		assertTrue(result);
	}

	// ─── 6. Exceed limit → 429 ─────────────────────────────────────────────

	@Test
	void preHandle_exceedLimit_shouldReturn429() throws Exception {
		mockRateLimit("login", 10, 60);
		when(request.getRemoteAddr()).thenReturn("192.168.1.100");
		mockLuaScript(11L);
		when(redisTemplate.getExpire("rate_limit:login:192.168.1.100", TimeUnit.SECONDS)).thenReturn(45L);

		StringWriter body = new StringWriter();
		when(response.getWriter()).thenReturn(new PrintWriter(body));

		boolean result = interceptor.preHandle(request, response, handlerMethod);

		assertFalse(result);
		verify(response).setStatus(429);
		verify(response).setHeader("Retry-After", "45");
		verify(response).setContentType("application/json");
		assertTrue(body.toString().contains("10030")); // ErrorCode.RATE_LIMIT_EXCEEDED
	}

	// ─── 7. At exact limit → still allowed ─────────────────────────────────

	@Test
	void preHandle_atExactLimit_shouldStillAllow() throws Exception {
		mockRateLimit("login", 10, 60);
		when(request.getRemoteAddr()).thenReturn("10.0.0.1");
		mockLuaScript(10L);

		boolean result = interceptor.preHandle(request, response, handlerMethod);

		assertTrue(result);
	}

	// ─── 8. Redis failure → fail-open ───────────────────────────────────────

	@Test
	void preHandle_redisUnavailable_shouldFailOpen() throws Exception {
		mockRateLimit("login", 10, 60);
		when(request.getRemoteAddr()).thenReturn("10.0.0.1");
		mockLuaScriptThrows(new RedisConnectionFailureException("Connection refused"));

		boolean result = interceptor.preHandle(request, response, handlerMethod);

		assertTrue(result);
		verify(response, never()).setStatus(anyInt());
	}

	// ─── 9. Script returns null → pass through ──────────────────────────────

	@Test
	@SuppressWarnings("unchecked")
	void preHandle_scriptReturnsNull_shouldPassThrough() throws Exception {
		mockRateLimit("login", 10, 60);
		when(request.getRemoteAddr()).thenReturn("10.0.0.1");
		when(redisTemplate.execute(any(RedisScript.class), anyList(), any(String.class))).thenReturn(null);

		boolean result = interceptor.preHandle(request, response, handlerMethod);

		assertTrue(result);
	}

	// ─── 10. Uses remoteAddr (not X-Forwarded-For) ──────────────────────────

	@Test
	void preHandle_shouldUseRemoteAddr_notForwardedHeader() throws Exception {
		mockRateLimit("login", 10, 60);
		when(request.getRemoteAddr()).thenReturn("172.16.0.1");
		mockLuaScript(1L);

		boolean result = interceptor.preHandle(request, response, handlerMethod);

		assertTrue(result);
		verify(request).getRemoteAddr();
		verify(request, never()).getHeader(anyString());
	}

	// ─── 11. Retry-After with null TTL ──────────────────────────────────────

	@Test
	void preHandle_exceedLimit_nullTtl_shouldNotSetRetryAfter() throws Exception {
		mockRateLimit("captcha", 20, 60);
		when(request.getRemoteAddr()).thenReturn("10.0.0.1");
		mockLuaScript(21L);
		when(redisTemplate.getExpire("rate_limit:captcha:10.0.0.1", TimeUnit.SECONDS)).thenReturn(null);

		StringWriter body = new StringWriter();
		when(response.getWriter()).thenReturn(new PrintWriter(body));

		boolean result = interceptor.preHandle(request, response, handlerMethod);

		assertFalse(result);
		verify(response).setStatus(429);
		verify(response, never()).setHeader(eq("Retry-After"), anyString());
	}

	// ─── 12. Different keys produce different Redis keys ────────────────────

	@Test
	@SuppressWarnings("unchecked")
	void preHandle_differentKeys_shouldUseDifferentRedisKeys() throws Exception {
		mockRateLimit("forgot-pwd", 5, 300);
		when(request.getRemoteAddr()).thenReturn("10.0.0.1");
		mockLuaScript(1L);

		boolean result = interceptor.preHandle(request, response, handlerMethod);

		assertTrue(result);
		verify(redisTemplate).execute(any(RedisScript.class), eq(List.of("rate_limit:forgot-pwd:10.0.0.1")), eq("300"));
	}

	// ─── F-5: KeyStrategy.USER_OR_IP ─────────────────────────────────────────

	private RateLimit mockRateLimitWithStrategy(String key, int limit, int period, KeyStrategy strategy) {
		RateLimit rateLimit = mockRateLimit(key, limit, period);
		when(rateLimit.strategy()).thenReturn(strategy);
		return rateLimit;
	}

	private void authenticateAs(String userId) {
		SecurityContextHolder.getContext()
			.setAuthentication(new UsernamePasswordAuthenticationToken(userId, "n/a", List.of()));
	}

	@Test
	@SuppressWarnings("unchecked")
	void preHandle_userOrIp_whenAuthenticated_usesUserScopedKey() throws Exception {
		mockRateLimitWithStrategy("create-order", 30, 60, KeyStrategy.USER_OR_IP);
		authenticateAs("alice-42");
		// remoteAddr 也存在，但不該被當成 identifier
		lenient().when(request.getRemoteAddr()).thenReturn("10.0.0.1");
		mockLuaScript(1L);

		boolean result = interceptor.preHandle(request, response, handlerMethod);

		assertTrue(result);
		verify(redisTemplate).execute(any(RedisScript.class), eq(List.of("rate_limit:create-order:user:alice-42")),
				eq("60"));
	}

	@Test
	@SuppressWarnings("unchecked")
	void preHandle_userOrIp_whenAnonymous_fallsBackToIpScopedKey() throws Exception {
		mockRateLimitWithStrategy("create-order", 30, 60, KeyStrategy.USER_OR_IP);
		// 沒有 SecurityContext authentication
		when(request.getRemoteAddr()).thenReturn("203.0.113.7");
		mockLuaScript(1L);

		boolean result = interceptor.preHandle(request, response, handlerMethod);

		assertTrue(result);
		verify(redisTemplate).execute(any(RedisScript.class), eq(List.of("rate_limit:create-order:ip:203.0.113.7")),
				eq("60"));
	}

	@Test
	@SuppressWarnings("unchecked")
	void preHandle_userOrIp_differentUsersOnSameIp_getSeparateBuckets() throws Exception {
		// alice 用滿配額
		mockRateLimitWithStrategy("create-order", 5, 60, KeyStrategy.USER_OR_IP);
		authenticateAs("alice");
		lenient().when(request.getRemoteAddr()).thenReturn("10.0.0.1");
		mockLuaScript(6L);
		when(redisTemplate.getExpire(anyString(), eq(TimeUnit.SECONDS))).thenReturn(30L);
		StringWriter aliceBody = new StringWriter();
		when(response.getWriter()).thenReturn(new PrintWriter(aliceBody));

		boolean aliceResult = interceptor.preHandle(request, response, handlerMethod);
		assertFalse(aliceResult);
		verify(redisTemplate).execute(any(RedisScript.class), eq(List.of("rate_limit:create-order:user:alice")),
				eq("60"));

		// bob 同 IP 但 userId 不同 → 不同 Redis key（不被 alice 影響）
		SecurityContextHolder.clearContext();
		authenticateAs("bob");
		reset(redisTemplate, response);
		StringWriter bobBody = new StringWriter();
		lenient().when(response.getWriter()).thenReturn(new PrintWriter(bobBody));
		when(redisTemplate.execute(any(RedisScript.class), anyList(), any(String.class))).thenReturn(1L);

		boolean bobResult = interceptor.preHandle(request, response, handlerMethod);
		assertTrue(bobResult);
		verify(redisTemplate).execute(any(RedisScript.class), eq(List.of("rate_limit:create-order:user:bob")),
				eq("60"));
	}

	@Test
	@SuppressWarnings("unchecked")
	void preHandle_userOrIp_blankUserId_treatedAsAnonymousAndUsesIp() throws Exception {
		mockRateLimitWithStrategy("create-order", 30, 60, KeyStrategy.USER_OR_IP);
		// 空字串 principal 視為匿名
		authenticateAs("   ");
		when(request.getRemoteAddr()).thenReturn("198.51.100.9");
		mockLuaScript(1L);

		boolean result = interceptor.preHandle(request, response, handlerMethod);

		assertTrue(result);
		verify(redisTemplate).execute(any(RedisScript.class), eq(List.of("rate_limit:create-order:ip:198.51.100.9")),
				eq("60"));
	}

	@Test
	@SuppressWarnings("unchecked")
	void preHandle_explicitIpStrategy_authenticatedUser_stillUsesIpKey() throws Exception {
		// 即使已登入，IP strategy 仍依 IP（背向相容 / 防 brute-force 端點）
		mockRateLimitWithStrategy("login", 10, 60, KeyStrategy.IP);
		authenticateAs("alice-42");
		when(request.getRemoteAddr()).thenReturn("10.0.0.1");
		mockLuaScript(1L);

		boolean result = interceptor.preHandle(request, response, handlerMethod);

		assertTrue(result);
		verify(redisTemplate).execute(any(RedisScript.class), eq(List.of("rate_limit:login:10.0.0.1")), eq("60"));
	}

}
