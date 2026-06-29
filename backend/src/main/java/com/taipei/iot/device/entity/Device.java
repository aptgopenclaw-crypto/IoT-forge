package com.taipei.iot.device.entity;

import com.taipei.iot.device.enums.ConnectivityType;
import com.taipei.iot.device.enums.DeviceStatus;
import com.taipei.iot.tenant.TenantAware;
import com.taipei.iot.tenant.TenantEntityListener;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

@Entity
@Table(name = "devices")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@EntityListeners({ TenantEntityListener.class, AuditingEntityListener.class })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Device implements TenantAware {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "tenant_id", nullable = false, length = 50)
	private String tenantId;

	@Column(name = "device_type", nullable = false, length = 30)
	private String deviceType;

	@Column(name = "device_code", nullable = false, length = 100)
	private String deviceCode;

	@Column(name = "device_name", length = 200)
	private String deviceName;

	// 坐標
	@Column(name = "twd97_x", precision = 12, scale = 3)
	private BigDecimal twd97X;

	@Column(name = "twd97_y", precision = 12, scale = 3)
	private BigDecimal twd97Y;

	@Column(name = "lng", precision = 11, scale = 7)
	private BigDecimal lng;

	@Column(name = "lat", precision = 10, scale = 7)
	private BigDecimal lat;

	@Column(name = "elevation", precision = 8, scale = 3)
	private BigDecimal elevation;

	@Column(name = "twd67_x", precision = 12, scale = 3)
	private BigDecimal twd67X;

	@Column(name = "twd67_y", precision = 12, scale = 3)
	private BigDecimal twd67Y;

	@Column(name = "taipower_coord", length = 100)
	private String taipowerCoord;

	// 組織歸屬
	@Column(name = "dept_id")
	private Long deptId;

	@Column(name = "contract_id")
	private Long contractId;

	@Column(name = "property_owner", length = 200)
	private String propertyOwner;

	// 狀態
	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 20)
	private DeviceStatus status;

	@Column(name = "installed_at")
	private LocalDate installedAt;

	@Column(name = "decommissioned_at")
	private LocalDate decommissionedAt;

	// 連線拓撲 / 物理組裝
	@Column(name = "parent_device_id")
	private Long parentDeviceId;

	@Column(name = "mount_position", length = 50)
	private String mountPosition;

	@Enumerated(EnumType.STRING)
	@Column(name = "connectivity_type", length = 20)
	private ConnectivityType connectivityType;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "network_config", columnDefinition = "jsonb")
	private Map<String, Object> networkConfig;

	@Column(name = "last_heartbeat_at")
	private LocalDateTime lastHeartbeatAt;

	// 回路
	@Column(name = "circuit_id")
	private Long circuitId;

	// ── IoT 擴充欄位 ──
	@Column(name = "device_token", length = 200, unique = true)
	private String deviceToken;

	@Column(name = "auth_type", length = 20)
	private String authType;

	@Column(name = "firmware_version", length = 50)
	private String firmwareVersion;

	@Column(name = "last_telemetry_at")
	private LocalDateTime lastTelemetryAt;

	@Column(name = "format_id")
	private Long formatId;

	// 專有屬性（由 DeviceTemplate schema 定義與驗證）
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "attributes", columnDefinition = "jsonb")
	private Map<String, Object> attributes;

	// 審計
	@Column(name = "created_by", length = 50)
	private String createdBy;

	@CreatedDate
	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@LastModifiedDate
	@Column(name = "updated_at")
	private LocalDateTime updatedAt;

	@Override
	public String getTenantId() {
		return tenantId;
	}

	@Override
	public void setTenantId(String tenantId) {
		this.tenantId = tenantId;
	}

}
