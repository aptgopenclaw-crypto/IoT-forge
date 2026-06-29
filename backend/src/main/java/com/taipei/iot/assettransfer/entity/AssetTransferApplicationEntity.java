package com.taipei.iot.assettransfer.entity;

import com.taipei.iot.tenant.TenantAware;
import com.taipei.iot.tenant.TenantEntityListener;
import com.taipei.iot.assettransfer.enums.AssetTransferStatus;
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
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "asset_transfer_applications")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@EntityListeners({ TenantEntityListener.class, AuditingEntityListener.class })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssetTransferApplicationEntity implements TenantAware {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "tenant_id", nullable = false, length = 50)
	private String tenantId;

	@Column(name = "application_no", nullable = false, length = 64)
	private String applicationNo;

	@Column(name = "applicant_id", nullable = false, length = 64)
	private String applicantId;

	@Column(name = "applicant_name", length = 128)
	private String applicantName;

	@Column(name = "department_id", nullable = false)
	private Long departmentId;

	@Column(name = "department_name", length = 128)
	private String departmentName;

	@Column(name = "asset_code", nullable = false, length = 64)
	private String assetCode;

	@Column(name = "asset_name", nullable = false, length = 256)
	private String assetName;

	@Column(name = "transfer_type", nullable = false, length = 32)
	private String transferType;

	@Column(name = "target_department_id")
	private Long targetDepartmentId;

	@Column(name = "reason")
	private String reason;

	@Column(name = "asset_value", precision = 20, scale = 2)
	private BigDecimal assetValue;

	@Column(name = "workflow_instance_id")
	private Long workflowInstanceId;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 32)
	@Builder.Default
	private AssetTransferStatus status = AssetTransferStatus.DRAFT;

	@Column(name = "current_assignee", length = 64)
	private String currentAssignee;

	@CreatedDate
	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@Column(name = "created_by", length = 64)
	private String createdBy;

	@LastModifiedDate
	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	@Column(name = "updated_by", length = 64)
	private String updatedBy;

	@Column(name = "approved_at")
	private LocalDateTime approvedAt;

	@Column(name = "approved_by", length = 64)
	private String approvedBy;

	@Column(name = "reject_reason")
	private String rejectReason;

	@Override
	public String getTenantId() {
		return tenantId;
	}

	@Override
	public void setTenantId(String tenantId) {
		this.tenantId = tenantId;
	}

}
