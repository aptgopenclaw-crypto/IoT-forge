package com.taipei.iot.import_.device;

import com.taipei.iot.device.enums.ConnectivityType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceImportRow {

	private String deviceType;

	private String deviceCode;

	private String deviceName;

	private BigDecimal twd97X;

	private BigDecimal twd97Y;

	private BigDecimal lng;

	private BigDecimal lat;

	private BigDecimal elevation;

	private String deptName;

	private String contractName;

	private String propertyOwner;

	private LocalDate installedAt;

	private String parentDeviceCode;

	private String mountPosition;

	private ConnectivityType connectivityType;

	private String circuitNumber;

	// raw 原始值（validate 階段用於回報格式錯誤）
	private String rawInstalledAt;

	private String rawConnectivityType;

}
