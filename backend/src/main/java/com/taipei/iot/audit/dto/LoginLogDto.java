package com.taipei.iot.audit.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginLogDto {

	private Long id;

	private String userId;

	private String tenantId;

	private String email;

	private String displayName;

	private String eventType;

	private String detail;

	private String ipAddress;

	private String userAgent;

	private Long deptId;

	private LocalDateTime createTime;

}
