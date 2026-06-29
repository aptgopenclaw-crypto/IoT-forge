package com.taipei.iot.device.dto;

import com.taipei.iot.device.enums.ConnectivityType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "新增/編輯設備請求")
public class DeviceRequest {

	@NotNull(message = "設備類型為必填")
	@Schema(description = "設備類型；對應 DeviceTemplate 定義的類型", example = "STREET_LIGHT")
	private String deviceType;

	@NotBlank(message = "設備代碼為必填")
	@Schema(description = "設備代碼（租戶內唯一）", example = "SL-001")
	private String deviceCode;

	@Schema(description = "設備名稱", example = "忠孝東路一段1號燈")
	private String deviceName;

	// 坐標
	@Schema(description = "TWD97 X 坐標")
	private BigDecimal twd97X;

	@Schema(description = "TWD97 Y 坐標")
	private BigDecimal twd97Y;

	@Schema(description = "經度 (WGS84)")
	private BigDecimal lng;

	@Schema(description = "緯度 (WGS84)")
	private BigDecimal lat;

	@Schema(description = "海拔高度（公尺）")
	private BigDecimal elevation;

	@Schema(description = "TWD67 X 坐標")
	private BigDecimal twd67X;

	@Schema(description = "TWD67 Y 坐標")
	private BigDecimal twd67Y;

	@Schema(description = "台電座標格式")
	private String taipowerCoord;

	// 組織歸屬
	@Schema(description = "所屬部門 ID")
	private Long deptId;

	@Schema(description = "標案契約 ID")
	private Long contractId;

	@Schema(description = "財產所有人")
	private String propertyOwner;

	@Schema(description = "安裝日期")
	private LocalDate installedAt;

	// 連線拓撲
	@Schema(description = "父設備 ID（設備組合階層）")
	private Long parentDeviceId;

	@Schema(description = "掛載位置（如：燈桿頂端、控制箱內部）")
	private String mountPosition;

	@Schema(description = "連線方式")
	private ConnectivityType connectivityType;

	@Schema(description = "網路連線設定（JSONB）")
	private Map<String, Object> networkConfig;

	@Schema(description = "所屬電力迴路 ID")
	private Long circuitId;

	@Schema(description = "設備自訂屬性；需符合 DeviceTemplate.schema 定義的 JSON Schema")
	private Map<String, Object> attributes;

}
