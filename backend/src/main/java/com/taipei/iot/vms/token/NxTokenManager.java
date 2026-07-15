package com.taipei.iot.vms.token;

import com.taipei.iot.auth.provider.crypto.AuthConfigEncryptor;
import com.taipei.iot.common.enums.ErrorCode;
import com.taipei.iot.common.exception.BusinessException;
import com.taipei.iot.vms.entity.VmsServerEntity;
import com.taipei.iot.vms.exception.NxTokenNotAvailableException;
import com.taipei.iot.vms.repository.VmsServerRepository;
import com.taipei.iot.common.context.TenantContext;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

@Component
@Slf4j
public class NxTokenManager {

	private record TokenInfo(String token, Instant expiresAt, ScheduledFuture<?> refreshTask) {
	}

	private final Map<Long, TokenInfo> tokens = new ConcurrentHashMap<>();

	private final RestTemplate restTemplate;

	private final VmsServerRepository vmsServerRepository;

	private final AuthConfigEncryptor encryptor;

	private final TaskScheduler taskScheduler;

	public NxTokenManager(VmsServerRepository vmsServerRepository, AuthConfigEncryptor encryptor,
			TaskScheduler taskScheduler) {
		this.vmsServerRepository = vmsServerRepository;
		this.encryptor = encryptor;
		this.taskScheduler = taskScheduler;
		this.restTemplate = createLenientRestTemplate();
	}

	/**
	 * Create a RestTemplate that accepts all SSL certificates (self-signed, expired,
	 * etc.). Suitable for development environments where the NX server uses a self-signed
	 * certificate. The SSL leniency is scoped to this RestTemplate instance only, not the
	 * entire JVM.
	 */
	private static RestTemplate createLenientRestTemplate() {
		try {
			SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
			TrustManager[] trustAll = new TrustManager[] { new X509TrustManager() {
				@Override
				public X509Certificate[] getAcceptedIssuers() {
					return new X509Certificate[0];
				}

				@Override
				public void checkClientTrusted(X509Certificate[] chain, String authType) {
				}

				@Override
				public void checkServerTrusted(X509Certificate[] chain, String authType) {
				}
			} };
			sslContext.init(null, trustAll, new SecureRandom());

			SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory() {
				@Override
				protected void prepareConnection(HttpURLConnection connection, String httpMethod) throws IOException {
					if (connection instanceof HttpsURLConnection httpsConn) {
						httpsConn.setSSLSocketFactory(sslContext.getSocketFactory());
						httpsConn.setHostnameVerifier((hostname, session) -> true);
					}
					super.prepareConnection(connection, httpMethod);
				}
			};
			factory.setConnectTimeout(5_000);
			factory.setReadTimeout(10_000);
			factory.setBufferRequestBody(false);

			return new RestTemplate(factory);
		}
		catch (Exception e) {
			throw new RuntimeException("Failed to create lenient RestTemplate", e);
		}
	}

	@PostConstruct
	public void init() {
		TenantContext.runInSystemContext(() -> {
			for (VmsServerEntity server : vmsServerRepository.findByIsActiveTrue()) {
				try {
					refreshToken(server);
				}
				catch (Exception e) {
					log.warn("Initial NX login failed for server [{}]: {}", server.getId(), e.getMessage());
				}
			}
		});
	}

	public synchronized void refreshToken(Long serverId) {
		VmsServerEntity server = vmsServerRepository.findById(serverId)
			.orElseThrow(() -> new BusinessException(ErrorCode.VMS_SERVER_NOT_FOUND));
		refreshToken(server);
	}

	private void refreshToken(VmsServerEntity server) {
		String password = encryptor.decrypt(server.getAuthPassword());
		String url = server.getBaseUrl() + "/rest/v1/login/sessions";

		String effectivePassword = password != null ? password : server.getAuthPassword();
		String jsonBody = "{" + "\"username\":\"" + escapeJson(server.getAuthUsername()) + "\"," + "\"password\":\""
				+ escapeJson(effectivePassword) + "\"" + "}";
		var headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);

		try {
			var response = restTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(jsonBody, headers), Map.class);

			var responseBody = response.getBody();
			if (responseBody == null || responseBody.get("token") == null) {
				throw new NxTokenNotAvailableException("Empty token response from NX server " + server.getId());
			}

			String token = (String) responseBody.get("token");
			int expiresInS = responseBody.get("expiresInS") instanceof Number n ? n.intValue() : 3600;
			Instant expiresAt = Instant.now().plusSeconds(expiresInS);

			// Cancel existing refresh task
			TokenInfo old = tokens.get(server.getId());
			if (old != null && old.refreshTask() != null) {
				old.refreshTask().cancel(false);
			}

			// Schedule next refresh at 90% of expiry
			long refreshDelayMs = (long) (expiresInS * 0.9 * 1000);
			ScheduledFuture<?> refreshTask = taskScheduler.schedule(() -> refreshToken(server.getId()),
					Instant.now().plusMillis(refreshDelayMs));

			tokens.put(server.getId(), new TokenInfo(token, expiresAt, refreshTask));
			log.info("NX token acquired for server [{}] (ID: {}), expires in {}s", server.getName(), server.getId(),
					expiresInS);

		}
		catch (Exception e) {
			log.error("Failed to acquire NX token for server [{}]: {}", server.getId(), e.getMessage());
			// Keep old token if exists — don't invalidate on transient failure
			if (!tokens.containsKey(server.getId())) {
				throw new NxTokenNotAvailableException("Initial NX login failed for server " + server.getId(), e);
			}
		}
	}

	/**
	 * Escape a string for safe inclusion in a JSON string value.
	 */
	private static String escapeJson(String value) {
		if (value == null)
			return "";
		return value.replace("\\", "\\\\")
			.replace("\"", "\\\"")
			.replace("\n", "\\n")
			.replace("\r", "\\r")
			.replace("\t", "\\t");
	}

	public String getToken(Long serverId) {
		TokenInfo info = tokens.get(serverId);
		if (info == null || info.token() == null) {
			throw new BusinessException(ErrorCode.VMS_CONNECTION_FAILED,
					"NX token not available for server " + serverId);
		}
		return info.token();
	}

	public void invalidateToken(Long serverId) {
		TokenInfo old = tokens.remove(serverId);
		if (old != null && old.refreshTask() != null) {
			old.refreshTask().cancel(false);
		}
	}

}
