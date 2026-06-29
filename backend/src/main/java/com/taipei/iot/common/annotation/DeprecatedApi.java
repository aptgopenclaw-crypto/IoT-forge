package com.taipei.iot.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a controller class or handler method as a deprecated HTTP endpoint.
 *
 * <p>
 * Picked up by {@code DeprecatedApiInterceptor} which writes RFC 8594 compatible response
 * headers:
 * <ul>
 * <li>{@code Deprecation: true}</li>
 * <li>{@code Link: <successor>; rel="successor-version"} — when {@link #successor()} is
 * set</li>
 * <li>{@code Sunset: <sunset>} — when {@link #sunset()} is set (RFC 3339 or
 * HTTP-date)</li>
 * </ul>
 *
 * <p>
 * Used by Phase 2.1.4 of the platform/tenant URL separation to advertise the legacy
 * {@code /v1/auth/**} routes whose canonical counterparts now live under
 * {@code /v1/platform/**}.
 */
@Target({ ElementType.METHOD, ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
public @interface DeprecatedApi {

	/**
	 * Successor URL or path template, e.g.
	 * {@code /v1/platform/tenants/{tenantId}/auth-config}.
	 */
	String successor() default "";

	/** Optional sunset date in RFC 3339 / HTTP-date format. */
	String sunset() default "";

}
