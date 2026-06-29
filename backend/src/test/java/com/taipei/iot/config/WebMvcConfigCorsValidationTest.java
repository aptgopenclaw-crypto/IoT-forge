package com.taipei.iot.config;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatNoException;

/**
 * 驗證 {@link CorsProperties#validate()} 啟動檢查邏輯。 [Config v2 N-3 / F-7]
 */
class WebMvcConfigCorsValidationTest {

	private CorsProperties createProps(String... origins) {
		CorsProperties props = new CorsProperties();
		ReflectionTestUtils.setField(props, "allowedOrigins", origins);
		return props;
	}

	@Test
	void validOrigin_shouldPass() {
		CorsProperties props = createProps("https://app.example.com");
		assertThatNoException().isThrownBy(props::validate);
	}

	@Test
	void multipleValidOrigins_shouldPass() {
		CorsProperties props = createProps("https://app.example.com", "https://admin.example.com");
		assertThatNoException().isThrownBy(props::validate);
	}

	@Test
	void nullOrigins_shouldThrow() {
		CorsProperties props = new CorsProperties();
		ReflectionTestUtils.setField(props, "allowedOrigins", null);
		assertThatThrownBy(props::validate).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("CORS_ALLOWED_ORIGINS must be configured");
	}

	@Test
	void emptyArray_shouldThrow() {
		CorsProperties props = createProps();
		assertThatThrownBy(props::validate).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("CORS_ALLOWED_ORIGINS must be configured");
	}

	@Test
	void singleBlankString_shouldThrow() {
		CorsProperties props = createProps("");
		assertThatThrownBy(props::validate).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("CORS_ALLOWED_ORIGINS must be configured");
	}

	@Test
	void wildcardOrigin_shouldThrow() {
		CorsProperties props = createProps("*");
		assertThatThrownBy(props::validate).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("must not be '*'");
	}

	@Test
	void wildcardAmongOthers_shouldThrow() {
		CorsProperties props = createProps("https://app.example.com", "*");
		assertThatThrownBy(props::validate).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("must not be '*'");
	}

}
