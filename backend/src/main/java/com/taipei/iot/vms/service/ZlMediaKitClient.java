package com.taipei.iot.vms.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.taipei.iot.common.enums.ErrorCode;
import com.taipei.iot.common.exception.BusinessException;
import com.taipei.iot.vms.config.VmsProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * ZLMediaKit 媒體伺服器客戶端。
 *
 * <p>
 * 透過 ZLMediaKit REST API 控制串流的建立與釋放。統一以 WebRTC 輸出， 各 VMS adapter（Nx Witness / Milestone /
 * Axxon）取得的 RTSP 串流皆經由此 轉換為 WebRTC 格式，前端以 iframe 嵌入 ZLMediaKit 內建 player 播放。
 * </p>
 *
 * <p>
 * WebRTC 延遲 &lt; 500ms，適合 PTZ 即時控制與安全監控場景。 另保留 FLV 路徑作為降級方案（可透過建構子參數切換輸出格式）。
 * </p>
 */
@Slf4j
@Component
public class ZlMediaKitClient {

	private final RestClient restClient;

	private final VmsProperties vmsProperties;

	public ZlMediaKitClient(VmsProperties vmsProperties, RestClient.Builder restClientBuilder) {
		this.vmsProperties = vmsProperties;
		this.restClient = restClientBuilder.baseUrl(vmsProperties.getMediaServer().getApiUrl())
			.defaultHeader("Content-Type", "application/json")
			.build();
	}

	/**
	 * 要求 ZLMediaKit 拉取 RTSP 串流，回傳轉換後的播放 URL。
	 * @param rtspUrl 原始 RTSP 串流 URL
	 * @param streamId 自訂串流識別碼
	 * @return 統一播放 URL（FLV / WebRTC / HLS）
	 */
	public String addStreamProxy(String rtspUrl, String streamId) {
		Map<String, Object> body = Map.of("vhost", "__defaultVhost__", "app", "vms", "stream", streamId, "url", rtspUrl,
				"rtp_type", 0);

		var response = restClient.post()
			.uri("/index/api/addStreamProxy")
			.body(body)
			.retrieve()
			.body(ZlMediaKitResponse.class);

		if (response == null || response.code() != 0) {
			String msg = response != null ? response.msg() : "null response";
			log.warn("ZLMediaKit addStreamProxy 失敗: code={}, msg={}", response != null ? response.code() : -1, msg);
			throw new BusinessException(ErrorCode.VMS_STREAM_NOT_AVAILABLE, "媒體伺服器無法建立串流: " + msg);
		}

		String playUrl = buildPlayUrl(streamId);
		log.info("ZLMediaKit 串流已建立: streamId={}, playUrl={}", streamId, playUrl);
		return playUrl;
	}

	/**
	 * 關閉串流。
	 * @param streamId 串流識別碼
	 */
	public void closeStream(String streamId) {
		Map<String, Object> body = Map.of("vhost", "__defaultVhost__", "app", "vms", "stream", streamId);

		try {
			restClient.post().uri("/index/api/close_stream").body(body).retrieve().toBodilessEntity();
			log.info("ZLMediaKit 串流已關閉: streamId={}", streamId);
		}
		catch (Exception ex) {
			log.warn("ZLMediaKit closeStream 失敗 (streamId={}): {}", streamId, ex.getMessage());
		}
	}

	/**
	 * 建構對外播放 URL（WebRTC）。
	 *
	 * <p>
	 * 回傳 ZLMediaKit 內建 WebRTC player 頁面 URL，前端以 iframe 嵌入即可播放。 ZLMediaKit WebRTC player
	 * 支援 H264/H265 + Opus/G711，延遲 &lt; 500ms。
	 * </p>
	 *
	 * <p>
	 * 各 VMS adapter（Nx Witness / Milestone / Axxon）皆透過此單一入口輸出 WebRTC 串流。
	 * </p>
	 */
	private String buildPlayUrl(String streamId) {
		String publicUrl = vmsProperties.getMediaServer().getPublicUrl();
		// WebRTC player 頁面，embed 於 iframe
		return publicUrl + "/webrtcplayer/?streamId=" + streamId + "&app=vms&schema=http";
	}

	/**
	 * ZLMediaKit API 通用回應格式。
	 */
	private record ZlMediaKitResponse(int code, String msg, ZlMediaKitData data) {
	}

	private record ZlMediaKitData(String vhost, String app, String stream, @JsonProperty("stream_id") String streamId) {
	}

}
