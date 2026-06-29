package com.taipei.iot.platform.impersonation.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Response for {@code POST /v1/platform/impersonations}: returns the new impersonation
 * session metadata plus a short-lived IMPERSONATION-scope JWT.
 */
@Data
@Builder
public class ImpersonationTokenResponse {

	private String accessToken;

	private String sessionId;

	private String targetTenantId;

	private LocalDateTime expiresAt;

	/** Always "IMPERSONATION" (see {@link com.taipei.iot.auth.security.TokenScope}). */
	private String scope;

}
