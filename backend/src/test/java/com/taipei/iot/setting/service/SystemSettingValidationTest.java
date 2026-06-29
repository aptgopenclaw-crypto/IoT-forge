package com.taipei.iot.setting.service;

import com.taipei.iot.common.exception.BusinessException;
import com.taipei.iot.setting.entity.SystemSettingEntity;
import com.taipei.iot.setting.repository.SystemSettingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * [Setting N-1] 驗證 validateSettingValue 的 key 白名單 + per-key 值域驗證。 覆蓋：未知 key
 * 拒絕、idle_timeout 範圍、audit_retention 範圍、 notification_retention 範圍、frontend_base_url 格式。
 */
@ExtendWith(MockitoExtension.class)
class SystemSettingValidationTest {

	@InjectMocks
	private SystemSettingService settingService;

	@Mock
	private SystemSettingRepository settingRepository;

	private void mockKeyExists(String key) {
		when(settingRepository.findBySettingKey(key))
			.thenReturn(Optional.of(SystemSettingEntity.builder().settingKey(key).settingValue("old").build()));
		when(settingRepository.saveAndFlush(org.mockito.ArgumentMatchers.any())).thenAnswer(inv -> inv.getArgument(0));
	}

	// === Unknown key (not in SettingKey enum) → skips validation, but not found in DB
	// ===

	@ParameterizedTest
	@ValueSource(strings = { "unknown_key", "random", "IDLE_TIMEOUT_MINUTES", "admin_password" })
	void updateSetting_unknownKey_shouldRejectWithSettingNotFound(String key) {
		when(settingRepository.findBySettingKey(key)).thenReturn(Optional.empty());
		assertThatThrownBy(() -> settingService.updateSetting(key, "anything")).isInstanceOf(BusinessException.class);
	}

	// === idle_timeout_minutes ===

	@Test
	void updateSetting_idleTimeout_nonNumeric_shouldThrow() {
		assertThatThrownBy(() -> settingService.updateSetting("idle_timeout_minutes", "abc"))
			.isInstanceOf(BusinessException.class)
			.hasMessageContaining("must be a valid integer");
	}

	@ParameterizedTest
	@ValueSource(strings = { "0", "-1", "481", "999" })
	void updateSetting_idleTimeout_outOfRange_shouldThrow(String value) {
		assertThatThrownBy(() -> settingService.updateSetting("idle_timeout_minutes", value))
			.isInstanceOf(BusinessException.class)
			.hasMessageContaining("must be between 1 and 480");
	}

	@Test
	void updateSetting_idleTimeout_validValue_shouldPass() {
		mockKeyExists("idle_timeout_minutes");

		var result = settingService.updateSetting("idle_timeout_minutes", "30");

		assertThat(result.getSettingValue()).isEqualTo("30");
	}

	// === audit_retention_days ===

	@Test
	void updateSetting_auditRetention_nonNumeric_shouldThrow() {
		assertThatThrownBy(() -> settingService.updateSetting("audit_retention_days", "abc"))
			.isInstanceOf(BusinessException.class)
			.hasMessageContaining("must be a valid integer");
	}

	@ParameterizedTest
	@ValueSource(strings = { "0", "-1", "3651", "99999" })
	void updateSetting_auditRetention_outOfRange_shouldThrow(String value) {
		assertThatThrownBy(() -> settingService.updateSetting("audit_retention_days", value))
			.isInstanceOf(BusinessException.class)
			.hasMessageContaining("must be between 1 and 3650");
	}

	@Test
	void updateSetting_auditRetention_validValue_shouldPass() {
		mockKeyExists("audit_retention_days");

		var result = settingService.updateSetting("audit_retention_days", "365");

		assertThat(result.getSettingValue()).isEqualTo("365");
	}

	// === notification_retention_days ===

	@ParameterizedTest
	@ValueSource(strings = { "0", "-1", "3651" })
	void updateSetting_notificationRetention_outOfRange_shouldThrow(String value) {
		assertThatThrownBy(() -> settingService.updateSetting("notification_retention_days", value))
			.isInstanceOf(BusinessException.class)
			.hasMessageContaining("must be between 1 and 3650");
	}

	@Test
	void updateSetting_notificationRetention_validValue_shouldPass() {
		mockKeyExists("notification_retention_days");

		var result = settingService.updateSetting("notification_retention_days", "90");

		assertThat(result.getSettingValue()).isEqualTo("90");
	}

	// === frontend_base_url ===

	@ParameterizedTest
	@ValueSource(strings = { "not-a-url", "://bad", "just text", "" })
	void updateSetting_frontendBaseUrl_invalidUrl_shouldThrow(String value) {
		assertThatThrownBy(() -> settingService.updateSetting("frontend_base_url", value))
			.isInstanceOf(BusinessException.class)
			.hasMessageContaining("must be a valid URL");
	}

	@ParameterizedTest
	@ValueSource(strings = { "http://localhost:5173", "https://app.example.com", "https://iot.taipei.gov.tw/portal" })
	void updateSetting_frontendBaseUrl_validUrl_shouldPass(String value) {
		mockKeyExists("frontend_base_url");

		var result = settingService.updateSetting("frontend_base_url", value);

		assertThat(result.getSettingValue()).isEqualTo(value);
	}

}
