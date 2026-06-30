package com.taipei.iot.device.service;

import com.taipei.iot.common.enums.ErrorCode;
import com.taipei.iot.common.exception.BusinessException;
import com.taipei.iot.dept.entity.DeptInfoEntity;
import com.taipei.iot.dept.repository.DeptInfoRepository;
import com.taipei.iot.device.dto.DeviceRequest;
import com.taipei.iot.device.dto.DeviceResponse;
import com.taipei.iot.device.dto.DeviceStatsResponse;
import com.taipei.iot.device.entity.Device;
import com.taipei.iot.device.entity.Contract;
import com.taipei.iot.device.enums.DeviceStatus;
import com.taipei.iot.device.repository.DeviceRepository;
import com.taipei.iot.device.repository.ContractRepository;
import com.taipei.iot.common.dispatch.port.OpenWorkOrderCounter;
import com.taipei.iot.schema.service.DeviceTemplateService;
import com.taipei.iot.common.context.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DeviceService {

	private final DeviceRepository deviceRepository;

	private final DeptInfoRepository deptInfoRepository;

	private final ContractRepository contractRepository;

	private final OpenWorkOrderCounter openWorkOrderCounter;

	private final DeviceTemplateService deviceTemplateService;

	// ── 查詢 ─────────────────────────────────────────────────────────

	public Page<DeviceResponse> listDevices(String deviceType, DeviceStatus status, String keyword, Pageable pageable) {
		Page<Device> page = deviceRepository.findByFilters(deviceType, status, keyword, null, pageable);
		return page.map(this::toResponse);
	}

	public DeviceResponse getById(Long id) {
		Device device = deviceRepository.findById(id)
			.orElseThrow(() -> new BusinessException(ErrorCode.DEVICE_NOT_FOUND));
		return toResponse(device);
	}

	public DeviceStatsResponse getStats() {
		List<Device> all = deviceRepository.findAll();
		Map<String, Long> byType = all.stream()
			.collect(Collectors.groupingBy(Device::getDeviceType, Collectors.counting()));
		Map<String, Long> byStatus = all.stream()
			.collect(Collectors.groupingBy(d -> d.getStatus().name(), Collectors.counting()));
		long active = all.stream().filter(d -> d.getStatus() == DeviceStatus.ACTIVE).count();
		long online = all.stream().filter(d -> d.getLastHeartbeatAt() != null).count();

		long openWorkOrders = openWorkOrderCounter.countOpenWorkOrders();

		return DeviceStatsResponse.builder()
			.totalDevices(all.size())
			.byType(byType)
			.byStatus(byStatus)
			.onlineRate(active > 0 ? (double) online / active * 100 : 0)
			.openFaults(openWorkOrders)
			.build();
	}

	// ── 新增 ─────────────────────────────────────────────────────────

	@Transactional
	public DeviceResponse create(DeviceRequest request) {
		String tenantId = TenantContext.getCurrentTenantId();

		deviceRepository.findByTenantIdAndDeviceCode(tenantId, request.getDeviceCode()).ifPresent(d -> {
			throw new BusinessException(ErrorCode.DEVICE_CODE_DUPLICATE);
		});

		deviceTemplateService.validateDeviceTypeExists(request.getDeviceType());
		deviceTemplateService.validateAttributes(request.getDeviceType(), request.getAttributes());

		if (request.getParentDeviceId() != null) {
			validateHierarchyDepth(request.getParentDeviceId(), 1);
		}

		Device device = Device.builder()
			.deviceType(request.getDeviceType())
			.deviceCode(request.getDeviceCode())
			.deviceName(request.getDeviceName())
			.twd97X(request.getTwd97X())
			.twd97Y(request.getTwd97Y())
			.lng(request.getLng())
			.lat(request.getLat())
			.elevation(request.getElevation())
			.twd67X(request.getTwd67X())
			.twd67Y(request.getTwd67Y())
			.taipowerCoord(request.getTaipowerCoord())
			.deptId(request.getDeptId())
			.contractId(request.getContractId())
			.propertyOwner(request.getPropertyOwner())
			.status(DeviceStatus.ACTIVE)
			.installedAt(request.getInstalledAt())
			.parentDeviceId(request.getParentDeviceId())
			.mountPosition(request.getMountPosition())
			.connectivityType(request.getConnectivityType())
			.networkConfig(request.getNetworkConfig())
			.circuitId(request.getCircuitId())
			.attributes(request.getAttributes())
			.build();

		return toResponse(deviceRepository.save(device));
	}

	// ── 編輯 ─────────────────────────────────────────────────────────

	@Transactional
	public DeviceResponse update(Long id, DeviceRequest request) {
		Device device = deviceRepository.findById(id)
			.orElseThrow(() -> new BusinessException(ErrorCode.DEVICE_NOT_FOUND));

		deviceTemplateService.validateDeviceTypeExists(request.getDeviceType());
		deviceTemplateService.validateAttributes(request.getDeviceType(), request.getAttributes());

		if (request.getParentDeviceId() != null && !request.getParentDeviceId().equals(device.getParentDeviceId())) {
			validateHierarchyDepth(request.getParentDeviceId(), 1);
			deviceRepository.checkCircularReference(id, request.getParentDeviceId());
		}

		device.setDeviceType(request.getDeviceType());
		device.setDeviceCode(request.getDeviceCode());
		device.setDeviceName(request.getDeviceName());
		device.setTwd97X(request.getTwd97X());
		device.setTwd97Y(request.getTwd97Y());
		device.setLng(request.getLng());
		device.setLat(request.getLat());
		device.setElevation(request.getElevation());
		device.setTwd67X(request.getTwd67X());
		device.setTwd67Y(request.getTwd67Y());
		device.setTaipowerCoord(request.getTaipowerCoord());
		device.setDeptId(request.getDeptId());
		device.setContractId(request.getContractId());
		device.setPropertyOwner(request.getPropertyOwner());
		device.setInstalledAt(request.getInstalledAt());
		device.setParentDeviceId(request.getParentDeviceId());
		device.setMountPosition(request.getMountPosition());
		device.setConnectivityType(request.getConnectivityType());
		device.setNetworkConfig(request.getNetworkConfig());
		device.setCircuitId(request.getCircuitId());
		device.setAttributes(request.getAttributes());

		return toResponse(deviceRepository.save(device));
	}

	// ── 刪除 ─────────────────────────────────────────────────────────

	@Transactional
	public void delete(Long id) {
		Device device = deviceRepository.findById(id)
			.orElseThrow(() -> new BusinessException(ErrorCode.DEVICE_NOT_FOUND));

		if (deviceRepository.existsByParentDeviceId(id)) {
			throw new BusinessException(ErrorCode.DEVICE_HAS_CHILDREN);
		}
		deviceRepository.delete(device);
	}

	// ── 報廢 ─────────────────────────────────────────────────────────

	@Transactional
	public void decommission(Long id) {
		Device device = deviceRepository.findById(id)
			.orElseThrow(() -> new BusinessException(ErrorCode.DEVICE_NOT_FOUND));
		device.setStatus(DeviceStatus.DECOMMISSIONED);
	}

	// ── 設備樹 ───────────────────────────────────────────────────────

	public DeviceResponse getDeviceTree(Long id) {
		Device device = deviceRepository.findById(id)
			.orElseThrow(() -> new BusinessException(ErrorCode.DEVICE_NOT_FOUND));
		return toTreeResponse(device);
	}

	private DeviceResponse toTreeResponse(Device device) {
		DeviceResponse response = toResponse(device);
		List<Device> children = deviceRepository.findByParentDeviceIdAndStatusNot(device.getId(),
				DeviceStatus.DECOMMISSIONED);
		if (!children.isEmpty()) {
			response.setChildren(children.stream().map(this::toTreeResponse).toList());
		}
		return response;
	}

	// ── 內部方法 ─────────────────────────────────────────────────────

	void validateHierarchyDepth(Long parentId, int depth) {
		if (depth > 4) {
			throw new BusinessException(ErrorCode.DEVICE_CIRCULAR_REFERENCE);
		}
		Device parent = deviceRepository.findById(parentId).orElse(null);
		if (parent != null && parent.getParentDeviceId() != null) {
			validateHierarchyDepth(parent.getParentDeviceId(), depth + 1);
		}
	}

	DeviceResponse toResponse(Device device) {
		if (device == null) {
			return null;
		}
		long childrenCount = deviceRepository.countByParentDeviceIdAndStatusNot(device.getId(),
				DeviceStatus.DECOMMISSIONED);

		String deptName = null;
		if (device.getDeptId() != null) {
			deptName = deptInfoRepository.findById(device.getDeptId()).map(DeptInfoEntity::getDeptName).orElse(null);
		}

		String contractCode = null;
		if (device.getContractId() != null) {
			contractCode = contractRepository.findById(device.getContractId())
				.map(Contract::getContractCode)
				.orElse(null);
		}

		return DeviceResponse.builder()
			.id(device.getId())
			.deviceType(device.getDeviceType())
			.deviceCode(device.getDeviceCode())
			.deviceName(device.getDeviceName())
			.twd97X(device.getTwd97X())
			.twd97Y(device.getTwd97Y())
			.lng(device.getLng())
			.lat(device.getLat())
			.elevation(device.getElevation())
			.twd67X(device.getTwd67X())
			.twd67Y(device.getTwd67Y())
			.taipowerCoord(device.getTaipowerCoord())
			.deptId(device.getDeptId())
			.deptName(deptName)
			.contractId(device.getContractId())
			.contractCode(contractCode)
			.propertyOwner(device.getPropertyOwner())
			.status(device.getStatus())
			.installedAt(device.getInstalledAt())
			.decommissionedAt(device.getDecommissionedAt())
			.parentDeviceId(device.getParentDeviceId())
			.mountPosition(device.getMountPosition())
			.connectivityType(device.getConnectivityType())
			.networkConfig(device.getNetworkConfig())
			.lastHeartbeatAt(device.getLastHeartbeatAt())
			.circuitId(device.getCircuitId())
			.attributes(device.getAttributes())
			.childrenCount(childrenCount)
			.createdBy(device.getCreatedBy())
			.createdAt(device.getCreatedAt())
			.updatedAt(device.getUpdatedAt())
			.build();
	}

}
