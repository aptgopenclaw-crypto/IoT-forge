package com.taipei.iot.vms.controller;

import com.taipei.iot.vms.service.StreamTokenService;
import com.taipei.iot.vms.service.ZlMediaKitClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * ZLMediaKit 串流 Hook 接收端點。
 *
 * <p>
 * ZLMediaKit 在播放前會呼叫 {@code on_play} hook 驗證 token， 無人觀看時呼叫 {@code on_stream_none_reader}
 * hook 通知釋放串流。
 * </p>
 *
 * <p>
 * 此端點不經 JWT 驗證，僅供 ZLMediaKit 內部網路呼叫。
 * </p>
 */
@Slf4j
@RestController
@RequestMapping("/v1/vms/stream-hook")
@RequiredArgsConstructor
public class VmsStreamHookController {

	private final StreamTokenService streamTokenService;

	private final ZlMediaKitClient zlMediaKitClient;

	/**
	 * ZLMediaKit 播放前 hook。
	 *
	 * <p>
	 * 請求格式（ZLMediaKit 自動發送）： <pre>
	 * {
	 *   "app": "vms",
	 *   "stream": "vms_1",
	 *   "params": { "token": "xxx" }
	 * }
	 * </pre> 回傳 {@code {"code": 0}} 允許播放，{@code {"code": -1}} 拒絕播放。
	 */
	@PostMapping("/on_play")
	public ResponseEntity<Map<String, Object>> onPlay(@RequestBody Map<String, Object> request) {
		try {
			// 從 params 中取出 token
			@SuppressWarnings("unchecked")
			Map<String, Object> params = (Map<String, Object>) request.get("params");
			String token = params != null ? (String) params.get("token") : null;

			if (token == null || token.isBlank()) {
				log.warn("ZLMediaKit on_play: 缺少 token, stream={}", request.get("stream"));
				return ResponseEntity.ok(Map.of("code", -1, "msg", "missing token"));
			}

			streamTokenService.validateToken(token);
			log.debug("ZLMediaKit on_play: token 驗證成功, stream={}", request.get("stream"));
			return ResponseEntity.ok(Map.of("code", 0, "msg", "success"));
		}
		catch (Exception ex) {
			log.warn("ZLMediaKit on_play: token 驗證失敗: {}", ex.getMessage());
			return ResponseEntity.ok(Map.of("code", -1, "msg", ex.getMessage()));
		}
	}

	/**
	 * ZLMediaKit 串流無人觀看 hook。
	 *
	 * <p>
	 * 當串流的所有消費者斷開後觸發，自動關閉串流釋放資源。
	 * </p>
	 */
	@PostMapping("/on_stream_none_reader")
	public ResponseEntity<Map<String, Object>> onStreamNoneReader(@RequestBody Map<String, Object> request) {
		String stream = (String) request.get("stream");
		if (stream != null && stream.startsWith("vms_")) {
			log.info("ZLMediaKit 串流無人觀看，自動關閉: stream={}", stream);
			zlMediaKitClient.closeStream(stream);
		}
		return ResponseEntity.ok(Map.of("code", 0, "msg", "success"));
	}

}
