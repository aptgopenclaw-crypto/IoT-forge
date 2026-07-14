package com.taipei.iot.vms.service;

import com.taipei.iot.common.enums.ErrorCode;
import com.taipei.iot.common.exception.BusinessException;
import com.taipei.iot.vms.entity.VmsCameraMappingEntity;
import com.taipei.iot.vms.entity.VmsServerEntity;
import com.taipei.iot.vms.repository.VmsCameraMappingRepository;
import com.taipei.iot.vms.repository.VmsServerRepository;
import com.taipei.iot.vms.session.HlsSession;
import com.taipei.iot.vms.session.HlsSessionManager;
import com.taipei.iot.vms.token.NxTokenManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

@Service
@RequiredArgsConstructor
@Slf4j
public class HlsProxyService {

	private final RestTemplate restTemplate = new RestTemplate();

	private final HlsSessionManager sessionManager;

	private final NxTokenManager nxTokenManager;

	private final VmsCameraMappingRepository cameraRepository;

	private final VmsServerRepository serverRepository;

	public byte[] fetchMasterPlaylist(String sessionToken, String pos) {
		HlsSession session = sessionManager.getSession(sessionToken);
		VmsCameraMappingEntity camera = cameraRepository.findById(session.getCameraId())
			.orElseThrow(() -> new BusinessException(ErrorCode.VMS_CAMERA_NOT_FOUND));
		VmsServerEntity server = serverRepository.findById(session.getServerId())
			.orElseThrow(() -> new BusinessException(ErrorCode.VMS_SERVER_NOT_FOUND));

		StringBuilder url = new StringBuilder(server.getBaseUrl()).append("/hls/")
			.append(camera.getVmsCameraId())
			.append(".m3u");

		boolean isLive = "LIVE".equalsIgnoreCase(session.getStreamType());
		url.append("?lo=true");
		if (!isLive && pos != null) {
			url.append("&pos=").append(pos);
		}
		if (!isLive && pos == null && session.getStartTime() != null) {
			url.append("&pos=").append(session.getStartTime().toEpochMilli());
		}

		String nxToken = nxTokenManager.getToken(server.getId());
		return proxyRequest(url.toString(), nxToken, sessionToken);
	}

	public byte[] fetchSegment(String sessionToken, String path) {
		HlsSession session = sessionManager.getSession(sessionToken);
		sessionManager.touchSession(sessionToken);

		// Security: validate path
		String decodedPath = URLDecoder.decode(path, StandardCharsets.UTF_8);
		if (decodedPath.contains("..") || (!decodedPath.startsWith("/hls/") && !decodedPath.contains(".ts")
				&& !decodedPath.contains(".m3u8"))) {
			throw new BusinessException(ErrorCode.VMS_STREAM_TOKEN_INVALID, "Invalid segment path");
		}

		VmsServerEntity server = serverRepository.findById(session.getServerId())
			.orElseThrow(() -> new BusinessException(ErrorCode.VMS_SERVER_NOT_FOUND));

		String url = server.getBaseUrl() + path;
		String nxToken = nxTokenManager.getToken(server.getId());
		return proxyRequest(url, nxToken, sessionToken);
	}

	public byte[] fetchTrickplay(String sessionToken, int speed) {
		HlsSession session = sessionManager.getSession(sessionToken);
		VmsCameraMappingEntity camera = cameraRepository.findById(session.getCameraId())
			.orElseThrow(() -> new BusinessException(ErrorCode.VMS_CAMERA_NOT_FOUND));
		VmsServerEntity server = serverRepository.findById(session.getServerId())
			.orElseThrow(() -> new BusinessException(ErrorCode.VMS_SERVER_NOT_FOUND));

		long posMs = session.getStartTime() != null ? session.getStartTime().toEpochMilli()
				: System.currentTimeMillis();

		String url = server.getBaseUrl() + "/hls/" + camera.getVmsCameraId() + ".m3u?lo=true&pos=" + posMs + "&speed="
				+ speed;

		String nxToken = nxTokenManager.getToken(server.getId());
		return proxyRequest(url, nxToken, sessionToken);
	}

	private byte[] proxyRequest(String url, String nxToken, String sessionToken) {
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(nxToken);
		headers.set("x-runtime-guid", nxToken);
		headers.set("User-Agent", "IoT-Forge-VMS/1.0");

		try {
			ResponseEntity<byte[]> response = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers),
					byte[].class);

			byte[] body = response.getBody();
			if (body == null) {
				throw new BusinessException(ErrorCode.VMS_STREAM_NOT_AVAILABLE);
			}

			// Rewrite m3u8 URLs if it's a playlist
			String contentType = response.getHeaders().getContentType() != null
					? response.getHeaders().getContentType().toString() : "";
			if (contentType.contains("mpegurl") || contentType.contains("m3u8") || url.endsWith(".m3u")) {
				String content = new String(body, StandardCharsets.UTF_8);
				// Rewrite /hls/ paths to proxy paths with sessionToken
				content = content.replaceAll("(?m)^(?!https?://|#)(/hls/[^\\s?#]+)",
						"/v1/auth/vms/stream/" + sessionToken + "$1");
				body = content.getBytes(StandardCharsets.UTF_8);
			}
			return body;

		}
		catch (BusinessException e) {
			throw e;
		}
		catch (Exception e) {
			log.error("NX proxy request failed: {} - {}", url, e.getMessage());
			throw new BusinessException(ErrorCode.VMS_CONNECTION_FAILED, "NX proxy request failed: " + e.getMessage());
		}
	}

}
