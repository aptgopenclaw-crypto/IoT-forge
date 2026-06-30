package com.taipei.iot.dispatch.entity;

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
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "work_order_logs")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@EntityListeners({ TenantEntityListener.class, AuditingEntityListener.class })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkOrderLog implements TenantAware {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "tenant_id", nullable = false, length = 50)
	private String tenantId;

	@Column(name = "work_order_id", nullable = false)
	private Long workOrderId;

	@Column(name = "action", nullable = false, length = 30)
	private String action;

	@Column(name = "from_status", length = 20)
	private String fromStatus;

	@Column(name = "to_status", length = 20)
	private String toStatus;

	@Column(name = "operator_id", length = 50)
	private String operatorId;

	@Column(name = "operator_name", length = 100)
	private String operatorName;

	@Column(name = "latitude", precision = 10, scale = 7)
	private BigDecimal latitude;

	@Column(name = "longitude", precision = 11, scale = 7)
	private BigDecimal longitude;

	@Column(name = "note", columnDefinition = "TEXT")
	private String note;

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
