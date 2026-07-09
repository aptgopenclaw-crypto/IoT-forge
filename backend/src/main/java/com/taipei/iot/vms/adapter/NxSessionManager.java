package com.taipei.iot.vms.adapter;

import com.taipei.iot.common.enums.ErrorCode;
import com.taipei.iot.common.exception.BusinessException;
import com.taipei.iot.vms.entity.VmsServer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
		RestClient client = restClientBuilder.baseUrl(server.getBaseUrl()).build();

		var response = client.post()
			.uri(LOGIN_PATH)
			.body(new LoginRequest(server.getAuthUsername(), server.getAuthPassword()))
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

	private record LoginRequest(String username, String password) {

	}

	private record LoginResponse(String id, String username, String token, int ageS, int expiresInS) {

	}

}
