package com.taipei.iot.import_.device;

import com.taipei.iot.common.context.TenantContext;
import com.taipei.iot.common.exception.BusinessException;
import com.taipei.iot.common.enums.ErrorCode;
import com.taipei.iot.dept.repository.DeptInfoRepository;
import com.taipei.iot.device.repository.CircuitRepository;
import com.taipei.iot.device.repository.ContractRepository;
import com.taipei.iot.device.repository.DeviceRepository;
import com.taipei.iot.import_.ImportError;
import com.taipei.iot.schema.service.DeviceTemplateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeviceImportStrategyTest {

	@Mock
	private DeviceRepository deviceRepository;

	@Mock
	private DeviceTemplateService deviceTemplateService;

	@Mock
	private DeptInfoRepository deptInfoRepository;

	@Mock
	private ContractRepository contractRepository;

	@Mock
	private CircuitRepository circuitRepository;

	private DeviceImportStrategy strategy;

	@BeforeEach
	void setUp() {
		strategy = new DeviceImportStrategy(deviceRepository, deviceTemplateService, deptInfoRepository,
				contractRepository, circuitRepository);
	}

	@Test
	void mapToDto_shouldMapAllFields() {
		Map<String, String> row = Map.of("device_type", "STREET_LIGHT", "device_code", "SL-001", "device_name", "測試燈具",
				"lng", "121.5", "lat", "25.0");

		DeviceImportRow dto = strategy.mapToDto(row);
		assertEquals("STREET_LIGHT", dto.getDeviceType());
		assertEquals("SL-001", dto.getDeviceCode());
		assertEquals("測試燈具", dto.getDeviceName());
		assertNull(dto.getTwd97X()); // null -> null
	}

	@Test
	void validate_missingRequiredFields_shouldReturnErrors() {
		Map<String, String> row = Map.of("device_type", "", "device_code", "", "device_name", "");
		DeviceImportRow dto = strategy.mapToDto(row);

		List<ImportError> errors = strategy.validate(dto, 2);
		assertFalse(errors.isEmpty());

		// device_code 必填
		assertTrue(errors.stream().anyMatch(e -> e.getField().equals("device_code") && e.getMessage().contains("必填")));
		// device_type 必填
		assertTrue(errors.stream().anyMatch(e -> e.getField().equals("device_type") && e.getMessage().contains("必填")));
	}

	@Test
	void validate_deviceTypeNotExists_shouldReturnError() {
		doThrow(new BusinessException(ErrorCode.DEVICE_TYPE_NOT_DEFINED)).when(deviceTemplateService)
			.validateDeviceTypeExists("UNKNOWN");

		Map<String, String> row = Map.of("device_type", "UNKNOWN", "device_code", "SL-001");
		DeviceImportRow dto = strategy.mapToDto(row);

		List<ImportError> errors = strategy.validate(dto, 2);
		assertTrue(errors.stream().anyMatch(e -> e.getField().equals("device_type") && e.getMessage().contains("不存在")));
	}

	@Test
	void validate_validRow_shouldReturnNoErrors() {
		doNothing().when(deviceTemplateService).validateDeviceTypeExists("STREET_LIGHT");

		Map<String, String> row = Map.of("device_type", "STREET_LIGHT", "device_code", "SL-001");
		DeviceImportRow dto = strategy.mapToDto(row);

		List<ImportError> errors = strategy.validate(dto, 2);
		assertTrue(errors.isEmpty());
	}

	@Test
	void expectedHeaders_shouldContainAllRequired() {
		assertTrue(strategy.expectedHeaders().contains("device_type"));
		assertTrue(strategy.expectedHeaders().contains("device_code"));
	}

	@Test
	void getEntityType_shouldReturnDevice() {
		assertEquals("device", strategy.getEntityType());
	}

}
