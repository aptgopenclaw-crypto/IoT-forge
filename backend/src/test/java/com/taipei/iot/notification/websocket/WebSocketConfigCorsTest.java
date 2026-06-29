package com.taipei.iot.notification.websocket;

import com.taipei.iot.config.CorsProperties;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * [N-9] 驗證 WebSocketConfig 啟動時對 CORS allowedOrigins 的 fail-fast 檢查。
 */
class WebSocketConfigCorsTest {

	private WebSocketConfig createConfig(String... origins) {
		CorsProperties props = new CorsProperties();
		ReflectionTestUtils.setField(props, "allowedOrigins", origins);
		return new WebSocketConfig(props);
	}

	@Test
	void validOrigins_shouldPassValidation() {
		WebSocketConfig config = createConfig("https://app.example.com");
		assertThatNoException().isThrownBy(config::validateAndLog);
	}

	@Test
	void multipleValidOrigins_shouldPassValidation() {
		WebSocketConfig config = createConfig("https://app.example.com", "https://admin.example.com");
		assertThatNoException().isThrownBy(config::validateAndLog);
	}

	@Test
	void wildcardOrigin_shouldFailFast() {
		WebSocketConfig config = createConfig("*");
		assertThatThrownBy(config::validateAndLog).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("must not contain '*'");
	}

	@Test
	void wildcardAmongValidOrigins_shouldFailFast() {
		WebSocketConfig config = createConfig("https://app.example.com", "*");
		assertThatThrownBy(config::validateAndLog).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("must not contain '*'");
	}

	@Test
	void nullOrigins_shouldFailFast() {
		CorsProperties props = new CorsProperties();
		ReflectionTestUtils.setField(props, "allowedOrigins", null);
		WebSocketConfig config = new WebSocketConfig(props);
		assertThatThrownBy(config::validateAndLog).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("allowedOrigins must be configured");
	}

	@Test
	void emptyOrigins_shouldFailFast() {
		WebSocketConfig config = createConfig();
		assertThatThrownBy(config::validateAndLog).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("allowedOrigins must be configured");
	}

}
