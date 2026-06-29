package com.taipei.iot.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * [Config v2 N-9] 驗證 SecurityProfileValidator 各項安全檢核邏輯。
 */
class SecurityProfileValidatorTest {

	private SecurityProfileValidator createValidator(String jwtSecret, String dbPassword, String redisPassword,
			String corsOrigins, boolean cookieSecure, String sameSite) {
		SecurityProfileValidator v = new SecurityProfileValidator();
		ReflectionTestUtils.setField(v, "jwtSecret", jwtSecret);
		ReflectionTestUtils.setField(v, "dbPassword", dbPassword);
		ReflectionTestUtils.setField(v, "redisPassword", redisPassword);
		ReflectionTestUtils.setField(v, "corsAllowedOrigins", corsOrigins);
		ReflectionTestUtils.setField(v, "cookieSecure", cookieSecure);
		ReflectionTestUtils.setField(v, "cookieSameSite", sameSite);
		return v;
	}

	@Test
	void validate_allCorrect_shouldPass() {
		SecurityProfileValidator v = createValidator(GOOD_SECRET, "dbpass123", "redispass123",
				"https://prod.example.com", true, "Lax");
		assertThatCode(v::validate).doesNotThrowAnyException();
	}

	@Test
	void validate_missingJwtSecret_shouldFail() {
		SecurityProfileValidator v = createValidator("", "dbpass", "redispass", "https://prod.example.com", true,
				"Lax");
		assertThatThrownBy(v::validate).isInstanceOf(IllegalStateException.class).hasMessageContaining("jwt.secret");
	}

	@Test
	void validate_shortJwtSecret_shouldFail() {
		SecurityProfileValidator v = createValidator("short", "dbpass", "redispass", "https://prod.example.com", true,
				"Lax");
		assertThatThrownBy(v::validate).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("at least 32 characters");
	}

	private static final String GOOD_SECRET = "aB3dE5gH7jK9mN1pQ3sT5vW7yZ0bC2eF4hJ6lO8qR0uX2wA4zA6cE8gI0kM2o";

	@Test
	void validate_missingDbPassword_shouldFail() {
		SecurityProfileValidator v = createValidator(GOOD_SECRET, "", "redispass", "https://prod.example.com", true,
				"Lax");
		assertThatThrownBy(v::validate).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("datasource.password");
	}

	@Test
	void validate_missingRedisPassword_shouldFail() {
		SecurityProfileValidator v = createValidator(GOOD_SECRET, "dbpass", "", "https://prod.example.com", true,
				"Lax");
		assertThatThrownBy(v::validate).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("redis.password");
	}

	@Test
	void validate_missingCorsOrigins_shouldFail() {
		SecurityProfileValidator v = createValidator(GOOD_SECRET, "dbpass", "redispass", "", true, "Lax");
		assertThatThrownBy(v::validate).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("cors.allowed-origins");
	}

	@Test
	void validate_corsWithLocalhost_shouldFail() {
		SecurityProfileValidator v = createValidator(GOOD_SECRET, "dbpass", "redispass", "http://localhost:5173", true,
				"Lax");
		assertThatThrownBy(v::validate).isInstanceOf(IllegalStateException.class).hasMessageContaining("localhost");
	}

	@Test
	void validate_cookieNotSecure_shouldFail() {
		SecurityProfileValidator v = createValidator(GOOD_SECRET, "dbpass", "redispass", "https://prod.example.com",
				false, "Lax");
		assertThatThrownBy(v::validate).isInstanceOf(IllegalStateException.class).hasMessageContaining("cookie.secure");
	}

	@ParameterizedTest
	@CsvSource({ "None", "''" })
	void validate_insecureSameSite_shouldFail(String sameSite) {
		SecurityProfileValidator v = createValidator(GOOD_SECRET, "dbpass", "redispass", "https://prod.example.com",
				true, sameSite);
		assertThatThrownBy(v::validate).isInstanceOf(IllegalStateException.class).hasMessageContaining("same-site");
	}

	@Test
	void validate_multipleErrors_shouldReportAll() {
		SecurityProfileValidator v = createValidator("", "", "", "", false, "");
		assertThatThrownBy(v::validate).isInstanceOf(IllegalStateException.class).hasMessageContaining("6 issue(s)");
	}

	// ──────────────── [F-8] JWT entropy checks ────────────────

	@Test
	void checkEntropy_highEntropySecret_shouldReturnNull() {
		SecurityProfileValidator v = new SecurityProfileValidator();
		// 64-char high entropy random string
		String good = "aB3dE5gH7jK9mN1pQ3sT5vW7yZ0bC2eF4hJ6lO8qR0uX2wA4zA6cE8gI0kM2o";
		assertThat(v.checkEntropy(good)).isNull();
	}

	@Test
	void checkEntropy_repeatedChar_shouldFail() {
		SecurityProfileValidator v = new SecurityProfileValidator();
		String bad = "a".repeat(64);
		assertThat(v.checkEntropy(bad)).contains("unique characters");
	}

	@Test
	void checkEntropy_fewUniqueChars_shouldFail() {
		SecurityProfileValidator v = new SecurityProfileValidator();
		// Only uses 5 different chars
		String bad = "abcde".repeat(13);
		assertThat(v.checkEntropy(bad)).contains("unique characters");
	}

	@Test
	void checkEntropy_dominantChar_shouldFail() {
		SecurityProfileValidator v = new SecurityProfileValidator();
		// 'x' repeated > 40% of the total
		String bad = "x".repeat(30) + "aBcDeFgHiJkLmNoPqRsTuVwXyZ01234567";
		assertThat(v.checkEntropy(bad)).contains("single character repeated");
	}

	@Test
	void checkEntropy_containsPasswordPattern_shouldFail() {
		SecurityProfileValidator v = new SecurityProfileValidator();
		String bad = "MySecretpassword123456789abcdefghijklmnopq";
		assertThat(v.checkEntropy(bad)).contains("weak pattern");
	}

	@Test
	void checkEntropy_containsQwerty_shouldFail() {
		SecurityProfileValidator v = new SecurityProfileValidator();
		String bad = "XyZ0123456789ABCDEFGHIJqwerty0LMNOPQRSTuv";
		assertThat(v.checkEntropy(bad)).contains("weak pattern");
	}

}
