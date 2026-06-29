package com.taipei.iot.common.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taipei.iot.common.annotation.RateLimit;
import com.taipei.iot.common.annotation.RateLimit.KeyStrategy;
import com.taipei.iot.common.enums.ErrorCode;
import com.taipei.iot.common.enums.SecurityEvent;
import com.taipei.iot.common.response.BaseResponse;
import com.taipei.iot.common.util.SecurityContextUtils;
import com.taipei.iot.common.util.SecurityLogger;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 速率限制攔截器 — 搭配 {@link RateLimit} 註解使用。
 *
 * <p>
 * 原理：使用 Redis INCR + EXPIRE 實作固定窗口計數器。
 * <ul>
 * <li>Redis Key 格式：{@code rate_limit:{key}:{clientIp}}</li>
 * <li>首次請求：INCR 建立 key（值為 1），並設定 TTL = period 秒</li>
 * <li>後續請求：INCR 遞增計數，超過 limit 則回傳 429 Too Many Requests</li>
 * <li>TTL 到期後 key 自動刪除，重新計數</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitInterceptor implements HandlerInterceptor {

	private static final String KEY_PREFIX = "rate_limit:";

	private static final String LUA_INCR_EXPIRE = """
			local count = redis.call('INCR', KEYS[1])
			if count == 1 then
			    redis.call('EXPIRE', KEYS[1], tonumber(ARGV[1]))
			end
			return count
			""";

	private static final DefaultRedisScript<Long> RATE_LIMIT_SCRIPT;

	static {
		RATE_LIMIT_SCRIPT = new DefaultRedisScript<>();
		RATE_LIMIT_SCRIPT.setScriptText(LUA_INCR_EXPIRE);
		RATE_LIMIT_SCRIPT.setResultType(Long.class);
	}

	private final StringRedisTemplate redisTemplate;

	private final ObjectMapper objectMapper;

	/**
	 * In-memory fallback counter when Redis is unavailable. Key = redisKey, Value =
	 * [count, windowStartMillis]. Not distributed — only protects this JVM instance, but
	 * better than no rate limiting.
	 */
	private final Map<String, long[]> localFallback = new ConcurrentHashMap<>();

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
			throws Exception {
		// 只處理有 @RateLimit 註解的 Controller 方法
		if (!(handler instanceof HandlerMethod handlerMethod)) {
			return true;
		}

		RateLimit rateLimit = handlerMethod.getMethodAnnotation(RateLimit.class);
		if (rateLimit == null) {
			return true;
		}

		String clientIp = getClientIp(request);
		String identifier = resolveIdentifier(rateLimit.strategy(), clientIp);
		String redisKey = KEY_PREFIX + rateLimit.key() + ":" + identifier;

		// 使用 Lua script 保證 INCR + EXPIRE 原子性，避免 key 永不過期
		Long currentCount;
		try {
			currentCount = redisTemplate.execute(RATE_LIMIT_SCRIPT, Collections.singletonList(redisKey),
					String.valueOf(rateLimit.period()));
		}
		catch (Exception ex) {
			// Redis 不可用時使用 in-memory fallback，避免速率限制完全失效
			log.warn("Redis 不可用，使用本地 fallback 速率限制: {}", ex.getMessage());
			currentCount = localFallbackIncrement(redisKey, rateLimit.period());
		}
		if (currentCount == null) {
			return true;
		}

		// 超過限制 → 回傳 429
		if (currentCount > rateLimit.limit()) {
			SecurityLogger.warn(SecurityEvent.RATE_LIMITED, clientIp, "endpoint=" + rateLimit.key(),
					"count=" + currentCount + "/" + rateLimit.limit(), "period=" + rateLimit.period() + "s");
			log.warn("速率限制觸發 — IP: {}, key: {}, count: {}/{}, period: {}s", clientIp, rateLimit.key(), currentCount,
					rateLimit.limit(), rateLimit.period());

			response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
			response.setContentType(MediaType.APPLICATION_JSON_VALUE);
			response.setCharacterEncoding("UTF-8");
			// 告訴前端還需要等多久（秒）
			Long ttl = redisTemplate.getExpire(redisKey, TimeUnit.SECONDS);
			if (ttl != null && ttl > 0) {
				response.setHeader("Retry-After", String.valueOf(ttl));
			}
			String body = objectMapper.writeValueAsString(BaseResponse.fail(ErrorCode.RATE_LIMIT_EXCEEDED));
			response.getWriter().write(body);
			return false;
		}

		return true;
	}

	/**
	 * In-memory rate limit fallback when Redis is unavailable. Uses a simple fixed-window
	 * counter per key. Not distributed, but prevents single-JVM brute-force during Redis
	 * outages.
	 */
	private long localFallbackIncrement(String key, int periodSeconds) {
		long now = System.currentTimeMillis();
		long windowMs = periodSeconds * 1000L;

		long[] state = localFallback.compute(key, (k, v) -> {
			if (v == null || (now - v[1]) >= windowMs) {
				// New window
				return new long[] { 1, now };
			}
			v[0]++;
			return v;
		});
		return state[0];
	}

	/**
	 * 取得客戶端真實 IP。
	 *
	 * <h3>安全考量（CVE 級別）：</h3>
	 * <p>
	 * X-Forwarded-For、X-Real-IP 等 HTTP header 是<b>客戶端可任意偽造的</b>。 如果速率限制依賴這些 header 來識別
	 * IP，攻擊者只需每次請求帶不同的 {@code X-Forwarded-For} 值，就能無限繞過速率限制：
	 * </p>
	 * <pre>
	 *   curl -H "X-Forwarded-For: 1.1.1.1" /login   → 計數器 key = rate_limit:login:1.1.1.1
	 *   curl -H "X-Forwarded-For: 2.2.2.2" /login   → 計數器 key = rate_limit:login:2.2.2.2
	 *   // 每次換一個假 IP，速率限制永遠不會觸發
	 * </pre>
	 *
	 * <h3>修復策略：</h3>
	 * <p>
	 * 直接使用 {@code request.getRemoteAddr()} — 這是 TCP 連線層的來源 IP， 無法被 HTTP header
	 * 偽造。只有在前方有<b>受信任的反向代理</b>（如 Nginx） 且由 Nginx 設定 X-Forwarded-For 時，才應讀取 proxy header。
	 * </p>
	 *
	 * <p>
	 * 目前架構：Client → Tomcat（無反向代理），因此 remoteAddr 就是真實客戶端 IP。 未來若部署 Nginx，應搭配 Spring 的
	 * {@code ForwardedHeaderFilter} 或在 {@code application.yml} 設定
	 * {@code server.forward-headers-strategy=NATIVE}， 由框架層安全處理，而非手動讀取 header。
	 * </p>
	 */
	private String getClientIp(HttpServletRequest request) {
		// [安全修復] 直接使用 TCP 連線的來源 IP，不信任任何可偽造的 HTTP header。
		// 舊做法讀取 X-Forwarded-For 等 header，攻擊者可每次變換 header 值繞過速率限制。
		return request.getRemoteAddr();
	}

	/**
	 * F-5：依 {@link KeyStrategy} 決定計數 key 的 identifier 部分。
	 * <ul>
	 * <li>{@link KeyStrategy#IP}：回 {@code clientIp}（背向相容）</li>
	 * <li>{@link KeyStrategy#USER_OR_IP}：已認證回 {@code "user:" + userId}， 匿名回
	 * {@code "ip:" + clientIp}</li>
	 * </ul>
	 */
	private String resolveIdentifier(KeyStrategy strategy, String clientIp) {
		if (strategy == KeyStrategy.USER_OR_IP) {
			String userId = SecurityContextUtils.getCurrentUserId();
			if (userId != null && !userId.isBlank()) {
				return "user:" + userId;
			}
			return "ip:" + clientIp;
		}
		// 預設 IP — 維持原本「rate_limit:{key}:{ip}」格式，避免破壞既有 Redis 鍵
		return clientIp;
	}

}
