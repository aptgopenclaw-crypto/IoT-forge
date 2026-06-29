package com.taipei.iot.config;

import com.taipei.iot.common.interceptor.DeprecatedApiInterceptor;
import com.taipei.iot.common.interceptor.RateLimitInterceptor;
import com.taipei.iot.common.resolver.PaginationArgumentResolver;
import com.taipei.iot.tenant.TenantInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

	private final RateLimitInterceptor rateLimitInterceptor;

	private final TenantInterceptor tenantInterceptor;

	private final DeprecatedApiInterceptor deprecatedApiInterceptor;

	private final CorsProperties corsProperties;

	@Override
	public void addCorsMappings(CorsRegistry registry) {
		registry.addMapping("/**")
			.allowedOrigins(corsProperties.getAllowedOrigins())
			.allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
			.allowedHeaders("Authorization", "Content-Type", "Accept", "X-Requested-With")
			.exposedHeaders("Retry-After", "Content-Disposition", "Deprecation", "Link", "Sunset")
			.allowCredentials(true)
			.maxAge(3600);
	}

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		// [F-1] 健康檢查 / LB probe 路徑 — 排除限流與租戶攔截，避免 probe 被誤限流或缺 tenant 報錯
		String[] healthProbeExcludes = { "/v1/health", "/v1/healthz", "/v1/ready" };

		// 速率限制攔截器 — 必須在 TenantInterceptor 之前，確保限流在業務邏輯前生效
		registry.addInterceptor(rateLimitInterceptor)
			.addPathPatterns("/v1/**")
			.excludePathPatterns(healthProbeExcludes);

		// [Tenant v2 T-10] 明確列出 TenantInterceptor 的攔截/排除範圍，避免「靠 SecurityConfig
		// 白名單副作用」造成的隱性耦合：
		// - addPathPatterns("/v1/**") 業務 API 才需要租戶上下文
		// - excludePathPatterns("/v1/noauth/**") 公開端點（login / captcha / refresh /
		// forgot-password / force-change-password / turnstile-config）尚未經 JWT
		// 設定 tenantId，本就不需要也不應依賴 TenantInterceptor 的 single-mode 覆寫。
		// Swagger（/v3/api-docs, /swagger-ui）、actuator、/ws/** 因不在 /v1/** 範圍內，
		// 天然就不會被攔截。
		registry.addInterceptor(tenantInterceptor)
			.addPathPatterns("/v1/**")
			.excludePathPatterns("/v1/noauth/**")
			.excludePathPatterns(healthProbeExcludes);

		// [Platform/Tenant Separation 2.1.4] RFC 8594 Deprecation/Link/Sunset headers
		// for legacy /v1/auth/** routes that have a /v1/platform/** successor.
		registry.addInterceptor(deprecatedApiInterceptor)
			.addPathPatterns("/v1/**")
			.excludePathPatterns(healthProbeExcludes);
	}

	/**
	 * [common v2 F-9] 註冊 {@link PaginationArgumentResolver}，讓 Controller 方法可使用
	 * {@code @PaginationParams PageQuery page} 取代散落各處的
	 * {@code @RequestParam(defaultValue="0") @Min(0) int page, @RequestParam(defaultValue="20") @Min(1) @Max(100)
	 * int size}。
	 */
	@Override
	public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
		resolvers.add(new PaginationArgumentResolver());
	}

}
