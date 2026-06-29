package com.taipei.iot.dispatch.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
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
@Schema(description = "新增/編輯工單請求")
public class WorkOrderRequest {

	@Schema(description = "關聯設備 ID（與 deviceCode 二選一）")
	private Long deviceId;

	@Schema(description = "設備代碼（與 deviceId 二選一，優先於 deviceId）")
	private String deviceCode;

	@Schema(description = "關聯電力迴路 ID")
	private Long circuitId;

	@NotBlank(message = "工單類型為必填")
	@Schema(description = "工單類型", example = "REPAIR")
	private String orderType;

	@NotBlank(message = "通報來源為必填")
	@Schema(description = "通報來源", example = "CITIZEN")
	private String sourceType;

	@Schema(description = "優先級", example = "HIGH")
	private String priority;

	@Schema(description = "通報人姓名")
	private String reporterName;

	@Schema(description = "通報人聯絡方式")
	private String reporterContact;

	@Schema(description = "問題描述")
	private String description;

	@Schema(description = "地點快照（工單建立時凍結設備位置）")
	private Map<String, Object> locationSnapshot;

}
