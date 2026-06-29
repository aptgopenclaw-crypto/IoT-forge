package com.taipei.iot.setting.service;

import com.taipei.iot.common.enums.ErrorCode;
import com.taipei.iot.common.exception.BusinessException;
import com.taipei.iot.setting.dto.SystemSettingDto;
import com.taipei.iot.setting.entity.SystemSettingEntity;
import com.taipei.iot.setting.enums.SettingKey;
import com.taipei.iot.setting.repository.SystemSettingRepository;
import com.taipei.iot.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SystemSettingService {

	private static final String PLATFORM_TENANT_ID = "__PLATFORM__";

	private final SystemSettingRepository settingRepository;

	@Transactional(readOnly = true)
	public List<SystemSettingDto> findAllSettings() {
		List<SystemSettingEntity> entities;
		if (TenantContext.isSystemContext()) {
			entities = settingRepository.findByTenantId(PLATFORM_TENANT_ID);
		}
		else {
			entities = settingRepository.findAll();
		}
		return entities.stream()
			.filter(e -> !e.getSettingKey().startsWith("password."))
			.map(e -> SystemSettingDto.builder()
				.settingKey(e.getSettingKey())
				.settingValue(e.getSettingValue())
				.description(e.getDescription())
				.build())
			.toList();
	}

	@Transactional
	public SystemSettingDto updateSetting(String key, String value) {
		validateSettingValue(key, value);
		SystemSettingEntity entity;
		if (TenantContext.isSystemContext()) {
			entity = settingRepository.findByTenantIdAndSettingKey(PLATFORM_TENANT_ID, key)
				.orElseThrow(() -> new BusinessException(ErrorCode.SETTING_NOT_FOUND));
		}
		else {
			entity = settingRepository.findBySettingKey(key)
				.orElseThrow(() -> new BusinessException(ErrorCode.SETTING_NOT_FOUND));
		}
		entity.setSettingValue(value);
		try {
			settingRepository.saveAndFlush(entity);
		}
		catch (OptimisticLockingFailureException ex) {
			throw new BusinessException(ErrorCode.SETTING_VERSION_CONFLICT);
		}
		return SystemSettingDto.builder()
			.settingKey(entity.getSettingKey())
			.settingValue(entity.getSettingValue())
			.description(entity.getDescription())
			.build();
	}

	@Transactional(readOnly = true)
	public int getIdleTimeoutMinutes() {
		String settingKey = SettingKey.IDLE_TIMEOUT_MINUTES.getKey();
		int defaultVal = Integer.parseInt(SettingKey.IDLE_TIMEOUT_MINUTES.getDefaultValue());
		Optional<SystemSettingEntity> entity;
		if (TenantContext.isSystemContext()) {
			entity = settingRepository.findByTenantIdAndSettingKey(PLATFORM_TENANT_ID, settingKey);
		}
		else {
			entity = settingRepository.findBySettingKey(settingKey);
		}
		return entity.map(e -> parseIntOrDefault(e.getSettingValue(), defaultVal)).orElse(defaultVal);
	}

	@Transactional
	public int updateIdleTimeoutMinutes(int minutes) {
		String settingKey = SettingKey.IDLE_TIMEOUT_MINUTES.getKey();
		SystemSettingEntity entity;
		if (TenantContext.isSystemContext()) {
			entity = settingRepository.findByTenantIdAndSettingKey(PLATFORM_TENANT_ID, settingKey)
				.orElseThrow(() -> new BusinessException(ErrorCode.SETTING_NOT_FOUND));
		}
		else {
			entity = settingRepository.findBySettingKey(settingKey)
				.orElseThrow(() -> new BusinessException(ErrorCode.SETTING_NOT_FOUND));
		}
		entity.setSettingValue(String.valueOf(minutes));
		try {
			settingRepository.saveAndFlush(entity);
		}
		catch (OptimisticLockingFailureException ex) {
			throw new BusinessException(ErrorCode.SETTING_VERSION_CONFLICT);
		}
		return minutes;
	}

	private void validateSettingValue(String key, String value) {
		Arrays.stream(SettingKey.values())
			.filter(k -> k.getKey().equals(key))
			.findFirst()
			.ifPresent(known -> known.getValidator().validate(key, value));
	}

	private int parseIntOrDefault(String value, int defaultValue) {
		try {
			return Integer.parseInt(value);
		}
		catch (NumberFormatException e) {
			log.warn("Invalid integer setting value '{}', using default: {}", value, defaultValue);
			return defaultValue;
		}
	}

}
