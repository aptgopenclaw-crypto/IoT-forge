package com.taipei.iot.dispatch.entity;

import com.taipei.iot.dispatch.enums.WorkOrderSourceType;
import com.taipei.iot.dispatch.enums.WorkOrderStatus;
import com.taipei.iot.common.tenant.TenantAware;
import com.taipei.iot.common.tenant.TenantEntityListener;
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
import java.time.LocalDateTime;
import java.util.Map;

@Entity
@Table(name = "work_orders")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@EntityListeners({ TenantEntityListener.class, AuditingEntityListener.class })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkOrder implements TenantAware {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "tenant_id", nullable = false, length = 50)
	private String tenantId;

	// ── 關聯 ──
	@Column(name = "device_id")
	private Long deviceId;

	@Column(name = "circuit_id")
	private Long circuitId;

	@Column(name = "contract_id")
	private Long contractId;

	// ── 工單類型與來源 ──
	@Column(name = "order_type", nullable = false, length = 20)
	private String orderType;

	@Enumerated(EnumType.STRING)
	@Column(name = "source_type", nullable = false, length = 20)
	private WorkOrderSourceType sourceType;

	// ── 狀態 ──
	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 20)
	private WorkOrderStatus status;

	@Column(name = "priority", length = 10)
	private String priority;

	// ── 通報資訊 ──
	@Column(name = "reporter_name", length = 100)
	private String reporterName;

	@Column(name = "reporter_contact", length = 100)
	private String reporterContact;

	@Column(name = "reported_at")
	private LocalDateTime reportedAt;

	@Column(name = "description", columnDefinition = "TEXT")
	private String description;

	// ── 地點快照（工單建立時凍結，防止設備移動後失準）──
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "location_snapshot", columnDefinition = "jsonb")
	private Map<String, Object> locationSnapshot;

	// ── 派工 ──
	@Column(name = "assigned_to", length = 50)
	private String assignedTo;

	@Column(name = "assigned_at")
	private LocalDateTime assignedAt;

	@Column(name = "assigned_by", length = 50)
	private String assignedBy;

	// ── 技師到場（GPS 打卡）──
	@Column(name = "started_at")
	private LocalDateTime startedAt;

	@Column(name = "start_lat", precision = 10, scale = 7)
	private BigDecimal startLat;

	@Column(name = "start_lng", precision = 11, scale = 7)
	private BigDecimal startLng;

	// ── 維修完成 ──
	@Column(name = "completed_at")
	private LocalDateTime completedAt;

	@Column(name = "completion_remark", columnDefinition = "TEXT")
	private String completionRemark;

	@Column(name = "fault_cause", length = 100)
	private String faultCause;

	@Column(name = "repair_cost")
	private Integer repairCost;

	// ── 覆核 ──
	@Column(name = "reviewer_id", length = 50)
	private String reviewerId;

	@Column(name = "reviewed_at")
	private LocalDateTime reviewedAt;

	@Column(name = "reject_reason", columnDefinition = "TEXT")
	private String rejectReason;

	@Column(name = "review_workflow_instance_id")
	private Long reviewWorkflowInstanceId;

	// ── 結案 ──
	@Column(name = "closed_at")
	private LocalDateTime closedAt;

	@Column(name = "closed_by", length = 50)
	private String closedBy;

	// ── 智能通報 ──
	@Column(name = "auto_reported_at")
	private LocalDateTime autoReportedAt;

	// ── 附件 ──
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "attachments", columnDefinition = "jsonb")
	private Map<String, Object> attachments;

	// ── 審計 ──
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
