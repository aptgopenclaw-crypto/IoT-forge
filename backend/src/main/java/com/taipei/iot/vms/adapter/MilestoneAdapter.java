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
 * Milestone XProtect VMS Adapter。
 *
 * <p>
 * 透過 Milestone REST API (v2) 與 VMS 伺服器通訊。 注意：Milestone 的強項是 C# MIP SDK，Java 端僅建議使用 REST
 * API。 若 REST API 無法滿足需求，應另建輕量 C# .NET 微服務跑 MIP SDK，透過 gRPC 通訊。
 * </p>
 *
 * <p>
 * API 參考：{@code GET /API/rest/v2/devices/{id}/streams/live}、 {@code PUT
 * /API/rest/v2/devices/{id}/ptz} 等。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MilestoneAdapter implements VmsAdapter {

	private static final String API_PATH = "/API/rest/v2";

	private final VmsServerRepository vmsServerRepository;

	@Override
	public VmsType getType() {
		return VmsType.MILESTONE;
	}

	@Override
	public CameraStreamInfo getLiveStreamUrl(String cameraId) {
		VmsServer server = resolveServer();
		RestClient client = buildRestClient(server);

		var response = client.get()
			.uri(API_PATH + "/devices/{id}/streams/live", cameraId)
			.retrieve()
			.body(MilestoneStreamResponse.class);

		if (response == null || response.streamUrl() == null) {
			throw new BusinessException(ErrorCode.VMS_STREAM_NOT_AVAILABLE,
					"無法取得 Milestone 相機 " + cameraId + " 的串流 URL");
		}
		return new CameraStreamInfo(cameraId, response.streamUrl(), null, VmsType.MILESTONE, null);
	}

	@Override
	public CameraStreamInfo getPlaybackUrl(String cameraId, Instant startTime, Instant endTime) {
		VmsServer server = resolveServer();
		RestClient client = buildRestClient(server);

		var response = client.get()
			.uri(API_PATH + "/devices/{id}/streams/playback?start={start}&end={end}", cameraId, startTime.toString(),
					endTime.toString())
			.retrieve()
			.body(MilestoneStreamResponse.class);

		if (response == null || response.streamUrl() == null) {
			throw new BusinessException(ErrorCode.VMS_STREAM_NOT_AVAILABLE,
					"無法取得 Milestone 相機 " + cameraId + " 的回放串流 URL");
		}
		return new CameraStreamInfo(cameraId, response.streamUrl(), null, VmsType.MILESTONE, null);
	}

	@Override
	public void controlPtz(String cameraId, PtzCommand command) {
		VmsServer server = resolveServer();
		RestClient client = buildRestClient(server);

		client.put()
			.uri(API_PATH + "/devices/{id}/ptz?command={cmd}", cameraId, command.direction())
			.retrieve()
			.toBodilessEntity();

		log.info("Milestone PTZ 控制成功: cameraId={}, direction={}", cameraId, command.direction());
	}

	@Override
	public VmsCamera getCameraInfo(String cameraId) {
		VmsServer server = resolveServer();
		RestClient client = buildRestClient(server);

		var response = client.get()
			.uri(API_PATH + "/devices/{id}", cameraId)
			.retrieve()
			.body(MilestoneDeviceResponse.class);

		if (response == null) {
			throw new BusinessException(ErrorCode.VMS_CAMERA_NOT_FOUND, "Milestone 相機不存在: " + cameraId);
		}
		return VmsCamera.builder().vmsCameraId(cameraId).displayName(response.displayName()).server(server).build();
	}

	@Override
	public List<VmsCamera> listCameras(int page, int size) {
		VmsServer server = resolveServer();
		RestClient client = buildRestClient(server);

		var response = client.get()
			.uri(API_PATH + "/devices?page={page}&pageSize={size}", page, size)
			.retrieve()
			.body(MilestoneDeviceListResponse.class);

		if (response == null || response.devices() == null) {
			return List.of();
		}
		return response.devices()
			.stream()
			.map(d -> VmsCamera.builder().vmsCameraId(d.id()).displayName(d.displayName()).server(server).build())
			.toList();
	}

	@Override
	public boolean healthCheck() {
		try {
			VmsServer server = resolveServer();
			RestClient client = buildRestClient(server);

			client.get().uri(API_PATH + "/system/info").retrieve().toBodilessEntity();
			return true;
		}
		catch (Exception ex) {
			log.warn("Milestone 健康檢查失敗: {}", ex.getMessage());
			return false;
		}
	}

	private VmsServer resolveServer() {
		List<VmsServer> servers = vmsServerRepository
			.findByTenantIdAndIsActiveTrue(com.taipei.iot.common.context.TenantContext.getCurrentTenantId());
		if (servers.isEmpty()) {
			throw new BusinessException(ErrorCode.VMS_SERVER_NOT_FOUND, "無啟用的 Milestone 伺服器");
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
			case CERT -> log.warn("CERT 認證尚未實作，Milestone adapter 將以無認證方式連線");
		}

		return builder.build();
	}

	// ── Milestone API 回應 DTO ─────────────────────────────

	private record MilestoneStreamResponse(String streamUrl) {
	}

	private record MilestoneDeviceResponse(String id, String displayName) {
	}

	private record MilestoneDeviceListResponse(List<MilestoneDeviceItem> devices) {
	}

	private record MilestoneDeviceItem(String id, String displayName) {
	}

}
