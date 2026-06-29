package com.taipei.iot.config;

import com.taipei.iot.auth.security.JwtAuthenticationFilter;
import com.taipei.iot.auth.security.ScopeEnforcementFilter;
import com.taipei.iot.common.enums.ErrorCode;
import com.taipei.iot.common.enums.SecurityEvent;
import com.taipei.iot.common.response.BaseResponse;
import com.taipei.iot.common.util.SecurityLogger;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.PermissionsPolicyHeaderWriter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.header.writers.XXssProtectionHeaderWriter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

	private final JwtAuthenticationFilter jwtAuthenticationFilter;

	private final ScopeEnforcementFilter scopeEnforcementFilter;

	private final ObjectMapper objectMapper;

	/**
	 * Swagger/OpenAPI 專用 FilterChain — 僅在 springdoc 啟用時註冊， 確保生產環境不會意外暴露 API 文件。
	 */
	@Bean
	@Order(1)
	@ConditionalOnProperty(name = "springdoc.api-docs.enabled", havingValue = "true")
	public SecurityFilterChain swaggerFilterChain(HttpSecurity http) throws Exception {
		return http.securityMatcher("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
			.authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
			.csrf(csrf -> csrf.disable())
			.build();
	}

	@Bean
	@Order(2)
	public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
		return http.cors(Customizer.withDefaults()) // 委派給
													// WebMvcConfig.addCorsMappings()；確保
													// preflight OPTIONS 不被 Security 攔截
			.csrf(csrf -> csrf.disable())
			// ====== 安全 Headers 設定 ======
			.headers(headers -> headers
				// HSTS (HTTP Strict Transport Security)
				// 沒設 → 攻擊者可用中間人攻擊（MITM）將 HTTPS 降級為 HTTP，竊聽或篡改傳輸中的資料
				.httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31536000)) // 1
																												// 年
				// CSP (Content Security Policy)
				// 沒設 → 攻擊者可注入外部惡意 script/style/iframe，執行 XSS 攻擊竊取使用者 cookie 或操作頁面
				.contentSecurityPolicy(csp -> csp.policyDirectives(
						"default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data: https://wmts.nlsc.gov.tw; font-src 'self' data:; frame-ancestors 'none'; object-src 'none'; base-uri 'self'; form-action 'self'"))
				// Referrer-Policy
				// 沒設 → 瀏覽器跳轉到外部網站時，完整的 URL（可能包含 token、session ID）會透過 Referer header
				// 洩漏給第三方
				.referrerPolicy(referrer -> referrer
					.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
				// Permissions-Policy
				// 沒設 → 惡意第三方 iframe 或 script 可以偷偷啟用攝影機、麥克風、定位等敏感裝置 API
				.addHeaderWriter(
						new PermissionsPolicyHeaderWriter("camera=(), microphone=(), geolocation=(), payment=()"))
				// N-7: X-XSS-Protection: 0 — 顯式關閉舊瀏覽器 buggy XSS auditor（OWASP 建議）
				.xssProtection(xss -> xss.headerValue(XXssProtectionHeaderWriter.HeaderValue.DISABLED))
			// X-Frame-Options（已有 DENY，保持不變）
			// 沒設 → 攻擊者可用 iframe 嵌入你的頁面，誘導使用者點擊隱藏按鈕（Clickjacking 點擊劫持）
			// X-Content-Type-Options（Spring Security 預設已加 nosniff，保持不變）
			// 沒設 → 瀏覽器可能「猜測」檔案類型，把惡意上傳的檔案當成 JS 執行（MIME Sniffing 攻擊）
			)
			.sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.exceptionHandling(ex -> ex.authenticationEntryPoint((request, response, authException) -> {
				response.setStatus(HttpStatus.UNAUTHORIZED.value());
				response.setContentType(MediaType.APPLICATION_JSON_VALUE);
				response.setCharacterEncoding("UTF-8");
				String body = objectMapper.writeValueAsString(BaseResponse.fail(ErrorCode.ACCESS_TOKEN_INVALID));
				response.getWriter().write(body);
			}).accessDeniedHandler((request, response, accessDeniedException) -> {
				SecurityLogger.warn(SecurityEvent.ACCESS_DENIED, request.getRemoteAddr(),
						"path=" + request.getRequestURI(), "user=" + (request.getUserPrincipal() != null
								? request.getUserPrincipal().getName() : "anonymous"));
				response.setStatus(HttpStatus.FORBIDDEN.value());
				response.setContentType(MediaType.APPLICATION_JSON_VALUE);
				response.setCharacterEncoding("UTF-8");
				String body = objectMapper.writeValueAsString(BaseResponse.fail(ErrorCode.PERMISSION_DENIED));
				response.getWriter().write(body);
			}))
			.authorizeHttpRequests(auth -> auth.requestMatchers("/v1/noauth/**")
				.permitAll()
				// WebSocket handshake (auth via STOMP headers)
				.requestMatchers("/ws/**")
				.permitAll()
				// F-4: Actuator health（含 /actuator/health/clamav）對外公開供 LB / 監控系統 probe
				.requestMatchers("/actuator/health", "/actuator/health/**")
				.permitAll()
				// LOGOUT: 允許未驗證（或 token 已過期）的請求進行登出，避免 token 過期時
				// 觸發 Spring Security 寫入 401（getWriter()），導致後續回應寫入衝突
				.requestMatchers(HttpMethod.POST, "/v1/auth/logout")
				.permitAll()
				// IDLE-LOGOUT: explicit (authenticated) — idle timeout 仍需已驗證
				.requestMatchers(HttpMethod.POST, "/v1/auth/idle-logout")
				.authenticated()
				// N-4: 其餘 actuator 端點限制為 SYSTEM_OPS 權限（防內部帳號濫用 /actuator/env 等）
				.requestMatchers("/actuator/**")
				.hasAuthority("SYSTEM_OPS")
				// USER: self-service (authenticated)
				.requestMatchers("/v1/auth/user/my", "/v1/auth/user/change-password")
				.authenticated()
				// USER: admin — permission-based (細部由 @PreAuthorize 控制)
				.requestMatchers("/v1/auth/users/**")
				.hasAnyAuthority("USER_LIST", "USER_CREATE", "USER_UPDATE", "USER_DISABLE", "USER_DELETE")
				// RBAC: roles query
				.requestMatchers(HttpMethod.GET, "/v1/auth/roles/**")
				.hasAuthority("ROLE_LIST")
				// RBAC: roles mutation
				.requestMatchers(HttpMethod.POST, "/v1/auth/roles")
				.hasAuthority("ROLE_CREATE")
				.requestMatchers(HttpMethod.PUT, "/v1/auth/roles/**")
				.hasAnyAuthority("ROLE_UPDATE", "ROLE_ASSIGN_PERM")
				.requestMatchers(HttpMethod.PATCH, "/v1/auth/roles/**")
				.hasAuthority("ROLE_UPDATE")
				// RBAC: permissions query (same gate as role list)
				.requestMatchers(HttpMethod.GET, "/v1/auth/permissions/**")
				.hasAuthority("ROLE_LIST")
				// RBAC: menus — user visible menus (authenticated)
				.requestMatchers(HttpMethod.GET, "/v1/auth/menus/my")
				.authenticated()
				// RBAC: menus — admin tree view
				.requestMatchers(HttpMethod.GET, "/v1/auth/menus/tree")
				.hasAuthority("MENU_LIST")
				// RBAC: menus CRUD
				.requestMatchers(HttpMethod.POST, "/v1/auth/menus/**")
				.hasAuthority("MENU_CREATE")
				.requestMatchers(HttpMethod.PUT, "/v1/auth/menus/**")
				.hasAuthority("MENU_UPDATE")
				.requestMatchers(HttpMethod.DELETE, "/v1/auth/menus/**")
				.hasAuthority("MENU_DELETE")
				.requestMatchers(HttpMethod.PATCH, "/v1/auth/menus/**")
				.hasAuthority("MENU_UPDATE")
				// AUDIT: personal log (authenticated)
				.requestMatchers("/v1/auth/audit/user/login/my")
				.authenticated()
				// AUDIT: admin queries
				.requestMatchers("/v1/auth/audit/**")
				.hasAnyAuthority("AUDIT_LIST", "LOGIN_LOG_LIST")
				// DEPT: tree list
				.requestMatchers(HttpMethod.GET, "/v1/auth/dept/list")
				.hasAuthority("DEPT_LIST")
				// DEPT: options / single dept (authenticated — used by dropdowns)
				.requestMatchers(HttpMethod.GET, "/v1/auth/dept/options")
				.authenticated()
				.requestMatchers(HttpMethod.GET, "/v1/auth/dept/scope-options")
				.authenticated()
				.requestMatchers(HttpMethod.GET, "/v1/auth/dept/{deptId}")
				.authenticated()
				// DEPT: CRUD is controlled by @PreAuthorize on controller
				// TENANT ADMIN: platform-scope permission (held only by SUPER_ADMIN via
				// auto-grant)
				// [Platform/Tenant Separation 2.1.1] path moved to
				// /v1/platform/tenants/**
				.requestMatchers("/v1/platform/tenants/**")
				.hasAuthority("PLATFORM_TENANT_MANAGE")
				// POC: 簽核引擎開發驗證端點（需 authenticated，不限特定權限）
				.requestMatchers("/v1/api/poc/**")
				.authenticated()
				// 資產異動申請 API（細部由 @PreAuthorize 控制）
				.requestMatchers("/v1/auth/asset-transfer/**")
				.hasAnyAuthority("ASSET_TRANSFER_VIEW", "ASSET_TRANSFER_CREATE", "ASSET_TRANSFER_APPROVE")
				// LOG-SUMMARY: all GET endpoints (authenticated)
				.requestMatchers(HttpMethod.GET, "/v1/log/**")
				.authenticated()
				.anyRequest()
				.authenticated())
			.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
			// [ADR-007] Phase 1.1.2 — observe scope claim vs path prefix in warning mode.
			// Runs after JwtAuthenticationFilter so Authentication details are populated.
			.addFilterAfter(scopeEnforcementFilter, JwtAuthenticationFilter.class)
			.build();
	}

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

}
