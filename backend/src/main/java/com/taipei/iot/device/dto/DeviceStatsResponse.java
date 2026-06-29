package com.taipei.iot.device.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "設備統計摘要")
public class DeviceStatsResponse {

	@Schema(description = "設備總數")
	private long totalDevices;

	@Schema(description = "依類型分類統計")
	private Map<String, Long> byType;

	@Schema(description = "依狀態分類統計")
	private Map<String, Long> byStatus;

	@Schema(description = "在線率（百分比）")
	private double onlineRate;

	@Schema(description = "未結障礙工單數")
	private long openFaults;

}
