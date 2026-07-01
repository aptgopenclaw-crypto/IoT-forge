package com.taipei.iot.ingest.source.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taipei.iot.common.enums.ErrorCode;
import com.taipei.iot.common.response.BaseResponse;
import com.taipei.iot.ingest.client.TelemetryIngestClientRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * {@code /v1/ingest/**} 專用 SecurityFilterChain（M2M API key 認證，與使用者 JWT 鏈完全隔離）。
 * <p>
 * 以 {@code @Order(0)} 優先於 swagger（1）與主鏈（2）；憑路徑 {@code securityMatcher} 套用。stateless、停用
 * CSRF（純 header 認證、無 cookie），通過後須持有 {@code INGEST} 權限。本配置自我包含於 ingest 模組，不更動 auth 模組的主鏈。
 */
@Configuration
public class IngestSecurityConfig {

	@Bean
	@Order(0)
	public SecurityFilterChain ingestFilterChain(HttpSecurity http, TelemetryIngestClientRepository clientRepository,
			PasswordEncoder passwordEncoder, ObjectMapper objectMapper) throws Exception {
		IngestApiKeyAuthFilter apiKeyAuthFilter = new IngestApiKeyAuthFilter(clientRepository, passwordEncoder,
				objectMapper);
		return http.securityMatcher("/v1/ingest/**")
			.cors(Customizer.withDefaults())
			.csrf(csrf -> csrf.disable())
			.sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.authorizeHttpRequests(auth -> auth.anyRequest().hasAuthority("INGEST"))
			.exceptionHandling(ex -> ex.authenticationEntryPoint((request, response, authException) -> {
				response.setStatus(HttpStatus.UNAUTHORIZED.value());
				response.setContentType(MediaType.APPLICATION_JSON_VALUE);
				response.setCharacterEncoding("UTF-8");
				response.getWriter()
					.write(objectMapper
						.writeValueAsString(BaseResponse.fail(ErrorCode.IOT_INGEST_CREDENTIALS_INVALID)));
			}).accessDeniedHandler((request, response, accessDeniedException) -> {
				response.setStatus(HttpStatus.FORBIDDEN.value());
				response.setContentType(MediaType.APPLICATION_JSON_VALUE);
				response.setCharacterEncoding("UTF-8");
				response.getWriter()
					.write(objectMapper.writeValueAsString(BaseResponse.fail(ErrorCode.PERMISSION_DENIED)));
			}))
			.addFilterBefore(apiKeyAuthFilter, UsernamePasswordAuthenticationFilter.class)
			.build();
	}

}
