package com.taipei.iot.config;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

/**
 * [Config v2 F-7] 驗證 CorsProperties 建立正確的 CorsConfiguration。
 */
class CorsPropertiesTest {

	private CorsProperties createProps(String... origins) {
		CorsProperties props = new CorsProperties();
		ReflectionTestUtils.setField(props, "allowedOrigins", origins);
		return props;
	}

	@Test
	void buildConfiguration_shouldContainAllowedOrigins() {
		CorsProperties props = createProps("https://app.example.com", "https://admin.example.com");
		CorsConfiguration config = props.buildConfiguration();

		assertThat(config.getAllowedOrigins()).containsExactly("https://app.example.com", "https://admin.example.com");
	}

	@Test
	void buildConfiguration_shouldSetAllowedMethods() {
		CorsProperties props = createProps("https://app.example.com");
		CorsConfiguration config = props.buildConfiguration();

		assertThat(config.getAllowedMethods()).containsExactlyInAnyOrder("GET", "POST", "PUT", "PATCH", "DELETE",
				"OPTIONS");
	}

	@Test
	void buildConfiguration_shouldSetAllowedHeaders() {
		CorsProperties props = createProps("https://app.example.com");
		CorsConfiguration config = props.buildConfiguration();

		assertThat(config.getAllowedHeaders()).containsExactlyInAnyOrder("Authorization", "Content-Type", "Accept",
				"X-Requested-With");
	}

	@Test
	void buildConfiguration_shouldSetExposedHeaders() {
		CorsProperties props = createProps("https://app.example.com");
		CorsConfiguration config = props.buildConfiguration();

		assertThat(config.getExposedHeaders()).containsExactlyInAnyOrder("Retry-After", "Content-Disposition");
	}

	@Test
	void buildConfiguration_shouldEnableCredentials() {
		CorsProperties props = createProps("https://app.example.com");
		CorsConfiguration config = props.buildConfiguration();

		assertThat(config.getAllowCredentials()).isTrue();
	}

	@Test
	void buildConfiguration_shouldSetMaxAge() {
		CorsProperties props = createProps("https://app.example.com");
		CorsConfiguration config = props.buildConfiguration();

		assertThat(config.getMaxAge()).isEqualTo(3600L);
	}

	@Test
	void buildSource_shouldRegisterForAllPaths() {
		CorsProperties props = createProps("https://app.example.com");
		CorsConfigurationSource source = props.buildSource();

		assertThat(source).isNotNull();
	}

	@Test
	void validate_validOrigins_shouldNotThrow() {
		CorsProperties props = createProps("https://app.example.com");
		assertThatNoException().isThrownBy(props::validate);
	}

}
