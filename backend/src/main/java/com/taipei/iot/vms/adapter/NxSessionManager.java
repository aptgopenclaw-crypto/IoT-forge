package com.taipei.iot.vms.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taipei.iot.common.enums.ErrorCode;
import com.taipei.iot.common.exception.BusinessException;
import com.taipei.iot.vms.entity.VmsServer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import javax.net.ssl.SSLParameters;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class NxSessionManager {

	static final String LOGIN_PATH = "/rest/v1/login/sessions";

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private final HttpClient httpClient;

	private final Map<Long, SessionCache> tokenCache = new ConcurrentHashMap<>();

	public NxSessionManager() {
		var sslParams = new SSLParameters();
		sslParams.setEndpointIdentificationAlgorithm(null);
		this.httpClient = HttpClient.newBuilder().sslContext(VmsSslUtil.sslContext()).sslParameters(sslParams).build();
	}

	/** 供測試注入 mock {@link HttpClient}。 */
	NxSessionManager(HttpClient httpClient) {
		this.httpClient = httpClient;
	}

	public String getToken(VmsServer server) {
		SessionCache cached = tokenCache.get(server.getId());
		if (cached != null && cached.isValid()) {
			return cached.token;
		}
		return login(server);
	}

	public void invalidate(Long serverId) {
		tokenCache.remove(serverId);
	}

	private String login(VmsServer server) {
		try {
			String bodyJson = MAPPER
				.writeValueAsString(Map.of("username", server.getAuthUsername(), "password", server.getAuthPassword()));

			String baseUrl = server.getBaseUrl().replaceAll("/+$", "");
			var request = HttpRequest.newBuilder()
				.uri(URI.create(baseUrl + LOGIN_PATH))
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(bodyJson))
				.build();

			var httpResponse = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
			if (httpResponse.statusCode() != 200) {
				throw new BusinessException(ErrorCode.VMS_CONNECTION_FAILED,
						"Nx Witness 登入失敗 [" + httpResponse.statusCode() + "]: " + httpResponse.body());
			}

			var loginResp = MAPPER.readValue(httpResponse.body(), LoginResponse.class);
			if (loginResp.token() == null) {
				throw new BusinessException(ErrorCode.VMS_CONNECTION_FAILED, "Nx Witness 登入失敗：回應無 token");
			}

			log.info("Nx Witness session 已建立: serverId={}, expiresInS={}", server.getId(), loginResp.expiresInS());

			int safeTtl = Math.max(loginResp.expiresInS() - 60, 60);
			tokenCache.put(server.getId(), new SessionCache(loginResp.token(), safeTtl));
			return loginResp.token();
		}
		catch (BusinessException ex) {
			throw ex;
		}
		catch (IOException | InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new BusinessException(ErrorCode.VMS_CONNECTION_FAILED,
					"Nx Witness 連線失敗: " + server.getBaseUrl() + " — " + ex.getMessage());
		}
	}

	static class SessionCache {

		final String token;

		final Instant expiresAt;

		SessionCache(String token, int ttlSeconds) {
			this.token = token;
			this.expiresAt = Instant.now().plusSeconds(ttlSeconds);
		}

		boolean isValid() {
			return Instant.now().isBefore(expiresAt);
		}

	}

	private record LoginResponse(String id, String username, String token, int ageS, int expiresInS) {

	}

}
