package com.taipei.iot.schema.dto;

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
public class DeviceTemplateResponse {

	private String deviceType;

	private Integer version;

	private String createdBy;

	private LocalDateTime createdAt;

	private LocalDateTime updatedAt;

}
