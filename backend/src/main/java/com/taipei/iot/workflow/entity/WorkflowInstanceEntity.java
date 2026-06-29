package com.taipei.iot.workflow.entity;

import com.taipei.iot.tenant.TenantAware;
import com.taipei.iot.tenant.TenantEntityListener;
import com.taipei.iot.workflow.model.WorkflowStatus;
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

import java.time.LocalDateTime;

@Entity
@Table(name = "workflow_instances")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@EntityListeners({ TenantEntityListener.class, AuditingEntityListener.class

})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowInstanceEntity implements TenantAware {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "tenant_id", nullable = false, length = 50)
	private String tenantId;

	@Column(name = "workflow_def_id", nullable = false)
	private Long workflowDefId;

	@Column(name = "business_id", length = 100, nullable = false)
	private String businessId;

	@Column(name = "business_type", length = 100, nullable = false)
	private String businessType;

	@Column(name = "current_step_id", length = 100)
	private String currentStepId;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", length = 50, nullable = false)
	@Builder.Default
	private WorkflowStatus status = WorkflowStatus.IN_PROGRESS;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "context_json", columnDefinition = "jsonb")
	private String contextJson;

	@CreatedDate
	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@LastModifiedDate
	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	/** 流程進入終態（COMPLETED / CANCELLED / REJECTED）的時間點 */
	@Column(name = "completed_at")
	private LocalDateTime completedAt;

}
