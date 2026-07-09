package com.taipei.iot.vms.adapter;

import com.taipei.iot.common.enums.ErrorCode;
import com.taipei.iot.common.exception.BusinessException;
import com.taipei.iot.vms.VmsAdapter;
import com.taipei.iot.vms.dto.CameraStreamInfo;
import com.taipei.iot.vms.dto.PtzCommand;
import com.taipei.iot.vms.entity.VmsCamera;
import com.taipei.iot.vms.entity.VmsServer;
import com.taipei.iot.vms.enums.CameraStatus;
import com.taipei.iot.vms.enums.VmsType;
import com.taipei.iot.vms.repository.VmsServerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Nx Witness VMS Adapter。
 *
 * <p>
 * 透過 Nx Witness REST API (ec2) 與 VMS 伺服器通訊。 認證方式從 HTTP Basic 改為 REST API v1
 * session-based（{@link NxSessionManager}），每次呼叫由 {@code NxSessionManager} 取得有效的 session
 * token。
 * </p>
 *
 * <p>
 * Nx Witness API 參考：{@code POST /ec2/cameras/{id}/streams}、 {@code PUT
 * /ec2/cameras/{id}/ptz}、{@code GET /ec2/cameras} 等。 串流輸出經 ZLMediaKit 統一轉為 WebRTC。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NxWitnessAdapter implements VmsAdapter {

	private final VmsServerRepository vmsServerRepository;

	private final NxSessionManager nxSessionManager;

	@Override
	public VmsType getType() {
		return VmsType.NX_WITNESS;
	}

	@Override
	public CameraStreamInfo getLiveStreamUrl(String cameraId) {
		VmsServer server = resolveServer();
		RestClient client = buildRestClient(server);

		// POST /ec2/cameras/{id}/streams → 回傳 RTSP URL
		var response = client.post()
			.uri("/ec2/cameras/{id}/streams", cameraId)
			.body(new StreamRequest("rtsp"))
			.retrieve()
			.body(NxStreamResponse.class);

		if (response == null || response.streamUrl() == null) {
			throw new BusinessException(ErrorCode.VMS_STREAM_NOT_AVAILABLE, "無法取得相機 " + cameraId + " 的串流 URL");
		}

		return new CameraStreamInfo(cameraId, response.streamUrl(), null, VmsType.NX_WITNESS, null);
	}

	@Override
	public CameraStreamInfo getPlaybackUrl(String cameraId, Instant startTime, Instant endTime) {
		VmsServer server = resolveServer();
		RestClient client = buildRestClient(server);

		// POST /ec2/cameras/{id}/streams + time range → 回放 RTSP URL
		var response = client.post()
			.uri("/ec2/cameras/{id}/streams", cameraId)
			.body(new PlaybackStreamRequest("rtsp", startTime.toString(), endTime.toString()))
			.retrieve()
			.body(NxStreamResponse.class);

		if (response == null || response.streamUrl() == null) {
			throw new BusinessException(ErrorCode.VMS_STREAM_NOT_AVAILABLE, "無法取得相機 " + cameraId + " 的回放串流 URL");
		}

		return new CameraStreamInfo(cameraId, response.streamUrl(), null, VmsType.NX_WITNESS, null);
	}

	@Override
	public void controlPtz(String cameraId, PtzCommand command) {
		VmsServer server = resolveServer();
		RestClient client = buildRestClient(server);

		// PUT /ec2/cameras/{id}/ptz
		client.put()
			.uri("/ec2/cameras/{id}/ptz", cameraId)
			.body(new NxPtzRequest(command.direction(), command.speed()))
			.retrieve()
			.toBodilessEntity();

		log.info("PTZ 控制成功: cameraId={}, direction={}, speed={}", cameraId, command.direction(), command.speed());
	}

	@Override
	public VmsCamera getCameraInfo(String cameraId) {
		VmsServer server = resolveServer();
		RestClient client = buildRestClient(server);

		// GET /rest/v1/devices/{id} → 取得單一裝置資訊
		// 404 視為 null，由下游拋 BusinessException
		var device = client.get()
			.uri("/rest/v1/devices/{id}", cameraId)
			.retrieve()
			.onStatus(status -> status.value() == 404, (req, res) -> {
			})
			.body(NxDevice.class);

		if (device == null) {
			throw new BusinessException(ErrorCode.VMS_CAMERA_NOT_FOUND, "相機不存在: " + cameraId);
		}

		return VmsCamera.builder()
			.vmsCameraId(device.id())
			.displayName(device.name())
			.server(server)
			.status(mapStatus(device.status()))
			.build();
	}

	@Override
	public List<VmsCamera> listCameras(int page, int size) {
		VmsServer server = resolveServer();
		RestClient client = buildRestClient(server);

		// GET /rest/v1/devices?_filter=deviceType=Camera&_orderBy=name
		// 只取 deviceType=Camera 的裝置，依名稱排序
		// REST API v1 直接回傳 JSON array，使用 ParameterizedTypeReference 解析
		var response = client.get()
			.uri("/rest/v1/devices?deviceType=Camera&_orderBy=name")
			.retrieve()
			.body(new ParameterizedTypeReference<List<NxDevice>>() {
			});

		if (response == null) {
			return List.of();
		}

		return response.stream()
			.map(d -> VmsCamera.builder()
				.vmsCameraId(d.id())
				.displayName(d.name())
				.server(server)
				.status(mapStatus(d.status()))
				.metadata(Map.of("vendor", d.vendor() != null ? d.vendor() : "", "model",
						d.model() != null ? d.model() : "", "physicalId", d.physicalId() != null ? d.physicalId() : "",
						"deviceType", d.deviceType() != null ? d.deviceType() : "", "groupName",
						d.group() != null ? d.group().name() : "", "isLicenseUsed", d.isLicenseUsed()))
				.build())
			.toList();
	}

	@Override
	public boolean healthCheck() {
		try {
			VmsServer server = resolveServer();
			// GET /rest/v1/system/info 免授權；使用 buildRestClient 以利測試注入 mock
			RestClient client = buildRestClient(server);
			var response = client.get().uri("/rest/v1/system/info").retrieve().body(NxSystemInfoResponse.class);
			return response != null && response.name() != null;
		}
		catch (Exception ex) {
			log.warn("Nx Witness 健康檢查失敗: {}", ex.getMessage());
			return false;
		}
	}

	/**
	 * 查詢目前租戶下第一個啟用的 Nx Witness 伺服器。 此處以 tenantId 取得 active server（實際 tenantId 由呼叫端透過
	 * TenantContext 提供）。
	 */
	private VmsServer resolveServer() {
		List<VmsServer> servers = vmsServerRepository
			.findByTenantIdAndIsActiveTrue(com.taipei.iot.common.context.TenantContext.getCurrentTenantId());
		if (servers.isEmpty()) {
			throw new BusinessException(ErrorCode.VMS_SERVER_NOT_FOUND, "無啟用的 Nx Witness 伺服器");
		}
		return servers.getFirst();
	}

	/**
	 * 建立針對特定 VMS 伺服器的 RestClient。 使用 {@link NxSessionManager#getToken(VmsServer)} 取得
	 * session token 作為 Bearer token。 package-private 以便測試覆寫。
	 */
	RestClient buildRestClient(VmsServer server) {
		String token = nxSessionManager.getToken(server);
		return RestClient.builder()
			.baseUrl(server.getBaseUrl())
			.defaultHeader("Authorization", "Bearer " + token)
			.build();
	}

	// ── 狀態映射 ──────────────────────────────────────────────

	/** 將 Nx Witness 狀態字串映射為本地 CameraStatus。 */
	private CameraStatus mapStatus(String nxStatus) {
		if (nxStatus == null)
			return CameraStatus.ERROR;
		return switch (nxStatus) {
			case "Online", "Recording" -> CameraStatus.ONLINE;
			case "Offline", "Unauthorized" -> CameraStatus.OFFLINE;
			default -> CameraStatus.ERROR;
		};
	}

	// ── Nx Witness API 請求/回應 DTO（內部類別） ────────────────

	/** ec2: 即時串流請求 */
	private record StreamRequest(String streamType) {
	}

	/** ec2: 回放串流請求 */
	private record PlaybackStreamRequest(String streamType, String startTime, String endTime) {
	}

	/** ec2: PTZ 請求 */
	private record NxPtzRequest(String command, Integer speed) {
	}

	/** ec2: 串流回應 */
	private record NxStreamResponse(String streamUrl) {
	}

	/** REST API v1: 系統資訊回應（免授權健康檢查） */
	private record NxSystemInfoResponse(String name, String version, String customization) {
	}

	/** REST API v1: 裝置回應（GET /rest/v1/devices 與 GET /rest/v1/devices/{id} 共用） */
	private record NxDevice(String id, String name, String physicalId, String url, String status, String deviceType,
			String vendor, String model, NxDeviceGroup group, boolean isLicenseUsed) {
	}

	/** REST API v1: 裝置群組 */
	private record NxDeviceGroup(String id, String name) {
	}

}
