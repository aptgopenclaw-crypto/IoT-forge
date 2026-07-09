package com.taipei.iot.vms.entity;

import com.taipei.iot.common.tenant.TenantAware;
import com.taipei.iot.vms.enums.VmsEventType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * VMS 事件紀錄實體，對應 {@code vms_camera_events} 表。
 *
 * <p>
 * append-only，接收 VMS webhook 後寫入。查詢層以 {@code tenant_id} 明確過濾 （同
 * {@code event_rule_trigger_log} 策略，不掛 tenantFilter）。
 * </p>
 */
@Entity
@Table(name = "vms_camera_events")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VmsCameraEvent implements TenantAware {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "tenant_id", nullable = false, length = 50)
	private String tenantId;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "camera_id", nullable = false)
	private VmsCamera camera;

	@Enumerated(EnumType.STRING)
	@Column(name = "event_type", nullable = false, length = 50)
	private VmsEventType eventType;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "payload", columnDefinition = "jsonb")
	private Map<String, Object> payload;

	@Column(name = "occurred_at", nullable = false)
	private LocalDateTime occurredAt;

	@CreatedDate
	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@Override
	public String getTenantId() {
		return tenantId;
	}

	@Override
	public void setTenantId(String tenantId) {
		this.tenantId = tenantId;
	}

}
