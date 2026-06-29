package com.taipei.iot.device.entity;

import com.taipei.iot.device.enums.ContractStatus;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

@Entity
@Table(name = "contracts")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@EntityListeners({ TenantEntityListener.class, AuditingEntityListener.class })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Contract implements TenantAware {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "tenant_id", nullable = false, length = 50)
	private String tenantId;

	@Column(name = "contract_code", nullable = false, length = 100)
	private String contractCode;

	@Column(name = "contract_name", nullable = false, length = 300)
	private String contractName;

	@Column(name = "budget_year")
	private Integer budgetYear;

	@Column(name = "procurement_number", length = 100)
	private String procurementNumber;

	@Column(name = "contractor_name", length = 200)
	private String contractorName;

	@Column(name = "contractor_contact", length = 200)
	private String contractorContact;

	@Column(name = "asset_category", length = 50)
	private String assetCategory;

	@Column(name = "quantity")
	private Integer quantity;

	@Column(name = "start_date")
	private LocalDate startDate;

	@Column(name = "end_date")
	private LocalDate endDate;

	@Column(name = "acceptance_date")
	private LocalDate acceptanceDate;

	@Column(name = "warranty_years")
	private Integer warrantyYears;

	@Column(name = "warranty_expiry")
	private LocalDate warrantyExpiry;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 20)
	private ContractStatus status;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "attributes", columnDefinition = "jsonb")
	private Map<String, Object> attributes;

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
