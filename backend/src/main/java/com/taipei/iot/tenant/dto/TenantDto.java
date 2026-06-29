package com.taipei.iot.tenant.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class TenantDto {

	private String tenantId;

	private String tenantCode;

	private String tenantName;

	private String deploymentMode;

	private Boolean enabled;

	private LocalDateTime createTime;

}
