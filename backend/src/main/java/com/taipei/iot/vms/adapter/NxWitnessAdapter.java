package com.taipei.iot.vms.adapter;

import com.taipei.iot.common.enums.ErrorCode;
import com.taipei.iot.common.exception.BusinessException;
import com.taipei.iot.vms.VmsAdapter;
import com.taipei.iot.vms.dto.CameraStreamInfo;
import com.taipei.iot.vms.dto.PtzCommand;
import com.taipei.iot.vms.entity.VmsCamera;
import com.taipei.iot.vms.entity.VmsServer;
import com.taipei.iot.vms.enums.VmsType;
import com.taipei.iot.vms.repository.VmsServerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.List;

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

		// GET /ec2/cameras/{id}
		var response = client.get().uri("/ec2/cameras/{id}", cameraId).retrieve().body(NxCameraInfoResponse.class);

		if (response == null) {
			throw new BusinessException(ErrorCode.VMS_CAMERA_NOT_FOUND, "相機不存在: " + cameraId);
		}

		return VmsCamera.builder().vmsCameraId(cameraId).displayName(response.displayName()).server(server).build();
	}

	@Override
	public List<VmsCamera> listCameras(int page, int size) {
		VmsServer server = resolveServer();
		RestClient client = buildRestClient(server);

		// GET /ec2/cameras?page={page}&pageSize={size}
		var response = client.get()
			.uri("/ec2/cameras?page={page}&pageSize={size}", page, size)
			.retrieve()
			.body(NxCameraListResponse.class);

		if (response == null || response.cameras() == null) {
			return List.of();
		}

		return response.cameras()
			.stream()
			.map(c -> VmsCamera.builder().vmsCameraId(c.id()).displayName(c.displayName()).server(server).build())
			.toList();
	}

	@Override
	public boolean healthCheck() {
		try {
			VmsServer server = resolveServer();
			RestClient client = buildRestClient(server);

			// GET /ec2/server/info 確認連線
			client.get().uri("/ec2/server/info").retrieve().toBodilessEntity();
			return true;
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

	// ── Nx Witness API 請求/回應 DTO（內部類別） ────────────────

	private record StreamRequest(String streamType) {
	}

	private record PlaybackStreamRequest(String streamType, String startTime, String endTime) {
	}

	private record NxPtzRequest(String command, Integer speed) {
	}

	private record NxStreamResponse(String streamUrl) {
	}

	private record NxCameraInfoResponse(String id, String displayName) {
	}

	private record NxCameraListResponse(List<NxCameraItem> cameras) {
	}

	private record NxCameraItem(String id, String displayName) {
	}

}
