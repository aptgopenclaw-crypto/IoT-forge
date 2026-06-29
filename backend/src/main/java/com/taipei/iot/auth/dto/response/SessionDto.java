package com.taipei.iot.auth.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * [v2 N-7] 使用者「登入裝置」清單回傳結構。 sessionId 對應 refresh-token 的 jti，可在 DELETE
 * /v1/auth/sessions/{id} 帶入以強制登出。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionDto {

	private String sessionId;

	private String tenantId;

	private String ipAddress;

	private String userAgent;

	private LocalDateTime issuedAt;

	private LocalDateTime lastSeenAt;

	private LocalDateTime expiresAt;

	/** 是否為當前請求所使用的 session（依 refresh_token cookie jti 判定）。 */
	private boolean current;

}
