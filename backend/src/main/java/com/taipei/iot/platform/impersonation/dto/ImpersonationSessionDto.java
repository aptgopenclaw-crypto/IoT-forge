package com.taipei.iot.platform.impersonation.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ImpersonationSessionDto {

	private String id;

	private String operatorUserId;

	private String targetTenantId;

	private String targetTenantName;

	private String reason;

	private String status;

	private LocalDateTime startedAt;

	private LocalDateTime expiresAt;

	private LocalDateTime revokedAt;

}
