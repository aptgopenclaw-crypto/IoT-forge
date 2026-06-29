package com.taipei.iot.setting.enums;

import com.taipei.iot.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SettingValidatorTest {

	// ---- Every SettingKey must have a non-null validator (compile-time guarantee) ----

	@ParameterizedTest
	@EnumSource(SettingKey.class)
	void everySettingKey_shouldHaveNonNullValidator(SettingKey key) {
		assertThat(key.getValidator()).as("SettingKey.%s must have a validator", key.name()).isNotNull();
	}

	// ---- intRange validator ----

	@Test
	void intRange_validValue_shouldPass() {
		SettingValidator v = SettingValidator.intRange(1, 100);
		assertThatCode(() -> v.validate("test_key", "50")).doesNotThrowAnyException();
	}

	@Test
	void intRange_atLowerBound_shouldPass() {
		SettingValidator v = SettingValidator.intRange(1, 100);
		assertThatCode(() -> v.validate("test_key", "1")).doesNotThrowAnyException();
	}

	@Test
	void intRange_atUpperBound_shouldPass() {
		SettingValidator v = SettingValidator.intRange(1, 100);
		assertThatCode(() -> v.validate("test_key", "100")).doesNotThrowAnyException();
	}

	@Test
	void intRange_belowMin_shouldThrow() {
		SettingValidator v = SettingValidator.intRange(1, 100);
		assertThatThrownBy(() -> v.validate("test_key", "0")).isInstanceOf(BusinessException.class)
			.hasMessageContaining("must be between 1 and 100");
	}

	@Test
	void intRange_aboveMax_shouldThrow() {
		SettingValidator v = SettingValidator.intRange(1, 100);
		assertThatThrownBy(() -> v.validate("test_key", "101")).isInstanceOf(BusinessException.class)
			.hasMessageContaining("must be between 1 and 100");
	}

	@Test
	void intRange_nonNumeric_shouldThrow() {
		SettingValidator v = SettingValidator.intRange(1, 100);
		assertThatThrownBy(() -> v.validate("test_key", "abc")).isInstanceOf(BusinessException.class)
			.hasMessageContaining("must be a valid integer");
	}

	// ---- url validator ----

	@Test
	void url_validHttpUrl_shouldPass() {
		SettingValidator v = SettingValidator.url();
		assertThatCode(() -> v.validate("test_key", "http://localhost:5173")).doesNotThrowAnyException();
	}

	@Test
	void url_validHttpsUrl_shouldPass() {
		SettingValidator v = SettingValidator.url();
		assertThatCode(() -> v.validate("test_key", "https://example.com")).doesNotThrowAnyException();
	}

	@Test
	void url_invalidUrl_shouldThrow() {
		SettingValidator v = SettingValidator.url();
		assertThatThrownBy(() -> v.validate("test_key", "not-a-url")).isInstanceOf(BusinessException.class)
			.hasMessageContaining("must be a valid URL");
	}

	@Test
	void url_emptyString_shouldThrow() {
		SettingValidator v = SettingValidator.url();
		assertThatThrownBy(() -> v.validate("test_key", "")).isInstanceOf(BusinessException.class)
			.hasMessageContaining("must be a valid URL");
	}

	// ---- SettingKey delegates to its validator correctly ----

	@Test
	void idleTimeoutMinutes_validatesViaValidator() {
		assertThatThrownBy(() -> SettingKey.IDLE_TIMEOUT_MINUTES.getValidator().validate("idle_timeout_minutes", "0"))
			.isInstanceOf(BusinessException.class);
	}

	@Test
	void frontendBaseUrl_validatesViaValidator() {
		assertThatThrownBy(() -> SettingKey.FRONTEND_BASE_URL.getValidator().validate("frontend_base_url", ""))
			.isInstanceOf(BusinessException.class);
	}

}
