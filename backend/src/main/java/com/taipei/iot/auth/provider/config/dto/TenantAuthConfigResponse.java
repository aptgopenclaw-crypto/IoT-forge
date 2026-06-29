package com.taipei.iot.auth.provider.config.dto;

import com.taipei.iot.auth.provider.AuthType;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Map;

@Getter
@Setter
@Builder
public class TenantAuthConfigResponse {

	private Long id;

	private String tenantId;

	private AuthType authType;

	private Boolean enabled;

	/** Sanitized config (passwords/secrets replaced with "***") */
	private Map<String, Object> config;

	private Boolean fallbackLocal;

	private LocalDateTime createdAt;

	private LocalDateTime updatedAt;

}
