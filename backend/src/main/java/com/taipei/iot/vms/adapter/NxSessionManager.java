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

/**
 * Nx Witness REST API v1 session 管理器。
 *
 * <p>
 * 管理每個 VmsServer 的 session token：login → cache → auto-refresh。 內部使用 JDK
 * {@link HttpClient} 向 {@code POST /rest/v1/login/sessions} 取得 token，以完整掌控 SSL 與 request
 * body 序列化。 token 到期前 60 秒自動 refresh，避免邊界情況。
 * </p>
 */
@Slf4j
@Component
public class NxSessionManager {

	static final String LOGIN_PATH = "/rest/v1/login/sessions";

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private final RestClient.Builder restClientBuilder;

	private final Map<Long, SessionCache> tokenCache = new ConcurrentHashMap<>();

	public NxSessionManager() {
		this.restClientBuilder = RestClient.builder();
	}

	/**
	 * 供測試注入 mock {@link RestClient.Builder}。
	 */
	NxSessionManager(RestClient.Builder restClientBuilder) {
		this.restClientBuilder = restClientBuilder;
	}

	/**
	 * 為指定 server 取得有效的 session token。 若 cache 中 token 仍有效（expiresInS > 60s），直接回傳； 否則重新
	 * login 並更新 cache。
	 */
	public String getToken(VmsServer server) {
		SessionCache cached = tokenCache.get(server.getId());
		if (cached != null && cached.isValid()) {
			return cached.token;
		}
		return login(server);
	}

	/**
	 * 清除指定 server 的 cached token（例如 server 密碼更新後）。
	 */
	public void invalidate(Long serverId) {
		tokenCache.remove(serverId);
	}

	private String login(VmsServer server) {
		try {
			// 使用 JDK HttpClient 直接發送，完整掌控 SSL（自簽憑證）與 JSON body
			var sslParams = new SSLParameters();
			sslParams.setEndpointIdentificationAlgorithm(null); // 關閉 hostname 驗證
			var httpClient = HttpClient.newBuilder()
				.sslContext(VmsSslUtil.sslContext())
				.sslParameters(sslParams)
				.build();

			String bodyJson = MAPPER
				.writeValueAsString(Map.of("username", server.getAuthUsername(), "password", server.getAuthPassword()));

			String baseUrl = server.getBaseUrl().replaceAll("/+$", ""); // 移除尾部 slash
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

			// 安全邊界：到期前 60 秒就 refresh
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

	/**
	 * cache entry，含有效期限判斷。
	 */
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

	// ── 內部 DTO ──

	private record LoginResponse(String id, String username, String token, int ageS, int expiresInS) {

	}

}

/**
 * Nx Witness REST API v1 session 管理器。
 *
 * <p>
 * 管理每個 VmsServer 的 session token：login → cache → auto-refresh。 內部使用 RestClient 向
 * {@code POST /rest/v1/login/sessions} 取得 token。 token 到期前 60 秒自動 refresh，避免邊界情況。
 * </p>
 */
@Slf4j
@Component
public class NxSessionManager {

	static final String LOGIN_PATH = "/rest/v1/login/sessions";

	private final RestClient.Builder restClientBuilder;

	private final Map<Long, SessionCache> tokenCache = new ConcurrentHashMap<>();

	public NxSessionManager() {
		this.restClientBuilder = RestClient.builder();
	}

	/**
	 * 供測試注入 mock {@link RestClient.Builder}。
	 */
	NxSessionManager(RestClient.Builder restClientBuilder) {
		this.restClientBuilder = restClientBuilder;
	}

	/**
	 * 為指定 server 取得有效的 session token。 若 cache 中 token 仍有效（expiresInS > 60s），直接回傳； 否則重新
	 * login 並更新 cache。
	 */
	public String getToken(VmsServer server) {
		SessionCache cached = tokenCache.get(server.getId());
		if (cached != null && cached.isValid()) {
			return cached.token;
		}
		return login(server);
	}

	/**
	 * 清除指定 server 的 cached token（例如 server 密碼更新後）。
	 */
	public void invalidate(Long serverId) {
		tokenCache.remove(serverId);
	}

	private String login(VmsServer server) {
		var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();

		RestClient client = restClientBuilder.baseUrl(server.getBaseUrl()).requestFactory(factory).build();

		var body = new LinkedHashMap<String, String>();
		body.put("username", server.getAuthUsername());
		body.put("password", server.getAuthPassword());

		var response = client.post()
			.uri(LOGIN_PATH)
			.contentType(MediaType.APPLICATION_JSON)
			.body(body)
			.retrieve()
			.body(LoginResponse.class);

		if (response == null || response.token() == null) {
			throw new BusinessException(ErrorCode.VMS_CONNECTION_FAILED, "Nx Witness 登入失敗: " + server.getBaseUrl());
		}

		log.info("Nx Witness session 已建立: serverId={}, expiresInS={}", server.getId(), response.expiresInS());

		// 安全邊界：到期前 60 秒就 refresh
		int safeTtl = Math.max(response.expiresInS() - 60, 60);
		tokenCache.put(server.getId(), new SessionCache(response.token(), safeTtl));
		return response.token();
	}

	/**
	 * cache entry，含有效期限判斷。
	 */
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

	// ── 內部 DTO ──

	private record LoginResponse(String id, String username, String token, int ageS, int expiresInS) {

	}

}
