package com.taipei.iot.common.resolver;

import com.taipei.iot.common.annotation.PaginationParams;
import com.taipei.iot.common.dto.PageQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link PaginationArgumentResolver} 單元測試 — 涵蓋 default、邊界、錯誤輸入。
 */
class PaginationArgumentResolverTest {

	private PaginationArgumentResolver resolver;

	@BeforeEach
	void setUp() {
		resolver = new PaginationArgumentResolver();
	}

	// ─── supportsParameter ──────────────────────────────────────────────────

	@Test
	@DisplayName("supportsParameter: 標註 @PaginationParams 的 PageQuery 參數應回 true")
	void supports_pageQueryWithAnnotation() throws Exception {
		MethodParameter param = paramOf("withAnnotation", 0);
		assertThat(resolver.supportsParameter(param)).isTrue();
	}

	@Test
	@DisplayName("supportsParameter: PageQuery 但無註解應回 false")
	void doesNotSupport_pageQueryWithoutAnnotation() throws Exception {
		MethodParameter param = paramOf("withoutAnnotation", 0);
		assertThat(resolver.supportsParameter(param)).isFalse();
	}

	@Test
	@DisplayName("supportsParameter: 註解標在非 PageQuery 型別應回 false")
	void doesNotSupport_annotationOnWrongType() throws Exception {
		MethodParameter param = paramOf("annotationOnWrongType", 0);
		assertThat(resolver.supportsParameter(param)).isFalse();
	}

	// ─── resolveArgument: defaults ──────────────────────────────────────────

	@Nested
	@DisplayName("resolveArgument — default 值")
	class Defaults {

		@Test
		@DisplayName("無 page / size query string → 採用註解預設值")
		void noParam_usesDefaults() throws Exception {
			PageQuery q = resolve(req(), "withAnnotation");
			assertThat(q.getPage()).isZero();
			assertThat(q.getSize()).isEqualTo(20);
		}

		@Test
		@DisplayName("空字串 → 採用預設值")
		void blankParam_usesDefaults() throws Exception {
			PageQuery q = resolve(req("page", "  ", "size", ""), "withAnnotation");
			assertThat(q.getPage()).isZero();
			assertThat(q.getSize()).isEqualTo(20);
		}

		@Test
		@DisplayName("自訂 defaultSize 生效")
		void customDefaultSize() throws Exception {
			PageQuery q = resolve(req(), "withCustomDefaults");
			assertThat(q.getPage()).isZero();
			assertThat(q.getSize()).isEqualTo(10);
		}

	}

	// ─── resolveArgument: normal values ─────────────────────────────────────

	@Test
	@DisplayName("正常傳值")
	void resolve_normalValues() throws Exception {
		PageQuery q = resolve(req("page", "2", "size", "50"), "withAnnotation");
		assertThat(q.getPage()).isEqualTo(2);
		assertThat(q.getSize()).isEqualTo(50);
	}

	@Test
	@DisplayName("size 等於 maxSize 邊界 → 通過")
	void resolve_sizeAtMaxBoundary() throws Exception {
		PageQuery q = resolve(req("size", "100"), "withAnnotation");
		assertThat(q.getSize()).isEqualTo(100);
	}

	// ─── resolveArgument: invalid values ────────────────────────────────────

	@Nested
	@DisplayName("resolveArgument — 邊界 / 錯誤輸入應拋 400")
	class Invalid {

		@Test
		@DisplayName("page 為負數 → 400")
		void negativePage() {
			assertThatThrownBy(() -> resolve(req("page", "-1"), "withAnnotation"))
				.isInstanceOf(ResponseStatusException.class)
				.hasMessageContaining("page");
		}

		@Test
		@DisplayName("size = 0 → 400")
		void zeroSize() {
			assertThatThrownBy(() -> resolve(req("size", "0"), "withAnnotation"))
				.isInstanceOf(ResponseStatusException.class)
				.hasMessageContaining("size");
		}

		@Test
		@DisplayName("size 超過 maxSize → 400")
		void sizeExceedsMax() {
			assertThatThrownBy(() -> resolve(req("size", "200"), "withAnnotation"))
				.isInstanceOf(ResponseStatusException.class)
				.hasMessageContaining("不得超過");
		}

		@Test
		@DisplayName("自訂 maxSize=50, 傳 51 → 400")
		void customMaxSizeExceeded() {
			assertThatThrownBy(() -> resolve(req("size", "51"), "withSmallMax"))
				.isInstanceOf(ResponseStatusException.class)
				.hasMessageContaining("50");
		}

		@Test
		@DisplayName("page 為非數字 → 400")
		void nonNumericPage() {
			assertThatThrownBy(() -> resolve(req("page", "abc"), "withAnnotation"))
				.isInstanceOf(ResponseStatusException.class)
				.hasMessageContaining("page");
		}

		@Test
		@DisplayName("size 為非數字 → 400")
		void nonNumericSize() {
			assertThatThrownBy(() -> resolve(req("size", "xx"), "withAnnotation"))
				.isInstanceOf(ResponseStatusException.class)
				.hasMessageContaining("size");
		}

	}

	// ─── PageQuery 自身行為 ────────────────────────────────────────────────

	@Test
	@DisplayName("PageQuery.toPageRequest() 應產生對應的 PageRequest")
	void pageQuery_toPageRequest() throws Exception {
		PageQuery q = resolve(req("page", "3", "size", "25"), "withAnnotation");
		assertThat(q.toPageRequest().getPageNumber()).isEqualTo(3);
		assertThat(q.toPageRequest().getPageSize()).isEqualTo(25);
	}

	// ─── helpers ────────────────────────────────────────────────────────────

	private PageQuery resolve(MockHttpServletRequest req, String methodName) throws Exception {
		MethodParameter param = paramOf(methodName, 0);
		Object result = resolver.resolveArgument(param, null, new ServletWebRequest(req), null);
		assertThat(result).isInstanceOf(PageQuery.class);
		return (PageQuery) result;
	}

	private MethodParameter paramOf(String methodName, int idx) throws NoSuchMethodException {
		for (Method m : Fixture.class.getDeclaredMethods()) {
			if (m.getName().equals(methodName)) {
				return new MethodParameter(m, idx);
			}
		}
		throw new NoSuchMethodException(methodName);
	}

	private static MockHttpServletRequest req() {
		return new MockHttpServletRequest();
	}

	private static MockHttpServletRequest req(String name, String value) {
		MockHttpServletRequest r = new MockHttpServletRequest();
		r.setParameter(name, value);
		return r;
	}

	private static MockHttpServletRequest req(String n1, String v1, String n2, String v2) {
		MockHttpServletRequest r = new MockHttpServletRequest();
		r.setParameter(n1, v1);
		r.setParameter(n2, v2);
		return r;
	}

	@SuppressWarnings("unused")
	static class Fixture {

		void withAnnotation(@PaginationParams PageQuery query) {
		}

		void withoutAnnotation(PageQuery query) {
		}

		void annotationOnWrongType(@PaginationParams String wrong) {
		}

		void withCustomDefaults(@PaginationParams(defaultSize = 10) PageQuery query) {
		}

		void withSmallMax(@PaginationParams(maxSize = 50) PageQuery query) {
		}

	}

}
