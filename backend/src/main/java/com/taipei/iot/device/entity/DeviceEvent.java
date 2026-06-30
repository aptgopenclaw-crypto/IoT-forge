package com.taipei.iot.device.entity;

import com.taipei.iot.common.tenant.TenantAware;
import com.taipei.iot.common.tenant.TenantEntityListener;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
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
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "device_events")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@EntityListeners({ TenantEntityListener.class, AuditingEntityListener.class })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceEvent implements TenantAware {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "tenant_id", nullable = false, length = 50)
	private String tenantId;

	@Column(name = "device_id", nullable = false)
	private Long deviceId;

	@Column(name = "event_type", nullable = false, length = 30)
	private String eventType;

	@Column(name = "event_date", nullable = false)
	private LocalDateTime eventDate;

	@Column(name = "description", columnDefinition = "TEXT")
	private String description;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "attachments", columnDefinition = "jsonb")
	private List<Map<String, Object>> attachments;

	@Column(name = "repair_ticket_id")
	private Long repairTicketId;

	@Column(name = "replacement_item_id")
	private Long replacementItemId;

	@Column(name = "created_by", length = 50)
	private String createdBy;

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
