package com.taipei.iot.common.interceptor;

import com.taipei.iot.common.annotation.DeprecatedApi;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * [2.1.4] Unit test for {@link DeprecatedApiInterceptor}: verifies RFC 8594
 * {@code Deprecation} / {@code Link} / {@code Sunset} headers are written based on
 * {@link DeprecatedApi} placement (method-level wins over class-level), and that
 * non-annotated or non-{@link HandlerMethod} handlers receive no headers.
 */
class DeprecatedApiInterceptorTest {

	private DeprecatedApiInterceptor interceptor;

	private HttpServletRequest request;

	private MockHttpServletResponse response;

	@BeforeEach
	void setUp() {
		interceptor = new DeprecatedApiInterceptor();
		request = new MockHttpServletRequest();
		response = new MockHttpServletResponse();
	}

	static class Fixtures {

		@DeprecatedApi(successor = "/v1/platform/new", sunset = "2026-12-31")
		void methodLevelWithSunset() {
		}

		@DeprecatedApi(successor = "/v1/platform/another")
		void methodLevelNoSunset() {
		}

		void plainMethod() {
		}

	}

	@DeprecatedApi(successor = "/v1/platform/class-level")
	static class ClassLevelFixture {

		void inheritedDeprecation() {
		}

	}

	private HandlerMethod handler(Class<?> beanType, String method) throws NoSuchMethodException {
		Object bean = beanType == Fixtures.class ? new Fixtures() : new ClassLevelFixture();
		Method m = beanType.getDeclaredMethod(method);
		return new HandlerMethod(bean, m);
	}

	@Test
	void methodLevelAnnotation_writesDeprecationLinkAndSunset() throws Exception {
		interceptor.preHandle(request, response, handler(Fixtures.class, "methodLevelWithSunset"));

		assertThat(response.getHeader("Deprecation")).isEqualTo("true");
		assertThat(response.getHeader("Link")).isEqualTo("</v1/platform/new>; rel=\"successor-version\"");
		assertThat(response.getHeader("Sunset")).isEqualTo("2026-12-31");
	}

	@Test
	void methodLevelAnnotation_withoutSunset_omitsSunsetHeader() throws Exception {
		interceptor.preHandle(request, response, handler(Fixtures.class, "methodLevelNoSunset"));

		assertThat(response.getHeader("Deprecation")).isEqualTo("true");
		assertThat(response.getHeader("Link")).isEqualTo("</v1/platform/another>; rel=\"successor-version\"");
		assertThat(response.getHeader("Sunset")).isNull();
	}

	@Test
	void classLevelAnnotation_appliesToUnannotatedMethod() throws Exception {
		interceptor.preHandle(request, response, handler(ClassLevelFixture.class, "inheritedDeprecation"));

		assertThat(response.getHeader("Deprecation")).isEqualTo("true");
		assertThat(response.getHeader("Link")).isEqualTo("</v1/platform/class-level>; rel=\"successor-version\"");
	}

	@Test
	void noAnnotation_writesNoHeaders() throws Exception {
		interceptor.preHandle(request, response, handler(Fixtures.class, "plainMethod"));

		assertThat(response.getHeader("Deprecation")).isNull();
		assertThat(response.getHeader("Link")).isNull();
		assertThat(response.getHeader("Sunset")).isNull();
	}

	@Test
	void nonHandlerMethod_isNoOp() {
		boolean proceed = interceptor.preHandle(request, response, new Object());

		assertThat(proceed).isTrue();
		assertThat(response.getHeader("Deprecation")).isNull();
	}

}
