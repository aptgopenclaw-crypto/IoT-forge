package com.taipei.iot.vms.controller;

import com.taipei.iot.common.response.BaseResponse;
import com.taipei.iot.vms.dto.StreamRequestDTO;
import com.taipei.iot.vms.service.HlsProxyService;
import com.taipei.iot.vms.service.VmsStreamService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/v1/auth/vms")
@RequiredArgsConstructor
public class VmsStreamController {

	private final VmsStreamService vmsStreamService;

	private final HlsProxyService hlsProxyService;

	@PostMapping("/{cameraId}/stream")
	public BaseResponse<Map<String, Object>> createStream(@PathVariable Long cameraId,
			@RequestBody StreamRequestDTO request, Authentication auth) {
		String userId = auth.getName();
		return BaseResponse.success(vmsStreamService.createStream(cameraId, userId, request));
	}

	@GetMapping("/stream/{sessionToken}/master.m3u8")
	public ResponseEntity<byte[]> getMasterPlaylist(@PathVariable String sessionToken,
			@RequestParam(required = false) String pos) {
		byte[] playlist = hlsProxyService.fetchMasterPlaylist(sessionToken, pos);
		return ResponseEntity.ok().contentType(MediaType.valueOf("application/vnd.apple.mpegurl")).body(playlist);
	}

	@GetMapping("/stream/{sessionToken}/trickplay")
	public ResponseEntity<byte[]> getTrickplay(@PathVariable String sessionToken, @RequestParam int speed) {
		byte[] playlist = hlsProxyService.fetchTrickplay(sessionToken, speed);
		return ResponseEntity.ok().contentType(MediaType.valueOf("application/vnd.apple.mpegurl")).body(playlist);
	}

	@GetMapping("/stream/{sessionToken}/**")
	public ResponseEntity<byte[]> getSegment(@PathVariable String sessionToken, HttpServletRequest request) {
		String path = request.getRequestURI();
		String prefix = "/v1/auth/vms/stream/" + sessionToken;
		String relativePath = path.substring(prefix.length());
		byte[] data = hlsProxyService.fetchSegment(sessionToken, relativePath);
		return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM).body(data);
	}

	@DeleteMapping("/stream/{sessionToken}")
	public BaseResponse<Void> stopStream(@PathVariable String sessionToken) {
		// Session auto-expires; explicit removal allowed
		return BaseResponse.success(null);
	}

}
