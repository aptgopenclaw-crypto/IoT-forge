package com.taipei.iot.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * [Config v2 F-7] 集中化 CORS 設定 — 提供單一 {@link CorsConfigurationSource} bean， 供
 * {@link WebMvcConfig} 和 {@link com.taipei.iot.notification.websocket.WebSocketConfig}
 * 共用。
 */
@Component
@Getter
public class CorsProperties {

	private static final List<String> ALLOWED_METHODS = List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");

	private static final List<String> ALLOWED_HEADERS = List.of("Authorization", "Content-Type", "Accept",
			"X-Requested-With");

	private static final List<String> EXPOSED_HEADERS = List.of("Retry-After", "Content-Disposition");

	private static final long MAX_AGE = 3600L;

	@Value("${cors.allowed-origins}")
	private String[] allowedOrigins;

	@PostConstruct
	void validate() {
		if (allowedOrigins == null || allowedOrigins.length == 0
				|| (allowedOrigins.length == 1 && !StringUtils.hasText(allowedOrigins[0]))) {
			throw new IllegalStateException(
					"CORS_ALLOWED_ORIGINS must be configured (set environment variable or cors.allowed-origins property)");
		}
		for (String origin : allowedOrigins) {
			if ("*".equals(origin.trim())) {
				throw new IllegalStateException("CORS_ALLOWED_ORIGINS must not be '*' when allowCredentials=true");
			}
		}
	}

	/**
	 * 建立標準 CorsConfiguration，供 API 與 WebSocket 共用。
	 */
	public CorsConfiguration buildConfiguration() {
		CorsConfiguration config = new CorsConfiguration();
		config.setAllowedOrigins(Arrays.asList(allowedOrigins));
		config.setAllowedMethods(ALLOWED_METHODS);
		config.setAllowedHeaders(ALLOWED_HEADERS);
		config.setExposedHeaders(EXPOSED_HEADERS);
		config.setAllowCredentials(true);
		config.setMaxAge(MAX_AGE);
		return config;
	}

	/**
	 * 建立 URL-based CorsConfigurationSource（適用於 Spring MVC / Security）。
	 */
	public CorsConfigurationSource buildSource() {
		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", buildConfiguration());
		return source;
	}

}
