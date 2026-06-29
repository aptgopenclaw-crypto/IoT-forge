package com.taipei.iot.config;

import brave.Tracing;
import brave.context.slf4j.MDCScopeDecorator;
import brave.propagation.CurrentTraceContext;
import brave.propagation.ThreadLocalCurrentTraceContext;
import brave.propagation.TraceContext;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

import java.io.IOException;

/**
 * Explicit Brave Tracing 配置。
 *
 * <p>
 * Spring Boot 3.x 的 {@code micrometer-tracing-bridge-brave} auto-configuration 雖然會建立
 * {@link Tracing} bean，但在自訂 {@code SecurityFilterChain} 的情境下，auto-configuration 的
 * {@code HttpServerRequestsObservationFilter} 可能未被正確掛入 filter chain， 導致 MDC 中沒有
 * {@code traceId} / {@code spanId}。
 *
 * <p>
 * 此類用 {@link MDCScopeDecorator} 建立 {@link CurrentTraceContext}，確保 Brave 的 traceId /
 * spanId 會自動注入 SLF4J MDC。同時註冊一個自訂 servlet filter（執行順序在 auto-configuration 的
 * {@code HttpServerRequestsObservationFilter} 之後）， 從 {@link Tracing} 取出當前
 * {@link TraceContext} 並補寫入 MDC，作為雙重保障。
 *
 * <p>
 * 若 {@link Tracing} bean 已由 auto-configuration 提供（預設行為），則此類的 {@code tracing()} 方法不會覆蓋（由
 * {@link ConditionalOnMissingBean} 保護）。
 */
@Configuration
public class TracingConfig {

	/**
	 * 建立 Brave Tracing 實例，並掛載 {@link MDCScopeDecorator} 以將 traceId / spanId 自動注入 SLF4J
	 * MDC。
	 * <p>
	 * {@code @ConditionalOnMissingBean} 確保不覆蓋 Spring Boot auto-configuration 已產生的
	 * {@link Tracing} bean。
	 */
	@Bean
	@ConditionalOnMissingBean
	public Tracing tracing() {
		return Tracing.newBuilder()
			.currentTraceContext(
					ThreadLocalCurrentTraceContext.newBuilder().addScopeDecorator(MDCScopeDecorator.get()).build())
			.build();
	}

	/**
	 * 註冊自訂 servlet filter，從 {@link Tracing} 取出當前 {@link TraceContext} 並寫入 MDC。
	 * <p>
	 * Auto-configuration 的 {@code HttpServerRequestsObservationFilter} 預設 order 為
	 * {@link Ordered#HIGHEST_PRECEDENCE} + 10，此 filter 設為 +15 以確保在 observation 建立 span
	 * 之後才讀取 trace context。
	 */
	@Bean
	public FilterRegistrationBean<Filter> mdcTracingFilter(Tracing tracing) {
		FilterRegistrationBean<Filter> registrationBean = new FilterRegistrationBean<>();
		registrationBean.setFilter(new Filter() {
			@Override
			public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
					throws IOException, ServletException {
				TraceContext context = tracing.currentTraceContext().get();
				if (context != null) {
					try (MDC.MDCCloseable mdcTraceId = MDC.putCloseable("traceId", context.traceIdString());
							MDC.MDCCloseable mdcSpanId = MDC.putCloseable("spanId", context.spanIdString())) {
						chain.doFilter(request, response);
					}
				}
				else {
					chain.doFilter(request, response);
				}
			}
		});
		registrationBean.setOrder(Ordered.HIGHEST_PRECEDENCE + 15);
		registrationBean.addUrlPatterns("/*");
		registrationBean.setName("mdcTracingFilter");
		return registrationBean;
	}

}
