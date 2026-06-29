package com.taipei.iot.workflow.entity;

import com.taipei.iot.tenant.TenantAware;
import com.taipei.iot.tenant.TenantEntityListener;
import com.taipei.iot.workflow.model.WorkflowAction;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Filter;

import java.time.LocalDateTime;

@Entity
@Table(name = "workflow_step_logs")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@EntityListeners(TenantEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowStepLogEntity implements TenantAware {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "tenant_id", nullable = false, length = 50)
	private String tenantId;

	@Column(name = "workflow_instance_id", nullable = false)
	private Long workflowInstanceId;

	@Column(name = "step_id", length = 100, nullable = false)
	private String stepId;

	@Column(name = "step_name", length = 200, nullable = false)
	private String stepName;

	@Column(name = "assignee_user_id", length = 100)
	private String assigneeUserId;

	@Enumerated(EnumType.STRING)
	@Column(name = "action", length = 50)
	private WorkflowAction action;

	@Size(max = 2000)
	@Column(name = "comment", length = 2000)
	private String comment;

	@Column(name = "target_step_id", length = 100)
	private String targetStepId;

	@Column(name = "entered_at", nullable = false)
	@Builder.Default
	private LocalDateTime enteredAt = LocalDateTime.now();

	@Column(name = "completed_at")
	private LocalDateTime completedAt;

}
