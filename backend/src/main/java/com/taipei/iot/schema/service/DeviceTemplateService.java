package com.taipei.iot.schema.service;

import com.taipei.iot.common.exception.BusinessException;
import com.taipei.iot.common.enums.ErrorCode;
import com.taipei.iot.common.device.port.DeviceTypeUsageGuard;
import com.taipei.iot.schema.dto.DeviceTemplateResponse;
import com.taipei.iot.schema.entity.DeviceTemplate;
import com.taipei.iot.schema.repository.DeviceTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DeviceTemplateService {

	private final DeviceTemplateRepository deviceTemplateRepository;

	private final DeviceTypeUsageGuard deviceTypeUsageGuard;

	private static final int MAX_JSONB_SIZE = 10_000;

	public Map<String, Object> getSchema(String deviceType) {
		DeviceTemplate template = deviceTemplateRepository.findByDeviceType(deviceType)
			.orElseThrow(() -> new BusinessException(ErrorCode.DEVICE_NOT_FOUND,
					"DeviceType schema not defined: " + deviceType));
		return template.getSchema();
	}

	@Transactional
	public Map<String, Object> updateSchema(String deviceType, Map<String, Object> schema) {
		DeviceTemplate template = deviceTemplateRepository.findByDeviceType(deviceType)
			.orElseGet(() -> DeviceTemplate.builder().deviceType(deviceType).build());

		template.setSchema(schema);
		template.setVersion(template.getVersion() != null ? template.getVersion() + 1 : 1);
		deviceTemplateRepository.save(template);
		return template.getSchema();
	}

	private static final String ATTRIBUTES_KEY = "attributes";

	private static final String TELEMETRY_KEY = "telemetry";

	/**
	 * 取得指定設備型別 schema 的 {@code attributes}（靜態屬性）段。
	 */
	public Map<String, Object> getAttributesSchema(String deviceType) {
		return getSection(deviceType, ATTRIBUTES_KEY);
	}

	/**
	 * 取得指定設備型別 schema 的 {@code telemetry}（時序量測）段。
	 */
	public Map<String, Object> getTelemetrySchema(String deviceType) {
		return getSection(deviceType, TELEMETRY_KEY);
	}

	/**
	 * 更新 {@code attributes} 段，保留 {@code telemetry} 段不動；deviceType 不存在則自動建立。
	 */
	@Transactional
	public Map<String, Object> updateAttributesSchema(String deviceType, Map<String, Object> attributesSchema) {
		return updateSection(deviceType, ATTRIBUTES_KEY, attributesSchema);
	}

	/**
	 * 更新 {@code telemetry} 段，保留 {@code attributes} 段不動；deviceType 不存在則自動建立。
	 */
	@Transactional
	public Map<String, Object> updateTelemetrySchema(String deviceType, Map<String, Object> telemetrySchema) {
		return updateSection(deviceType, TELEMETRY_KEY, telemetrySchema);
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> getSection(String deviceType, String key) {
		Map<String, Object> schema = getSchema(deviceType);
		Object section = schema != null ? schema.get(key) : null;
		return section instanceof Map ? new HashMap<>((Map<String, Object>) section) : new HashMap<>();
	}

	private Map<String, Object> updateSection(String deviceType, String key, Map<String, Object> section) {
		DeviceTemplate template = deviceTemplateRepository.findByDeviceType(deviceType)
			.orElseGet(() -> DeviceTemplate.builder().deviceType(deviceType).schema(new HashMap<>()).build());

		Map<String, Object> schema = template.getSchema() != null ? new HashMap<>(template.getSchema())
				: new HashMap<>();
		Map<String, Object> normalized = section != null ? new HashMap<>(section) : new HashMap<>();
		schema.put(key, normalized);
		schema.putIfAbsent(ATTRIBUTES_KEY, new HashMap<>());
		schema.putIfAbsent(TELEMETRY_KEY, new HashMap<>());

		template.setSchema(schema);
		template.setVersion(template.getVersion() != null ? template.getVersion() + 1 : 1);
		deviceTemplateRepository.save(template);
		return normalized;
	}

	public List<DeviceTemplateResponse> listDeviceTypes() {
		return deviceTemplateRepository.findAll()
			.stream()
			.map(t -> DeviceTemplateResponse.builder()
				.deviceType(t.getDeviceType())
				.version(t.getVersion())
				.createdBy(t.getCreatedBy())
				.createdAt(t.getCreatedAt())
				.updatedAt(t.getUpdatedAt())
				.build())
			.sorted((a, b) -> a.getDeviceType().compareTo(b.getDeviceType()))
			.toList();
	}

	public List<String> listDeviceTypeNames() {
		return deviceTemplateRepository.findAll().stream().map(DeviceTemplate::getDeviceType).sorted().toList();
	}

	public boolean existsByDeviceType(String deviceType) {
		return deviceTemplateRepository.findByDeviceType(deviceType).isPresent();
	}

	public void validateDeviceTypeExists(String deviceType) {
		if (!existsByDeviceType(deviceType)) {
			throw new BusinessException(ErrorCode.DEVICE_TYPE_NOT_DEFINED,
					"Device type not defined in template: " + deviceType);
		}
	}

	public void validateAttributes(String deviceType, Map<String, Object> attributes) {
		if (attributes != null && attributes.toString().length() > MAX_JSONB_SIZE) {
			throw new BusinessException(ErrorCode.DEVICE_NOT_FOUND,
					"Attributes exceed maximum size of " + MAX_JSONB_SIZE + " chars");
		}
	}

	@Transactional
	public void deleteDeviceType(String deviceType) {
		DeviceTemplate template = deviceTemplateRepository.findByDeviceType(deviceType)
			.orElseThrow(() -> new BusinessException(ErrorCode.DEVICE_TYPE_NOT_DEFINED,
					"Device type not found: " + deviceType));

		long deviceCount = deviceTypeUsageGuard.countDevicesOfType(deviceType);
		if (deviceCount > 0) {
			throw new BusinessException(ErrorCode.DEVICE_TYPE_NOT_DEFINED,
					"無法刪除設備類型「" + deviceType + "」，尚有 " + deviceCount + " 個設備使用此類型");
		}

		deviceTemplateRepository.delete(template);
	}

}
