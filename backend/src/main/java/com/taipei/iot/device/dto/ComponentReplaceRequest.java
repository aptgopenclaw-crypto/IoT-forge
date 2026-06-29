package com.taipei.iot.device.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
@Schema(description = "更換元件請求")
public class ComponentReplaceRequest {

	@NotNull(message = "舊元件設備 ID 為必填")
	@Schema(description = "舊元件設備 ID")
	private Long oldDeviceId;

	@NotNull(message = "新設備資料為必填")
	@Schema(description = "新設備資料")
	private DeviceRequest newDevice;

	@NotBlank(message = "更換原因為必填")
	@Schema(description = "更換原因", example = "燈泡壽命到期")
	private String reason;

}
