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
import java.util.Base64;
import java.util.List;

/**
 * Axxon Next VMS Adapter。
 *
 * <p>
 * 透過 Axxon Next REST API (v1) 與 VMS 伺服器通訊。 API 參考：{@code GET
 * /api/rest/v1/cameras/{id}/stream}、 {@code PUT /api/rest/v1/cameras/{id}/ptz} 等。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AxxonAdapter implements VmsAdapter {

	private static final String API_PATH = "/api/rest/v1";

	private final VmsServerRepository vmsServerRepository;

	@Override
	public VmsType getType() {
		return VmsType.AXXON;
	}

	@Override
	public CameraStreamInfo getLiveStreamUrl(String cameraId) {
		VmsServer server = resolveServer();
		RestClient client = buildRestClient(server);

		var response = client.get()
			.uri(API_PATH + "/cameras/{id}/stream", cameraId)
			.retrieve()
			.body(AxxonStreamResponse.class);

		if (response == null || response.streamUrl() == null) {
			throw new BusinessException(ErrorCode.VMS_STREAM_NOT_AVAILABLE, "無法取得 Axxon 相機 " + cameraId + " 的串流 URL");
		}
		return new CameraStreamInfo(cameraId, response.streamUrl(), null, VmsType.AXXON, null);
	}

	@Override
	public CameraStreamInfo getPlaybackUrl(String cameraId, Instant startTime, Instant endTime) {
		VmsServer server = resolveServer();
		RestClient client = buildRestClient(server);

		var response = client.get()
			.uri(API_PATH + "/cameras/{id}/playback?start={start}&end={end}", cameraId, startTime.toString(),
					endTime.toString())
			.retrieve()
			.body(AxxonStreamResponse.class);

		if (response == null || response.streamUrl() == null) {
			throw new BusinessException(ErrorCode.VMS_STREAM_NOT_AVAILABLE, "無法取得 Axxon 相機 " + cameraId + " 的回放串流 URL");
		}
		return new CameraStreamInfo(cameraId, response.streamUrl(), null, VmsType.AXXON, null);
	}

	@Override
	public void controlPtz(String cameraId, PtzCommand command) {
		VmsServer server = resolveServer();
		RestClient client = buildRestClient(server);

		client.put()
			.uri(API_PATH + "/cameras/{id}/ptz", cameraId)
			.body(new AxxonPtzRequest(command.direction(), command.speed()))
			.retrieve()
			.toBodilessEntity();

		log.info("Axxon PTZ 控制成功: cameraId={}, direction={}", cameraId, command.direction());
	}

	@Override
	public VmsCamera getCameraInfo(String cameraId) {
		VmsServer server = resolveServer();
		RestClient client = buildRestClient(server);

		var response = client.get()
			.uri(API_PATH + "/cameras/{id}", cameraId)
			.retrieve()
			.body(AxxonCameraInfoResponse.class);

		if (response == null) {
			throw new BusinessException(ErrorCode.VMS_CAMERA_NOT_FOUND, "Axxon 相機不存在: " + cameraId);
		}
		return VmsCamera.builder().vmsCameraId(cameraId).displayName(response.displayName()).server(server).build();
	}

	@Override
	public List<VmsCamera> listCameras(int page, int size) {
		VmsServer server = resolveServer();
		RestClient client = buildRestClient(server);

		var response = client.get()
			.uri(API_PATH + "/cameras?page={page}&pageSize={size}", page, size)
			.retrieve()
			.body(AxxonCameraListResponse.class);

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

			client.get().uri(API_PATH + "/system/status").retrieve().toBodilessEntity();
			return true;
		}
		catch (Exception ex) {
			log.warn("Axxon 健康檢查失敗: {}", ex.getMessage());
			return false;
		}
	}

	private VmsServer resolveServer() {
		List<VmsServer> servers = vmsServerRepository
			.findByTenantIdAndIsActiveTrue(com.taipei.iot.common.context.TenantContext.getCurrentTenantId());
		if (servers.isEmpty()) {
			throw new BusinessException(ErrorCode.VMS_SERVER_NOT_FOUND, "無啟用的 Axxon 伺服器");
		}
		return servers.getFirst();
	}

	RestClient buildRestClient(VmsServer server) {
		RestClient.Builder builder = RestClient.builder().baseUrl(server.getBaseUrl());

		switch (server.getAuthType()) {
			case BASIC -> {
				String cred = server.getAuthUsername() + ":" + server.getAuthPassword();
				builder.defaultHeader("Authorization", "Basic " + Base64.getEncoder().encodeToString(cred.getBytes()));
			}
			case TOKEN -> builder.defaultHeader("Authorization", "Bearer " + server.getApiToken());
			case CERT -> log.warn("CERT 認證尚未實作，Axxon adapter 將以無認證方式連線");
		}

		return builder.build();
	}

	// ── Axxon API 請求/回應 DTO ─────────────────────────────

	private record AxxonStreamResponse(String streamUrl) {
	}

	private record AxxonPtzRequest(String command, Integer speed) {
	}

	private record AxxonCameraInfoResponse(String id, String displayName) {
	}

	private record AxxonCameraListResponse(List<AxxonCameraItem> cameras) {
	}

	private record AxxonCameraItem(String id, String displayName) {
	}

}
