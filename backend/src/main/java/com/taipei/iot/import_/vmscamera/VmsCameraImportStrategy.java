package com.taipei.iot.import_.vmscamera;

import com.taipei.iot.common.context.TenantContext;
import com.taipei.iot.dept.repository.DeptInfoRepository;
import com.taipei.iot.device.repository.DeviceRepository;
import com.taipei.iot.import_.ImportError;
import com.taipei.iot.import_.ImportStrategy;
import com.taipei.iot.vms.entity.VmsCamera;
import com.taipei.iot.vms.entity.VmsServer;
import com.taipei.iot.vms.enums.CameraStatus;
import com.taipei.iot.vms.repository.VmsCameraRepository;
import com.taipei.iot.vms.repository.VmsServerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * VMS 攝影機 CSV 匯入策略。
 *
 * <p>
 * CSV 欄位：server_name, vms_camera_id, display_name, rtsp_url, device_code, dept_name
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VmsCameraImportStrategy implements ImportStrategy<VmsCameraImportRow> {

	private static final Set<String> HEADERS = Set.of("server_name", "vms_camera_id", "display_name", "rtsp_url",
			"device_code", "dept_name");

	private static final int MAX_CAMERA_ID_LENGTH = 100;

	private static final int MAX_DISPLAY_NAME_LENGTH = 200;

	private final VmsServerRepository vmsServerRepository;

	private final VmsCameraRepository vmsCameraRepository;

	private final DeptInfoRepository deptInfoRepository;

	private final DeviceRepository deviceRepository;

	@Override
	public String getEntityType() {
		return "vms_camera";
	}

	@Override
	public Set<String> expectedHeaders() {
		return HEADERS;
	}

	@Override
	public VmsCameraImportRow mapToDto(Map<String, String> row) {
		return VmsCameraImportRow.builder()
			.serverName(row.get("server_name"))
			.vmsCameraId(row.get("vms_camera_id"))
			.displayName(row.get("display_name"))
			.rtspUrl(row.get("rtsp_url"))
			.deviceCode(row.get("device_code"))
			.deptName(row.get("dept_name"))
			.build();
	}

	@Override
	public List<ImportError> validate(VmsCameraImportRow dto, int rowNum) {
		List<ImportError> errors = new ArrayList<>();

		// server_name 必填
		if (dto.getServerName() == null || dto.getServerName().isBlank()) {
			errors.add(error(rowNum, "server_name", dto.getServerName(), "server_name 為必填"));
		}

		// vms_camera_id 必填
		if (dto.getVmsCameraId() == null || dto.getVmsCameraId().isBlank()) {
			errors.add(error(rowNum, "vms_camera_id", dto.getVmsCameraId(), "vms_camera_id 為必填"));
		}
		else if (dto.getVmsCameraId().length() > MAX_CAMERA_ID_LENGTH) {
			errors.add(error(rowNum, "vms_camera_id", dto.getVmsCameraId(),
					"vms_camera_id 長度不得超過 " + MAX_CAMERA_ID_LENGTH));
		}

		// displayName 長度
		if (dto.getDisplayName() != null && dto.getDisplayName().length() > MAX_DISPLAY_NAME_LENGTH) {
			errors.add(error(rowNum, "display_name", dto.getDisplayName(),
					"display_name 長度不得超過 " + MAX_DISPLAY_NAME_LENGTH));
		}

		return errors;
	}

	@Override
	public List<ImportError> batchValidate(List<VmsCameraImportRow> dtos) {
		String tenantId = TenantContext.getCurrentTenantId();
		List<ImportError> errors = new ArrayList<>();

		// ── vms_camera_id 檔案內重複 ──
		Map<String, List<Integer>> idRowMap = new LinkedHashMap<>();
		for (int i = 0; i < dtos.size(); i++) {
			String id = dtos.get(i).getVmsCameraId();
			if (id != null && !id.isBlank()) {
				idRowMap.computeIfAbsent(id, k -> new ArrayList<>()).add(i + 2);
			}
		}
		for (Map.Entry<String, List<Integer>> entry : idRowMap.entrySet()) {
			if (entry.getValue().size() > 1) {
				String msg = "vms_camera_id " + entry.getKey() + " 在檔案中重複（第 " + entry.getValue() + " 列）";
				for (int rowNum : entry.getValue()) {
					errors.add(error(rowNum, "vms_camera_id", entry.getKey(), msg));
				}
			}
		}

		// ── server_name 存在 ──
		Set<String> serverNames = dtos.stream()
			.map(VmsCameraImportRow::getServerName)
			.filter(n -> n != null && !n.isBlank())
			.collect(Collectors.toSet());
		Map<String, VmsServer> serverMap = new HashMap<>();
		for (String name : serverNames) {
			vmsServerRepository.findByTenantIdAndName(tenantId, name).ifPresent(s -> serverMap.put(name, s));
		}
		for (int i = 0; i < dtos.size(); i++) {
			String name = dtos.get(i).getServerName();
			if (name != null && !name.isBlank() && !serverMap.containsKey(name)) {
				errors.add(error(i + 2, "server_name", name, "VMS Server「" + name + "」對應不到任何已啟用的伺服器"));
			}
		}

		// ── dept_name 存在 ──
		Set<String> deptNames = dtos.stream()
			.map(VmsCameraImportRow::getDeptName)
			.filter(n -> n != null && !n.isBlank())
			.collect(Collectors.toSet());
		Map<String, Long> deptMap = new HashMap<>();
		for (String name : deptNames) {
			deptInfoRepository.findByTenantIdAndDeptName(tenantId, name)
				.ifPresent(d -> deptMap.put(name, d.getDeptId()));
		}
		for (int i = 0; i < dtos.size(); i++) {
			String name = dtos.get(i).getDeptName();
			if (name != null && !name.isBlank() && !deptMap.containsKey(name)) {
				errors.add(error(i + 2, "dept_name", name, "部門名稱「" + name + "」對應不到任何部門"));
			}
		}

		// ── device_code 存在 ──
		Set<String> deviceCodes = dtos.stream()
			.map(VmsCameraImportRow::getDeviceCode)
			.filter(c -> c != null && !c.isBlank())
			.collect(Collectors.toSet());
		Map<String, Long> deviceMap = new HashMap<>();
		for (String code : deviceCodes) {
			deviceRepository.findByTenantIdAndDeviceCode(tenantId, code).ifPresent(d -> deviceMap.put(code, d.getId()));
		}
		for (int i = 0; i < dtos.size(); i++) {
			String code = dtos.get(i).getDeviceCode();
			if (code != null && !code.isBlank() && !deviceMap.containsKey(code)) {
				errors.add(error(i + 2, "device_code", code, "設備代碼「" + code + "」對應不到任何設備"));
			}
		}

		return errors;
	}

	@Override
	@Transactional
	public void saveAll(List<VmsCameraImportRow> rows) {
		String tenantId = TenantContext.getCurrentTenantId();

		// 批量 lookup
		Map<String, VmsServer> serverMap = new HashMap<>();
		Map<String, Long> deptMap = new HashMap<>();
		Map<String, Long> deviceMap = new HashMap<>();

		Set<String> serverNames = rows.stream().map(VmsCameraImportRow::getServerName).collect(Collectors.toSet());
		for (String name : serverNames) {
			vmsServerRepository.findByTenantIdAndName(tenantId, name).ifPresent(s -> serverMap.put(name, s));
		}

		Set<String> deptNames = rows.stream()
			.map(VmsCameraImportRow::getDeptName)
			.filter(n -> n != null && !n.isBlank())
			.collect(Collectors.toSet());
		for (String name : deptNames) {
			deptInfoRepository.findByTenantIdAndDeptName(tenantId, name)
				.ifPresent(d -> deptMap.put(name, d.getDeptId()));
		}

		Set<String> deviceCodes = rows.stream()
			.map(VmsCameraImportRow::getDeviceCode)
			.filter(c -> c != null && !c.isBlank())
			.collect(Collectors.toSet());
		for (String code : deviceCodes) {
			deviceRepository.findByTenantIdAndDeviceCode(tenantId, code).ifPresent(d -> deviceMap.put(code, d.getId()));
		}

		List<VmsCamera> cameras = new ArrayList<>();
		for (VmsCameraImportRow row : rows) {
			VmsServer server = serverMap.get(row.getServerName());
			if (server == null) {
				log.warn("略過攝影機 {}，找不到 server {}", row.getVmsCameraId(), row.getServerName());
				continue;
			}

			VmsCamera c = VmsCamera.builder()
				.tenantId(tenantId)
				.server(server)
				.vmsCameraId(row.getVmsCameraId())
				.displayName(row.getDisplayName())
				.rtspUrl(row.getRtspUrl())
				.deviceId(deviceMap.get(row.getDeviceCode()))
				.deptId(deptMap.get(row.getDeptName()))
				.status(CameraStatus.ONLINE)
				.build();
			cameras.add(c);
		}

		vmsCameraRepository.saveAll(cameras);
		log.info("VMS 攝影機匯入完成: {} 筆", cameras.size());
	}

	private ImportError error(int rowNum, String field, String value, String message) {
		return new ImportError(rowNum, field, value, message);
	}

}
