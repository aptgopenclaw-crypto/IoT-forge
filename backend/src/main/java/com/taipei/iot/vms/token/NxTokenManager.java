package com.taipei.iot.vms.token;

import com.taipei.iot.auth.provider.crypto.AuthConfigEncryptor;
import com.taipei.iot.common.enums.ErrorCode;
import com.taipei.iot.common.exception.BusinessException;
import com.taipei.iot.vms.entity.VmsServerEntity;
import com.taipei.iot.vms.exception.NxTokenNotAvailableException;
import com.taipei.iot.vms.repository.VmsServerRepository;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

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

	private final RestTemplate restTemplate = new RestTemplate();

	private final VmsServerRepository vmsServerRepository;

	private final AuthConfigEncryptor encryptor;

	private final TaskScheduler taskScheduler;

	public NxTokenManager(VmsServerRepository vmsServerRepository, AuthConfigEncryptor encryptor,
			TaskScheduler taskScheduler) {
		this.vmsServerRepository = vmsServerRepository;
		this.encryptor = encryptor;
		this.taskScheduler = taskScheduler;
	}

	@PostConstruct
	public void init() {
		for (VmsServerEntity server : vmsServerRepository.findByIsActiveTrue()) {
			try {
				refreshToken(server);
			}
			catch (Exception e) {
				log.warn("Initial NX login failed for server [{}]: {}", server.getId(), e.getMessage());
			}
		}
	}

	public synchronized void refreshToken(Long serverId) {
		VmsServerEntity server = vmsServerRepository.findById(serverId)
			.orElseThrow(() -> new BusinessException(ErrorCode.VMS_SERVER_NOT_FOUND));
		refreshToken(server);
	}

	private void refreshToken(VmsServerEntity server) {
		String password = encryptor.decrypt(server.getAuthPassword());
		String url = server.getBaseUrl() + "/rest/v1/login/sessions";

		var body = Map.of("username", server.getAuthUsername(), "password",
				password != null ? password : server.getAuthPassword());
		var headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);

		try {
			var response = restTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);

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
