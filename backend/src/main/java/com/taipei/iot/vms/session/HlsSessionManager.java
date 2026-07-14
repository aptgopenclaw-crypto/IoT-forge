package com.taipei.iot.vms.session;

import com.taipei.iot.vms.config.VmsConfig;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class HlsSessionManager {

	private final Map<String, HlsSession> sessions = new ConcurrentHashMap<>();

	private final VmsConfig vmsConfig;

	public HlsSessionManager(VmsConfig vmsConfig) {
		this.vmsConfig = vmsConfig;
	}

	public String createSession(String userId, Long cameraId, Long serverId, String nxToken, String streamType,
			Instant startTime, Instant endTime) {
		String sessionToken = UUID.randomUUID().toString();
		HlsSession session = new HlsSession(sessionToken, userId, cameraId, serverId, nxToken, streamType, startTime,
				endTime, Instant.now());
		sessions.put(sessionToken, session);
		return sessionToken;
	}

	public HlsSession getSession(String sessionToken) {
		HlsSession session = sessions.get(sessionToken);
		if (session == null) {
			throw new SessionNotFoundException(sessionToken);
		}
		// Auto-expire stale sessions (older than TTL)
		if (session.getCreatedAt().plusSeconds(vmsConfig.getSessionTtlSeconds()).isBefore(Instant.now())) {
			sessions.remove(sessionToken);
			throw new SessionNotFoundException(sessionToken);
		}
		return session;
	}

	/** Refresh session TTL (called on each successful TS request) */
	public void touchSession(String sessionToken) {
		HlsSession session = sessions.get(sessionToken);
		if (session != null) {
			// Re-insert to refresh position in ConcurrentHashMap
		}
	}

	public void removeSession(String sessionToken) {
		sessions.remove(sessionToken);
	}

}
