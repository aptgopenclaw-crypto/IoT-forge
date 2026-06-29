package com.taipei.iot.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * [Config v2 N-9] Production profile 啟動驗證器 — 確保所有必要安全設定皆有值且合格。 缺項時
 * fail-fast，避免上線後才發現設定遺漏。
 */
@Slf4j
@Component
@Profile("prod")
public class SecurityProfileValidator {

	@Value("${jwt.secret:}")
	private String jwtSecret;

	@Value("${spring.datasource.password:}")
	private String dbPassword;

	@Value("${spring.data.redis.password:}")
	private String redisPassword;

	@Value("${cors.allowed-origins:}")
	private String corsAllowedOrigins;

	@Value("${auth.cookie.secure:false}")
	private boolean cookieSecure;

	@Value("${auth.cookie.same-site:}")
	private String cookieSameSite;

	@PostConstruct
	void validate() {
		List<String> errors = new ArrayList<>();

		// JWT secret: 必須存在且長度 >= 32 字元（256 bits）
		if (!StringUtils.hasText(jwtSecret)) {
			errors.add("jwt.secret must be configured (env: JWT_SECRET)");
		}
		else if (jwtSecret.length() < 32) {
			errors.add("jwt.secret must be at least 32 characters (current: " + jwtSecret.length() + ")");
		}
		else {
			// [F-8] Entropy check — 偵測重複字元 / 常見弱密碼模式
			String entropyError = checkEntropy(jwtSecret);
			if (entropyError != null) {
				errors.add(entropyError);
			}
		}

		// DB password
		if (!StringUtils.hasText(dbPassword)) {
			errors.add("spring.datasource.password must be configured (env: DB_PASSWORD)");
		}

		// Redis password
		if (!StringUtils.hasText(redisPassword)) {
			errors.add("spring.data.redis.password must be configured (env: REDIS_PASSWORD)");
		}

		// CORS allowed origins
		if (!StringUtils.hasText(corsAllowedOrigins)) {
			errors.add("cors.allowed-origins must be configured (env: CORS_ALLOWED_ORIGINS)");
		}
		else if (corsAllowedOrigins.contains("localhost") || corsAllowedOrigins.contains("127.0.0.1")) {
			errors.add("cors.allowed-origins must not contain localhost in production");
		}

		// Cookie security
		if (!cookieSecure) {
			errors.add("auth.cookie.secure must be true in production");
		}

		if (!StringUtils.hasText(cookieSameSite) || "None".equalsIgnoreCase(cookieSameSite.trim())) {
			errors.add("auth.cookie.same-site must be 'Lax' or 'Strict' in production");
		}

		if (!errors.isEmpty()) {
			String report = String.join("\n  - ", errors);
			throw new IllegalStateException(
					"Production security validation failed (" + errors.size() + " issue(s)):\n  - " + report);
		}

		log.info("[SecurityProfileValidator] All {} production security checks passed", 6);
	}

	/**
	 * [F-8] JWT secret entropy 檢查 — 偵測低熵模式（重複字元、連續序列、常見弱密碼）。
	 * @return 錯誤訊息，若通過則回傳 null
	 */
	String checkEntropy(String secret) {
		// 檢查唯一字元數量（至少需要 10 種不同字元）
		long uniqueChars = secret.chars().distinct().count();
		if (uniqueChars < 10) {
			return "jwt.secret has low entropy: only " + uniqueChars + " unique characters (minimum 10 required)";
		}

		// 檢查單一字元佔比是否過高（>40%）
		int maxRepeat = secret.chars()
			.boxed()
			.collect(java.util.stream.Collectors.groupingBy(c -> c, java.util.stream.Collectors.counting()))
			.values()
			.stream()
			.mapToInt(Long::intValue)
			.max()
			.orElse(0);
		if ((double) maxRepeat / secret.length() > 0.4) {
			return "jwt.secret has low entropy: single character repeated " + maxRepeat + " times out of "
					+ secret.length();
		}

		// 檢查常見弱密碼模式
		String lower = secret.toLowerCase();
		List<String> weakPatterns = List.of("password", "secret", "123456", "abcdef", "qwerty", "000000", "changeme");
		for (String pattern : weakPatterns) {
			if (lower.contains(pattern)) {
				return "jwt.secret contains weak pattern: '" + pattern + "'";
			}
		}

		return null;
	}

}
