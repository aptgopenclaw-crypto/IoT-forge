package com.taipei.iot.import_.device;

import com.taipei.iot.common.context.TenantContext;
import com.taipei.iot.common.exception.BusinessException;
import com.taipei.iot.dept.entity.DeptInfoEntity;
import com.taipei.iot.dept.repository.DeptInfoRepository;
import com.taipei.iot.device.entity.Circuit;
import com.taipei.iot.device.entity.Contract;
import com.taipei.iot.device.entity.Device;
import com.taipei.iot.device.enums.ConnectivityType;
import com.taipei.iot.device.enums.DeviceStatus;
import com.taipei.iot.device.repository.CircuitRepository;
import com.taipei.iot.device.repository.ContractRepository;
import com.taipei.iot.device.repository.DeviceRepository;
import com.taipei.iot.import_.ImportError;
import com.taipei.iot.import_.ImportStrategy;
import com.taipei.iot.schema.service.DeviceTemplateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class DeviceImportStrategy implements ImportStrategy<DeviceImportRow> {

	private static final Set<String> HEADERS = Set.of("device_type", "device_code", "device_name", "twd97_x", "twd97_y",
			"lng", "lat", "elevation", "dept_name", "contract_name", "property_owner", "installed_at",
			"parent_device_code", "mount_position", "connectivity_type", "circuit_number");

	private static final int MAX_DEVICE_CODE_LENGTH = 100;

	private static final int MAX_DEVICE_NAME_LENGTH = 200;

	private static final int MAX_PROPERTY_OWNER_LENGTH = 200;

	private static final int MAX_MOUNT_POSITION_LENGTH = 50;

	private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

	private final DeviceRepository deviceRepository;

	private final DeviceTemplateService deviceTemplateService;

	private final DeptInfoRepository deptInfoRepository;

	private final ContractRepository contractRepository;

	private final CircuitRepository circuitRepository;

	@Override
	public String getEntityType() {
		return "device";
	}

	@Override
	public Set<String> expectedHeaders() {
		return HEADERS;
	}

	@Override
	public DeviceImportRow mapToDto(Map<String, String> row) {
		DeviceImportRow.DeviceImportRowBuilder builder = DeviceImportRow.builder()
			.deviceType(row.get("device_type"))
			.deviceCode(row.get("device_code"))
			.deviceName(row.get("device_name"))
			.deptName(row.get("dept_name"))
			.contractName(row.get("contract_name"))
			.propertyOwner(row.get("property_owner"))
			.parentDeviceCode(row.get("parent_device_code"))
			.mountPosition(row.get("mount_position"))
			.circuitNumber(row.get("circuit_number"));

		// 保留原始值供 validate 階段回報格式錯誤
		builder.rawInstalledAt(row.get("installed_at"));
		builder.rawConnectivityType(row.get("connectivity_type"));

		// 數值解析 (空值或空白視為 null，不報錯；錯誤格式在 validate 回報，mapToDto 不拋錯)
		builder.twd97X(parseBigDecimal(row.get("twd97_x")));
		builder.twd97Y(parseBigDecimal(row.get("twd97_y")));
		builder.lng(parseBigDecimal(row.get("lng")));
		builder.lat(parseBigDecimal(row.get("lat")));
		builder.elevation(parseBigDecimal(row.get("elevation")));

		// 日期解析 (空值或格式錯誤由 validate 處理，mapToDto 不拋錯)
		if (row.get("installed_at") != null && !row.get("installed_at").isBlank()) {
			try {
				builder.installedAt(LocalDate.parse(row.get("installed_at"), DATE_FORMATTER));
			}
			catch (DateTimeParseException ignored) {
				// validate 階段會報錯
			}
		}

		// ConnectivityType 解析
		if (row.get("connectivity_type") != null && !row.get("connectivity_type").isBlank()) {
			try {
				builder.connectivityType(ConnectivityType.valueOf(row.get("connectivity_type").toUpperCase()));
			}
			catch (IllegalArgumentException ignored) {
				// validate 階段會報錯
			}
		}

		return builder.build();
	}

	@Override
	public List<ImportError> validate(DeviceImportRow dto, int rowNum) {
		List<ImportError> errors = new ArrayList<>();

		// device_code 必填
		if (dto.getDeviceCode() == null || dto.getDeviceCode().isBlank()) {
			errors.add(error(rowNum, "device_code", dto.getDeviceCode(), "device_code 為必填"));
		}
		else if (dto.getDeviceCode().length() > MAX_DEVICE_CODE_LENGTH) {
			errors
				.add(error(rowNum, "device_code", dto.getDeviceCode(), "device_code 長度不得超過 " + MAX_DEVICE_CODE_LENGTH));
		}

		// device_name 長度
		if (dto.getDeviceName() != null && dto.getDeviceName().length() > MAX_DEVICE_NAME_LENGTH) {
			errors
				.add(error(rowNum, "device_name", dto.getDeviceName(), "device_name 長度不得超過 " + MAX_DEVICE_NAME_LENGTH));
		}

		// device_type 必填
		if (dto.getDeviceType() == null || dto.getDeviceType().isBlank()) {
			errors.add(error(rowNum, "device_type", dto.getDeviceType(), "device_type 為必填"));
		}
		else {
			// device_type 存在於 device_template
			try {
				deviceTemplateService.validateDeviceTypeExists(dto.getDeviceType());
			}
			catch (BusinessException e) {
				errors.add(error(rowNum, "device_type", dto.getDeviceType(),
						"設備類型 " + dto.getDeviceType() + " 不存在於 DeviceTemplate"));
			}
		}

		// installed_at 格式檢查
		if (dto.getRawInstalledAt() != null && !dto.getRawInstalledAt().isBlank() && dto.getInstalledAt() == null) {
			errors.add(error(rowNum, "installed_at", dto.getRawInstalledAt(),
					"安裝日期格式應為 YYYY-MM-DD，收到「" + dto.getRawInstalledAt() + "」"));
		}

		// connectivity_type 合法值檢查
		if (dto.getRawConnectivityType() != null && !dto.getRawConnectivityType().isBlank()
				&& dto.getConnectivityType() == null) {
			errors.add(error(rowNum, "connectivity_type", dto.getRawConnectivityType(), "連線方式「"
					+ dto.getRawConnectivityType() + "」不在允許值中 (NONE, DIRECT, GATEWAY, WIFI, LORAWAN, NB_IOT, LTE)"));
		}

		// property_owner 長度
		if (dto.getPropertyOwner() != null && dto.getPropertyOwner().length() > MAX_PROPERTY_OWNER_LENGTH) {
			errors.add(error(rowNum, "property_owner", dto.getPropertyOwner(),
					"property_owner 長度不得超過 " + MAX_PROPERTY_OWNER_LENGTH));
		}

		// mount_position 長度
		if (dto.getMountPosition() != null && dto.getMountPosition().length() > MAX_MOUNT_POSITION_LENGTH) {
			errors.add(error(rowNum, "mount_position", dto.getMountPosition(),
					"mount_position 長度不得超過 " + MAX_MOUNT_POSITION_LENGTH));
		}

		return errors;
	}

	@Override
	public List<ImportError> batchValidate(List<DeviceImportRow> dtos) {
		String tenantId = TenantContext.getCurrentTenantId();
		List<ImportError> errors = new ArrayList<>();

		// ── device_code 檔案內重複 ──
		Map<String, List<Integer>> codeRowMap = new LinkedHashMap<>();
		for (int i = 0; i < dtos.size(); i++) {
			String code = dtos.get(i).getDeviceCode();
			if (code != null && !code.isBlank()) {
				codeRowMap.computeIfAbsent(code, k -> new ArrayList<>()).add(i + 2);
			}
		}
		for (Map.Entry<String, List<Integer>> entry : codeRowMap.entrySet()) {
			if (entry.getValue().size() > 1) {
				String msg = "設備代碼 " + entry.getKey() + " 在檔案中重複（第 " + entry.getValue() + " 列）";
				for (int rowNum : entry.getValue()) {
					errors.add(error(rowNum, "device_code", entry.getKey(), msg));
				}
			}
		}

		// ── device_code 租戶內唯一 ──
		Set<String> existingCodes = codeRowMap.keySet()
			.stream()
			.filter(code -> deviceRepository.findByTenantIdAndDeviceCode(tenantId, code).isPresent())
			.collect(Collectors.toSet());
		for (Map.Entry<String, List<Integer>> entry : codeRowMap.entrySet()) {
			if (existingCodes.contains(entry.getKey())) {
				for (int rowNum : entry.getValue()) {
					errors.add(error(rowNum, "device_code", entry.getKey(), "設備代碼 " + entry.getKey() + " 已存在於此租戶"));
				}
			}
		}

		// ── dept_name 存在 ──
		Set<String> deptNames = dtos.stream()
			.map(DeviceImportRow::getDeptName)
			.filter(n -> n != null && !n.isBlank())
			.collect(Collectors.toSet());
		Map<String, Boolean> deptExistsMap = new HashMap<>();
		for (String name : deptNames) {
			deptExistsMap.put(name, deptInfoRepository.findByTenantIdAndDeptName(tenantId, name).isPresent());
		}
		for (int i = 0; i < dtos.size(); i++) {
			String name = dtos.get(i).getDeptName();
			if (name != null && !name.isBlank()) {
				Boolean exists = deptExistsMap.get(name);
				if (Boolean.FALSE.equals(exists)) {
					errors.add(error(i + 2, "dept_name", name, "部門名稱「" + name + "」對應不到任何部門"));
				}
			}
		}

		// ── contract_name 存在 ──
		Set<String> contractNames = dtos.stream()
			.map(DeviceImportRow::getContractName)
			.filter(n -> n != null && !n.isBlank())
			.collect(Collectors.toSet());
		Map<String, Boolean> contractExistsMap = new HashMap<>();
		for (String name : contractNames) {
			contractExistsMap.put(name, contractRepository.findByTenantIdAndContractName(tenantId, name).isPresent());
		}
		for (int i = 0; i < dtos.size(); i++) {
			String name = dtos.get(i).getContractName();
			if (name != null && !name.isBlank()) {
				Boolean exists = contractExistsMap.get(name);
				if (Boolean.FALSE.equals(exists)) {
					errors.add(error(i + 2, "contract_name", name, "合約名稱「" + name + "」對應不到任何契約"));
				}
			}
		}

		// ── circuit_number 存在 ──
		Set<String> circuitNumbers = dtos.stream()
			.map(DeviceImportRow::getCircuitNumber)
			.filter(n -> n != null && !n.isBlank())
			.collect(Collectors.toSet());
		Map<String, Boolean> circuitExistsMap = new HashMap<>();
		for (String number : circuitNumbers) {
			circuitExistsMap.put(number,
					circuitRepository.findByTenantIdAndCircuitNumber(tenantId, number).isPresent());
		}
		for (int i = 0; i < dtos.size(); i++) {
			String number = dtos.get(i).getCircuitNumber();
			if (number != null && !number.isBlank()) {
				Boolean exists = circuitExistsMap.get(number);
				if (Boolean.FALSE.equals(exists)) {
					errors.add(error(i + 2, "circuit_number", number, "迴路編號「" + number + "」對應不到任何迴路"));
				}
			}
		}

		// ── parent_device_code 存在 ──
		Set<String> parentCodes = dtos.stream()
			.map(DeviceImportRow::getParentDeviceCode)
			.filter(c -> c != null && !c.isBlank())
			.collect(Collectors.toSet());
		Map<String, Boolean> parentExistsMap = new HashMap<>();
		for (String code : parentCodes) {
			parentExistsMap.put(code, deviceRepository.findByTenantIdAndDeviceCode(tenantId, code).isPresent());
		}
		for (int i = 0; i < dtos.size(); i++) {
			String code = dtos.get(i).getParentDeviceCode();
			if (code != null && !code.isBlank()) {
				Boolean exists = parentExistsMap.get(code);
				if (Boolean.FALSE.equals(exists)) {
					errors.add(error(i + 2, "parent_device_code", code, "父設備代碼「" + code + "」不存在"));
				}
				// 父設備代碼非自身
				if (code.equals(dtos.get(i).getDeviceCode())) {
					errors.add(error(i + 2, "parent_device_code", code, "父設備代碼與自身 device_code 相同"));
				}
			}
		}

		return errors;
	}

	@Override
	@Transactional
	public void saveAll(List<DeviceImportRow> rows) {
		String tenantId = TenantContext.getCurrentTenantId();

		// 批次查詢關聯資料
		Map<String, Long> deptMap = loadDeptMap(tenantId, rows);
		Map<String, Long> contractMap = loadContractMap(tenantId, rows);
		Map<String, Long> circuitMap = loadCircuitMap(tenantId, rows);
		Map<String, Long> parentDeviceMap = loadParentDeviceMap(tenantId, rows);

		List<Device> devices = rows.stream()
			.map(row -> Device.builder()
				.tenantId(tenantId)
				.deviceType(row.getDeviceType())
				.deviceCode(row.getDeviceCode())
				.deviceName(row.getDeviceName())
				.twd97X(row.getTwd97X())
				.twd97Y(row.getTwd97Y())
				.lng(row.getLng())
				.lat(row.getLat())
				.elevation(row.getElevation())
				.deptId(deptMap.get(row.getDeptName()))
				.contractId(contractMap.get(row.getContractName()))
				.propertyOwner(row.getPropertyOwner())
				.status(DeviceStatus.ACTIVE)
				.installedAt(row.getInstalledAt())
				.parentDeviceId(parentDeviceMap.get(row.getParentDeviceCode()))
				.mountPosition(row.getMountPosition())
				.connectivityType(row.getConnectivityType())
				.circuitId(circuitMap.get(row.getCircuitNumber()))
				.build())
			.toList();

		deviceRepository.saveAll(devices);
	}

	// ── 內部輔助方法 ──

	private Map<String, Long> loadDeptMap(String tenantId, List<DeviceImportRow> rows) {
		Set<String> names = rows.stream()
			.map(DeviceImportRow::getDeptName)
			.filter(n -> n != null && !n.isBlank())
			.collect(Collectors.toSet());
		Map<String, Long> map = new HashMap<>();
		for (String name : names) {
			deptInfoRepository.findByTenantIdAndDeptName(tenantId, name)
				.ifPresent(dept -> map.put(name, dept.getDeptId()));
		}
		return map;
	}

	private Map<String, Long> loadContractMap(String tenantId, List<DeviceImportRow> rows) {
		Set<String> names = rows.stream()
			.map(DeviceImportRow::getContractName)
			.filter(n -> n != null && !n.isBlank())
			.collect(Collectors.toSet());
		Map<String, Long> map = new HashMap<>();
		for (String name : names) {
			contractRepository.findByTenantIdAndContractName(tenantId, name)
				.ifPresent(contract -> map.put(name, contract.getId()));
		}
		return map;
	}

	private Map<String, Long> loadCircuitMap(String tenantId, List<DeviceImportRow> rows) {
		Set<String> numbers = rows.stream()
			.map(DeviceImportRow::getCircuitNumber)
			.filter(n -> n != null && !n.isBlank())
			.collect(Collectors.toSet());
		Map<String, Long> map = new HashMap<>();
		for (String number : numbers) {
			circuitRepository.findByTenantIdAndCircuitNumber(tenantId, number)
				.ifPresent(circuit -> map.put(number, circuit.getId()));
		}
		return map;
	}

	private Map<String, Long> loadParentDeviceMap(String tenantId, List<DeviceImportRow> rows) {
		Set<String> codes = rows.stream()
			.map(DeviceImportRow::getParentDeviceCode)
			.filter(c -> c != null && !c.isBlank())
			.collect(Collectors.toSet());
		Map<String, Long> map = new HashMap<>();
		for (String code : codes) {
			deviceRepository.findByTenantIdAndDeviceCode(tenantId, code)
				.ifPresent(device -> map.put(code, device.getId()));
		}
		return map;
	}

	private static BigDecimal parseBigDecimal(String value) {
		if (value == null || value.isBlank())
			return null;
		try {
			return new BigDecimal(value.trim());
		}
		catch (NumberFormatException e) {
			return null; // validate 階段不攔截此類錯誤，由上層處理
		}
	}

	private static ImportError error(int rowNum, String field, String value, String message) {
		return ImportError.builder()
			.row(rowNum)
			.field(field)
			.value(value == null ? "" : value)
			.message(message)
			.build();
	}

}
