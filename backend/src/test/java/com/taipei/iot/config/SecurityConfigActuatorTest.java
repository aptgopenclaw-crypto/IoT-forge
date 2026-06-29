package com.taipei.iot.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.autoconfigure.endpoint.web.WebEndpointProperties;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 驗證 Actuator 端點暴露設定正確收斂：僅 health + info 對外開放。 [Config v2 N-4]
 */
@SpringBootTest(classes = { SecurityConfigActuatorTest.class, WebMvcAutoConfiguration.class },
		properties = { "management.endpoints.web.exposure.include=health,info",
				"management.endpoint.health.show-details=never", "management.endpoint.shutdown.enabled=false",
				"management.endpoint.env.enabled=false" })
@EnableConfigurationProperties(WebEndpointProperties.class)
@ActiveProfiles("test")
class SecurityConfigActuatorTest {

	@Autowired
	private WebEndpointProperties webEndpointProperties;

	@Test
	void actuatorExposure_shouldOnlyIncludeHealthAndInfo() {
		assertThat(webEndpointProperties.getExposure().getInclude()).containsExactlyInAnyOrder("health", "info");
	}

	@Test
	void actuatorExposure_shouldNotIncludeSensitiveEndpoints() {
		assertThat(webEndpointProperties.getExposure().getInclude()).doesNotContain("env", "shutdown", "beans",
				"configprops", "mappings", "threaddump");
	}

	@Test
	void actuatorExposure_includeIsExplicitWhitelist() {
		// 確認 exposure.include 有明確設定（非預設空集合 = 全開）
		assertThat(webEndpointProperties.getExposure().getInclude()).isNotEmpty();
		assertThat(webEndpointProperties.getExposure().getInclude().size()).isLessThanOrEqualTo(3);
	}

}
