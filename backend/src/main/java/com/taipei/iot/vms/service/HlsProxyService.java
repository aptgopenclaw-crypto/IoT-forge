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
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

@Service
@RequiredArgsConstructor
@Slf4j
public class HlsProxyService {

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
		long t0 = System.currentTimeMillis();
		try {
			log.info("NX proxy → url={} token-present={}", url, (nxToken != null && !nxToken.isBlank()));

			ProcessBuilder pb = new ProcessBuilder("curl", "-sk", // silent, accept
																	// self-signed certs
					"--tlsv1.2", // force TLS 1.2 (NX interop)
					"--max-time", "15", // 15s timeout
					"-H", "x-runtime-guid: " + nxToken, "-H", "User-Agent: IoT-Forge-VMS/1.0", url);
			pb.redirectErrorStream(true);

			Process process = pb.start();
			byte[] body;
			try (InputStream in = process.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
				byte[] buf = new byte[8192];
				int n;
				while ((n = in.read(buf)) != -1) {
					out.write(buf, 0, n);
				}
				body = out.toByteArray();
			}

			int exitCode = process.waitFor();
			log.info("NX proxy ← exit={} elapsed={}ms url={}", exitCode, System.currentTimeMillis() - t0, url);

			if (exitCode != 0 || body.length == 0) {
				log.warn("NX returned empty response (curl exit {}) for {}", exitCode, url);
				return body;
			}

			// Rewrite m3u8 URLs if it's a playlist
			String firstLine = new String(body, 0, Math.min(200, body.length), StandardCharsets.UTF_8).trim();
			if (firstLine.startsWith("#EXTM3U") || url.endsWith(".m3u")) {
				String content = new String(body, StandardCharsets.UTF_8);
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
			log.error("NX proxy request failed: {} elapsed={}ms", url, System.currentTimeMillis() - t0, e);
			throw new BusinessException(ErrorCode.VMS_CONNECTION_FAILED, "NX proxy request failed: " + e.getMessage());
		}
	}

}
