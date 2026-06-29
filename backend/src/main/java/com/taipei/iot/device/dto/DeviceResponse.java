package com.taipei.iot.device.dto;

import com.taipei.iot.device.enums.ConnectivityType;
import com.taipei.iot.device.enums.DeviceStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "設備回應")
public class DeviceResponse {

	@Schema(description = "設備 ID", example = "1")
	private Long id;

	@Schema(description = "設備類型", example = "STREET_LIGHT")
	private String deviceType;

	@Schema(description = "設備代碼", example = "SL-001")
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

	@Schema(description = "所屬部門名稱")
	private String deptName;

	@Schema(description = "標案契約 ID")
	private Long contractId;

	@Schema(description = "標案契約編號")
	private String contractCode;

	@Schema(description = "財產所有人")
	private String propertyOwner;

	// 狀態
	@Schema(description = "設備狀態")
	private DeviceStatus status;

	@Schema(description = "安裝日期")
	private LocalDate installedAt;

	@Schema(description = "除役日期")
	private LocalDate decommissionedAt;

	// 連線拓撲
	@Schema(description = "父設備 ID")
	private Long parentDeviceId;

	@Schema(description = "父設備代碼")
	private String parentDeviceCode;

	@Schema(description = "掛載位置")
	private String mountPosition;

	@Schema(description = "連線方式")
	private ConnectivityType connectivityType;

	@Schema(description = "網路設定")
	private Map<String, Object> networkConfig;

	@Schema(description = "最後心跳時間")
	private LocalDateTime lastHeartbeatAt;

	@Schema(description = "所屬電力迴路 ID")
	private Long circuitId;

	@Schema(description = "迴路編號")
	private String circuitNumber;

	@Schema(description = "設備自訂屬性")
	private Map<String, Object> attributes;

	@Schema(description = "子設備數量（組合元件）")
	private long childrenCount;

	@Schema(description = "組合元件清單（僅單一設備明細時填入）")
	private List<DeviceResponse> children;

	@Schema(description = "建立者")
	private String createdBy;

	@Schema(description = "建立時間")
	private LocalDateTime createdAt;

	@Schema(description = "更新時間")
	private LocalDateTime updatedAt;

}
