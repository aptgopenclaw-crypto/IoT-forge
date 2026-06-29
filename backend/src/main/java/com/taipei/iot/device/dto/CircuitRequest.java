package com.taipei.iot.device.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "電力迴路請求")
public class CircuitRequest {

	@Schema(description = "配電盤設備 ID")
	private Long panelBoxDeviceId;

	@NotBlank(message = "迴路編號為必填")
	@Schema(description = "迴路編號", example = "CKT-001")
	private String circuitNumber;

	@Schema(description = "迴路名稱", example = "忠孝東路一段1號迴路")
	private String circuitName;

	@Schema(description = "台電電號")
	private String taipowerAccount;

	@Schema(description = "用電類型", example = "路燈用電")
	private String usageType;

}
