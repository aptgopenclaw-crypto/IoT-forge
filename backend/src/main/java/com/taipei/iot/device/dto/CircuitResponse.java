package com.taipei.iot.device.dto;

import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "電力迴路回應")
public class CircuitResponse {

	@Schema(description = "迴路 ID")
	private Long id;

	@Schema(description = "配電盤設備 ID")
	private Long panelBoxDeviceId;

	@Schema(description = "迴路編號")
	private String circuitNumber;

	@Schema(description = "迴路名稱")
	private String circuitName;

	@Schema(description = "台電電號")
	private String taipowerAccount;

	@Schema(description = "用電類型")
	private String usageType;

	@Schema(description = "狀態")
	private String status;

	@Schema(description = "建立時間")
	private LocalDateTime createdAt;

}
