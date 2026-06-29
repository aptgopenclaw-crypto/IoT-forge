package com.taipei.iot.setting.service;

import com.taipei.iot.common.enums.ErrorCode;
import com.taipei.iot.common.exception.BusinessException;
import com.taipei.iot.setting.dto.SystemSettingDto;
import com.taipei.iot.setting.entity.SystemSettingEntity;
import com.taipei.iot.setting.enums.SettingKey;
import com.taipei.iot.setting.repository.SystemSettingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SystemSettingServiceTest {

	@InjectMocks
	private SystemSettingService settingService;

	@Mock
	private SystemSettingRepository settingRepository;

	@Test
	void getIdleTimeoutMinutes_found_shouldReturnValue() {
		SystemSettingEntity entity = SystemSettingEntity.builder()
			.settingKey("idle_timeout_minutes")
			.settingValue("20")
			.build();
		when(settingRepository.findBySettingKey("idle_timeout_minutes")).thenReturn(Optional.of(entity));

		int result = settingService.getIdleTimeoutMinutes();

		assertEquals(20, result);
	}

	@Test
	void getIdleTimeoutMinutes_notFound_shouldReturnDefault() {
		when(settingRepository.findBySettingKey("idle_timeout_minutes")).thenReturn(Optional.empty());

		int result = settingService.getIdleTimeoutMinutes();

		assertEquals(Integer.parseInt(SettingKey.IDLE_TIMEOUT_MINUTES.getDefaultValue()), result);
	}

	@Test
	void updateIdleTimeoutMinutes_found_shouldUpdate() {
		SystemSettingEntity entity = SystemSettingEntity.builder()
			.settingKey("idle_timeout_minutes")
			.settingValue("15")
			.build();
		when(settingRepository.findBySettingKey("idle_timeout_minutes")).thenReturn(Optional.of(entity));
		when(settingRepository.saveAndFlush(entity)).thenReturn(entity);

		int result = settingService.updateIdleTimeoutMinutes(30);

		assertEquals(30, result);
		assertEquals("30", entity.getSettingValue());
		verify(settingRepository).saveAndFlush(entity);
	}

	@Test
	void updateIdleTimeoutMinutes_notFound_shouldThrow() {
		when(settingRepository.findBySettingKey("idle_timeout_minutes")).thenReturn(Optional.empty());

		assertThrows(BusinessException.class, () -> settingService.updateIdleTimeoutMinutes(30));
	}

	// ---- findAllSettings ----

	@Test
	void findAllSettings_shouldReturnDtoList() {
		var entity = SystemSettingEntity.builder()
			.settingKey("idle_timeout_minutes")
			.settingValue("15")
			.description("Idle timeout (minutes)")
			.build();
		when(settingRepository.findAll()).thenReturn(List.of(entity));

		List<SystemSettingDto> result = settingService.findAllSettings();

		assertEquals(1, result.size());
		assertEquals("idle_timeout_minutes", result.get(0).getSettingKey());
		assertEquals("15", result.get(0).getSettingValue());
		assertEquals("Idle timeout (minutes)", result.get(0).getDescription());
	}

	// ---- updateSetting (generic) ----

	@Test
	void updateSetting_found_shouldReturnUpdatedDto() {
		var entity = SystemSettingEntity.builder()
			.settingKey("idle_timeout_minutes")
			.settingValue("15")
			.description("Idle timeout")
			.build();
		when(settingRepository.findBySettingKey("idle_timeout_minutes")).thenReturn(Optional.of(entity));
		when(settingRepository.saveAndFlush(entity)).thenReturn(entity);

		SystemSettingDto result = settingService.updateSetting("idle_timeout_minutes", "30");

		assertEquals("30", result.getSettingValue());
		assertEquals("30", entity.getSettingValue());
		verify(settingRepository).saveAndFlush(entity);
	}

	@Test
	void updateSetting_unknownKey_shouldThrow() {
		when(settingRepository.findBySettingKey("unknown_key")).thenReturn(Optional.empty());
		assertThrows(BusinessException.class, () -> settingService.updateSetting("unknown_key", "value"));
	}

	// ---- [N-4] Optimistic locking tests ----

	@Test
	void updateSetting_versionConflict_shouldThrowSettingVersionConflict() {
		var entity = SystemSettingEntity.builder()
			.settingKey("idle_timeout_minutes")
			.settingValue("15")
			.version(0)
			.build();
		when(settingRepository.findBySettingKey("idle_timeout_minutes")).thenReturn(Optional.of(entity));
		when(settingRepository.saveAndFlush(entity))
			.thenThrow(new OptimisticLockingFailureException("Row was updated by another transaction"));

		assertThatThrownBy(() -> settingService.updateSetting("idle_timeout_minutes", "30"))
			.isInstanceOf(BusinessException.class)
			.satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
				.isEqualTo(ErrorCode.SETTING_VERSION_CONFLICT));
	}

	@Test
	void updateIdleTimeoutMinutes_versionConflict_shouldThrowSettingVersionConflict() {
		var entity = SystemSettingEntity.builder()
			.settingKey("idle_timeout_minutes")
			.settingValue("15")
			.version(0)
			.build();
		when(settingRepository.findBySettingKey("idle_timeout_minutes")).thenReturn(Optional.of(entity));
		when(settingRepository.saveAndFlush(entity))
			.thenThrow(new OptimisticLockingFailureException("Row was updated by another transaction"));

		assertThatThrownBy(() -> settingService.updateIdleTimeoutMinutes(30)).isInstanceOf(BusinessException.class)
			.satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
				.isEqualTo(ErrorCode.SETTING_VERSION_CONFLICT));
	}

	// ---- [N-5] Dead code removal verification ----

	@Test
	void getSetting_singleKeyMethod_shouldNotExist() {
		assertThat(java.util.Arrays.stream(SystemSettingService.class.getDeclaredMethods())
			.noneMatch(m -> "getSetting".equals(m.getName()) && m.getParameterCount() == 1
					&& m.getParameterTypes()[0] == String.class))
			.as("Dead code getSetting(String) should have been removed")
			.isTrue();
	}

}
