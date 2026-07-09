package com.taipei.iot.vms.entity;

import com.taipei.iot.common.tenant.TenantAware;
import com.taipei.iot.common.tenant.TenantEntityListener;
import com.taipei.iot.vms.enums.CameraStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 攝影機映射實體，對應 {@code vms_cameras} 表。
 *
 * <p>
 * 將 VMS 端的攝影機 ID 映射到本地記錄，並快取 RTSP URL 與狀態。
 * </p>
 */
@Entity
@Table(name = "vms_cameras")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@EntityListeners({ TenantEntityListener.class, AuditingEntityListener.class })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VmsCamera implements TenantAware {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "tenant_id", nullable = false, length = 50)
	private String tenantId;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "server_id", nullable = false)
	private VmsServer server;

	@Column(name = "vms_camera_id", nullable = false, length = 100)
	private String vmsCameraId;

	@Column(name = "display_name", length = 200)
	private String displayName;

	@Column(name = "device_id")
	private Long deviceId;

	@Column(name = "rtsp_url", length = 500)
	private String rtspUrl;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 20)
	@Builder.Default
	private CameraStatus status = CameraStatus.ONLINE;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "metadata", columnDefinition = "jsonb")
	private Map<String, Object> metadata;

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
