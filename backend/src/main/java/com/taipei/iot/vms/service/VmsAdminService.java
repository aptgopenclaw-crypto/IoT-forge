package com.taipei.iot.vms.service;

import com.taipei.iot.common.context.TenantContext;
import com.taipei.iot.common.dept.port.VisibleDeptScopeProvider;
import com.taipei.iot.common.enums.ErrorCode;
import com.taipei.iot.common.exception.BusinessException;
import com.taipei.iot.vms.VmsAdapterManager;
import com.taipei.iot.vms.dto.VmsCameraRequest;
import com.taipei.iot.vms.dto.VmsCameraResponse;
import com.taipei.iot.vms.dto.VmsServerRequest;
import com.taipei.iot.vms.dto.VmsServerResponse;
import com.taipei.iot.vms.entity.VmsCamera;
import com.taipei.iot.vms.entity.VmsServer;
import com.taipei.iot.vms.repository.VmsCameraRepository;
import com.taipei.iot.vms.repository.VmsServerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;

/**
 * VMS 管理 CRUD 服務。
 */
@Service
@RequiredArgsConstructor
public class VmsAdminService {

	private final VmsServerRepository vmsServerRepository;

	private final VmsCameraRepository vmsCameraRepository;

	private final VmsAdapterManager vmsAdapterManager;

	private final VisibleDeptScopeProvider visibleDeptScopeProvider;

	// ── Servers ─────────────────────────────────────────────────

	@Transactional(readOnly = true)
	public List<VmsServerResponse> listServers() {
		String tenantId = TenantContext.getCurrentTenantId();
		return vmsServerRepository.findByTenantIdAndIsActiveTrue(tenantId)
			.stream()
			.map(VmsServerResponse::from)
			.toList();
	}

	@Transactional(readOnly = true)
	public VmsServerResponse getServer(Long id) {
		String tenantId = TenantContext.getCurrentTenantId();
		VmsServer server = vmsServerRepository.findByIdAndTenantId(id, tenantId)
			.orElseThrow(() -> new BusinessException(ErrorCode.VMS_SERVER_NOT_FOUND, "VMS 伺服器不存在"));
		return VmsServerResponse.from(server);
	}

	@Transactional
	public VmsServerResponse createServer(VmsServerRequest request) {
		String tenantId = TenantContext.getCurrentTenantId();
		VmsServer server = VmsServer.builder()
			.tenantId(tenantId)
			.name(request.name())
			.vmsType(request.vmsType())
			.baseUrl(request.baseUrl())
			.authType(request.authType() != null ? request.authType() : com.taipei.iot.vms.enums.VmsAuthType.BASIC)
			.authUsername(request.authUsername())
			.authPassword(request.authPassword())
			.apiToken(request.apiToken())
			.isActive(true)
			.build();
		server = vmsServerRepository.save(server);
		return VmsServerResponse.from(server);
	}

	@Transactional
	public VmsServerResponse updateServer(Long id, VmsServerRequest request) {
		String tenantId = TenantContext.getCurrentTenantId();
		VmsServer server = vmsServerRepository.findByIdAndTenantId(id, tenantId)
			.orElseThrow(() -> new BusinessException(ErrorCode.VMS_SERVER_NOT_FOUND, "VMS 伺服器不存在"));
		server.setName(request.name());
		server.setVmsType(request.vmsType());
		server.setBaseUrl(request.baseUrl());
		if (request.authType() != null) {
			server.setAuthType(request.authType());
		}
		server.setAuthUsername(request.authUsername());
		server.setAuthPassword(request.authPassword());
		server.setApiToken(request.apiToken());
		server = vmsServerRepository.save(server);
		return VmsServerResponse.from(server);
	}

	@Transactional
	public void deleteServer(Long id) {
		String tenantId = TenantContext.getCurrentTenantId();
		VmsServer server = vmsServerRepository.findByIdAndTenantId(id, tenantId)
			.orElseThrow(() -> new BusinessException(ErrorCode.VMS_SERVER_NOT_FOUND, "VMS 伺服器不存在"));
		// 軟刪除：設為非啟用（保留資料）
		server.setIsActive(false);
		vmsServerRepository.save(server);
	}

	@Transactional
	public VmsServerResponse testConnection(Long id) {
		String tenantId = TenantContext.getCurrentTenantId();
		VmsServer server = vmsServerRepository.findByIdAndTenantId(id, tenantId)
			.orElseThrow(() -> new BusinessException(ErrorCode.VMS_SERVER_NOT_FOUND, "VMS 伺服器不存在"));
		boolean connected = vmsAdapterManager.getAdapter(server.getVmsType()).healthCheck();
		if (!connected) {
			throw new BusinessException(ErrorCode.VMS_CONNECTION_FAILED, "VMS 連線失敗: " + server.getBaseUrl());
		}
		return VmsServerResponse.from(server);
	}

	// ── Cameras ────────────────────────────────────────────────

	@Transactional(readOnly = true)
	public List<VmsCameraResponse> listCameras(Long serverId) {
		String tenantId = TenantContext.getCurrentTenantId();
		List<Long> visibleDeptIds = visibleDeptScopeProvider.getVisibleDeptIds();
		Collection<Long> deptFilter = visibleDeptIds.isEmpty() ? null : visibleDeptIds;

		List<VmsCamera> cameras;
		if (deptFilter != null) {
			if (serverId != null) {
				cameras = vmsCameraRepository.findByServerIdAndTenantIdAndDeptIdIn(serverId, tenantId, deptFilter);
			}
			else {
				cameras = vmsCameraRepository.findByTenantIdAndDeptIdIn(tenantId, deptFilter);
			}
		}
		else {
			// ALL scope — 不限制部門
			if (serverId != null) {
				cameras = vmsCameraRepository.findByServerIdAndTenantId(serverId, tenantId);
			}
			else {
				cameras = vmsCameraRepository.findByTenantId(tenantId);
			}
		}
		return cameras.stream().map(VmsCameraResponse::from).toList();
	}

	@Transactional
	public VmsCameraResponse createCamera(VmsCameraRequest request) {
		String tenantId = TenantContext.getCurrentTenantId();
		VmsServer server = vmsServerRepository.findByIdAndTenantId(request.serverId(), tenantId)
			.orElseThrow(() -> new BusinessException(ErrorCode.VMS_SERVER_NOT_FOUND, "VMS 伺服器不存在"));

		VmsCamera camera = VmsCamera.builder()
			.tenantId(tenantId)
			.server(server)
			.vmsCameraId(request.vmsCameraId())
			.displayName(request.displayName())
			.rtspUrl(request.rtspUrl())
			.deviceId(request.deviceId())
			.deptId(request.deptId())
			.status(com.taipei.iot.vms.enums.CameraStatus.ONLINE)
			.build();
		camera = vmsCameraRepository.save(camera);
		return VmsCameraResponse.from(camera);
	}

	@Transactional
	public void deleteCamera(Long id) {
		String tenantId = TenantContext.getCurrentTenantId();
		VmsCamera camera = vmsCameraRepository.findByIdAndTenantId(id, tenantId)
			.orElseThrow(() -> new BusinessException(ErrorCode.VMS_CAMERA_NOT_FOUND, "攝影機不存在"));
		vmsCameraRepository.delete(camera);
	}

}
