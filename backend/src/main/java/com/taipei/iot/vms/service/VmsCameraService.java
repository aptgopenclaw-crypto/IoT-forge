package com.taipei.iot.vms.service;

import com.taipei.iot.common.context.TenantContext;
import com.taipei.iot.common.enums.ErrorCode;
import com.taipei.iot.common.exception.BusinessException;
import com.taipei.iot.vms.dto.VmsCameraMappingDTO;
import com.taipei.iot.vms.dto.VmsCameraMappingRequest;
import com.taipei.iot.vms.entity.VmsCameraMappingEntity;
import com.taipei.iot.vms.entity.VmsServerEntity;
import com.taipei.iot.vms.repository.VmsCameraMappingRepository;
import com.taipei.iot.vms.repository.VmsServerRepository;
import com.taipei.iot.vms.token.NxTokenManager;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class VmsCameraService {

	private final VmsCameraMappingRepository repository;

	private final VmsServerRepository serverRepository;

	private final NxTokenManager nxTokenManager;

	private final RestTemplate restTemplate = new RestTemplate();

	public List<VmsCameraMappingDTO> findAll() {
		return repository.findAll().stream().map(this::toDTO).toList();
	}

	public VmsCameraMappingDTO findById(Long id) {
		return toDTO(repository.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.VMS_CAMERA_NOT_FOUND)));
	}

	public VmsCameraMappingDTO create(VmsCameraMappingRequest request) {
		// Verify server exists
		serverRepository.findById(request.getServerId())
			.orElseThrow(() -> new BusinessException(ErrorCode.VMS_SERVER_NOT_FOUND));

		VmsCameraMappingEntity entity = VmsCameraMappingEntity.builder()
			.tenantId(TenantContext.getCurrentTenantId())
			.serverId(request.getServerId())
			.vmsCameraId(request.getVmsCameraId())
			.displayName(request.getDisplayName())
			.deptId(request.getDeptId())
			.rtspUrl(request.getRtspUrl())
			.status("ONLINE")
			.build();
		return toDTO(repository.save(entity));
	}

	public VmsCameraMappingDTO update(Long id, VmsCameraMappingRequest request) {
		VmsCameraMappingEntity entity = repository.findById(id)
			.orElseThrow(() -> new BusinessException(ErrorCode.VMS_CAMERA_NOT_FOUND));
		entity.setServerId(request.getServerId());
		entity.setVmsCameraId(request.getVmsCameraId());
		entity.setDisplayName(request.getDisplayName());
		entity.setDeptId(request.getDeptId());
		entity.setRtspUrl(request.getRtspUrl());
		return toDTO(repository.save(entity));
	}

	public void delete(Long id) {
		repository.deleteById(id);
	}

	public List<Map<String, Object>> syncCamerasFromServer(Long serverId) {
		VmsServerEntity server = serverRepository.findById(serverId)
			.orElseThrow(() -> new BusinessException(ErrorCode.VMS_SERVER_NOT_FOUND));

		String token = nxTokenManager.getToken(serverId);
		String url = server.getBaseUrl() + "/rest/v1/devices?_with=id,name,status,deviceType";

		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(token);
		headers.set("x-runtime-guid", token);

		try {
			ResponseEntity<List> response = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers),
					List.class);
			List<Map<String, Object>> devices = response.getBody();

			if (devices != null) {
				for (Map<String, Object> device : devices) {
					String deviceId = (String) device.get("id");
					String deviceName = (String) device.get("name");
					String deviceStatus = (String) device.get("status");
					String deviceType = (String) device.get("deviceType");

					// Only import cameras
					if (!"Camera".equalsIgnoreCase(deviceType))
						continue;

					String nxCameraId = deviceId;

					// Upsert
					var existing = repository.findByVmsCameraId(nxCameraId);
					if (existing.isEmpty()) {
						VmsCameraMappingEntity newCam = VmsCameraMappingEntity.builder()
							.tenantId(TenantContext.getCurrentTenantId())
							.serverId(serverId)
							.vmsCameraId(nxCameraId)
							.displayName(deviceName)
							.status(mapNxStatus(deviceStatus))
							.build();
						repository.save(newCam);
					}
					else {
						VmsCameraMappingEntity cam = existing.get();
						cam.setStatus(mapNxStatus(deviceStatus));
						if (cam.getDisplayName() == null) {
							cam.setDisplayName(deviceName);
						}
						repository.save(cam);
					}
				}
			}
			return devices;
		}
		catch (Exception e) {
			throw new BusinessException(ErrorCode.VMS_CONNECTION_FAILED, "Failed to sync cameras: " + e.getMessage());
		}
	}

	private String mapNxStatus(String nxStatus) {
		if (nxStatus == null)
			return "OFFLINE";
		return switch (nxStatus.toUpperCase()) {
			case "ONLINE", "RECORDING" -> "ONLINE";
			case "UNAUTHORIZED" -> "ERROR";
			default -> "OFFLINE";
		};
	}

	private VmsCameraMappingDTO toDTO(VmsCameraMappingEntity entity) {
		String serverName = serverRepository.findById(entity.getServerId()).map(VmsServerEntity::getName).orElse(null);
		return VmsCameraMappingDTO.builder()
			.id(entity.getId())
			.serverId(entity.getServerId())
			.serverName(serverName)
			.vmsCameraId(entity.getVmsCameraId())
			.displayName(entity.getDisplayName())
			.deptId(entity.getDeptId())
			.status(entity.getStatus())
			.rtspUrl(entity.getRtspUrl())
			.build();
	}

}
