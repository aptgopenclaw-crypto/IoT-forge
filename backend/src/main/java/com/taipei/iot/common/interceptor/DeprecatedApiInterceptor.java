package com.taipei.iot.common.interceptor;

import com.taipei.iot.common.annotation.DeprecatedApi;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Adds RFC 8594 {@code Deprecation} / {@code Link} / {@code Sunset} response headers for
 * handlers (or controller classes) annotated with {@link DeprecatedApi}. Used by Phase
 * 2.1.4 to advertise that the legacy {@code /v1/auth/**} routes have a canonical
 * successor under {@code /v1/platform/**}.
 *
 * <p>
 * Method-level annotation wins over class-level. The interceptor is a no-op for
 * non-{@link HandlerMethod} requests (e.g. static resources, default servlet).
 */
@Component
public class DeprecatedApiInterceptor implements HandlerInterceptor {

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
		if (!(handler instanceof HandlerMethod hm)) {
			return true;
		}
		DeprecatedApi ann = hm.getMethodAnnotation(DeprecatedApi.class);
		if (ann == null) {
			ann = hm.getBeanType().getAnnotation(DeprecatedApi.class);
		}
		if (ann == null) {
			return true;
		}
		response.setHeader("Deprecation", "true");
		if (!ann.successor().isBlank()) {
			response.setHeader("Link", "<" + ann.successor() + ">; rel=\"successor-version\"");
		}
		if (!ann.sunset().isBlank()) {
			response.setHeader("Sunset", ann.sunset());
		}
		return true;
	}

}
