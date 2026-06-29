package com.taipei.iot.auth.service.impl;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.taipei.iot.auth.service.TurnstileService;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Cloudflare Turnstile 驗證服務實作。
 *
 * <p>
 * 驗證流程：
 * <ol>
 * <li>前端載入 Turnstile JS widget，使用者完成驗證後取得 token</li>
 * <li>前端在登入請求中附上 token（turnstileToken 欄位）</li>
 * <li>後端收到 token 後，呼叫 Cloudflare siteverify API 驗證</li>
 * <li>Cloudflare 回傳 success: true/false</li>
 * </ol>
 *
 * <p>
 * 設定方式（application.yml）： <pre>
 * captcha:
 *   turnstile:
 *     secret-key: ${TURNSTILE_SECRET_KEY:}   # Cloudflare Dashboard 取得
 *     site-key: ${TURNSTILE_SITE_KEY:}       # 給前端用
 * </pre>
 *
 * <p>
 * 如果未設定 secret-key，{@link #isEnabled()} 回傳 false，系統退回使用圖片驗證碼。
 */
@Slf4j
@Service
public class TurnstileServiceImpl implements TurnstileService {

	private static final String VERIFY_URL = "https://challenges.cloudflare.com/turnstile/v0/siteverify";

	private static final String TURNSTILE_USED_PREFIX = "turnstile:used:";

	private static final long REPLAY_TTL_SECONDS = 300; // 5 minutes (Turnstile token
														// validity window)

	@Value("${captcha.turnstile.secret-key:}")
	private String secretKey;

	private final RestClient restClient;

	private final StringRedisTemplate stringRedisTemplate;

	public TurnstileServiceImpl(RestClient.Builder restClientBuilder, StringRedisTemplate stringRedisTemplate) {
		this.restClient = restClientBuilder.build();
		this.stringRedisTemplate = stringRedisTemplate;
	}

	@Override
	public boolean verify(String token, String remoteIp) {
		if (!isEnabled()) {
			log.warn("Turnstile 未啟用（未設定 secret-key），拒絕驗證");
			return false;
		}

		if (token == null || token.isBlank()) {
			return false;
		}

		// Replay protection: reject previously-used tokens
		String tokenHash = sha256(token);
		String cacheKey = TURNSTILE_USED_PREFIX + tokenHash;
		try {
			if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(cacheKey))) {
				log.warn("Turnstile token replay detected: {}", tokenHash.substring(0, 8));
				return false;
			}
		}
		catch (Exception e) {
			// Redis unavailable — proceed with verification (Cloudflare still does
			// idempotency)
			log.warn("Redis 不可用，跳過 Turnstile replay 檢查: {}", e.getMessage());
		}

		try {
			MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
			form.add("secret", secretKey);
			form.add("response", token);
			if (remoteIp != null && !remoteIp.isBlank()) {
				form.add("remoteip", remoteIp);
			}

			TurnstileResponse result = restClient.post()
				.uri(VERIFY_URL)
				.contentType(MediaType.APPLICATION_FORM_URLENCODED)
				.body(form)
				.retrieve()
				.body(TurnstileResponse.class);

			if (result == null) {
				log.error("Turnstile API 回傳 null");
				return false;
			}

			if (!result.isSuccess()) {
				log.warn("Turnstile 驗證失敗 — errorCodes: {}", result.getErrorCodes());
				return false;
			}

			// Mark token as used to prevent replay within TTL window
			try {
				stringRedisTemplate.opsForValue().set(cacheKey, "1", REPLAY_TTL_SECONDS, TimeUnit.SECONDS);
			}
			catch (Exception e) {
				log.warn("無法寫入 Turnstile replay 快取: {}", e.getMessage());
			}

			return true;
		}
		catch (Exception e) {
			log.error("Turnstile API 呼叫失敗", e);
			return false;
		}
	}

	@Override
	public boolean isEnabled() {
		return secretKey != null && !secretKey.isBlank();
	}

	private static String sha256(String input) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(hash);
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 not available", e);
		}
	}

	/**
	 * Cloudflare siteverify API 的回應格式。
	 *
	 * @see <a href=
	 * "https://developers.cloudflare.com/turnstile/get-started/server-side-validation/">Turnstile
	 * Server-side Validation</a>
	 */
	@Data
	static class TurnstileResponse {

		private boolean success;

		@JsonProperty("error-codes")
		private List<String> errorCodes;

		@JsonProperty("challenge_ts")
		private String challengeTs;

		private String hostname;

	}

}
