package com.taipei.iot.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * [Config v2 N-5] 驗證 JacksonConfig 啟用 FAIL_ON_UNKNOWN_PROPERTIES。
 */
@SpringBootTest(classes = { JacksonConfig.class, JacksonAutoConfiguration.class })
@ActiveProfiles("test")
class JacksonConfigFailOnUnknownTest {

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	void failOnUnknownProperties_shouldBeEnabled() {
		assertThat(objectMapper.isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)).isTrue();
	}

	@Test
	void deserialize_withKnownFields_shouldSucceed() throws Exception {
		String json = """
				{"name":"test","age":25}
				""";
		SampleDto dto = objectMapper.readValue(json, SampleDto.class);
		assertThat(dto.name).isEqualTo("test");
		assertThat(dto.age).isEqualTo(25);
	}

	@Test
	void deserialize_withUnknownField_shouldThrow() {
		String json = """
				{"name":"test","age":25,"unknownField":"hacker"}
				""";
		assertThatThrownBy(() -> objectMapper.readValue(json, SampleDto.class))
			.isInstanceOf(UnrecognizedPropertyException.class)
			.hasMessageContaining("unknownField");
	}

	@Test
	void deserialize_withExtraNestedField_shouldThrow() {
		String json = """
				{"name":"test","age":25,"extra":{"nested":true}}
				""";
		assertThatThrownBy(() -> objectMapper.readValue(json, SampleDto.class))
			.isInstanceOf(UnrecognizedPropertyException.class);
	}

	static class SampleDto {

		public String name;

		public int age;

	}

}
