package com.taipei.iot.workflow.entity;

import com.taipei.iot.tenant.TenantAware;
import com.taipei.iot.tenant.TenantEntityListener;
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
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "workflow_definitions")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId OR tenant_id = 'DEFAULT'")
@EntityListeners({ TenantEntityListener.class, AuditingEntityListener.class })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowDefinitionEntity implements TenantAware {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "tenant_id", nullable = false, length = 50)
	private String tenantId;

	@Column(name = "code", length = 100, nullable = false)
	private String code;

	@Column(name = "version", nullable = false)
	@Builder.Default
	private Integer version = 1;

	@Column(name = "name", length = 200, nullable = false)
	private String name;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "steps_json", nullable = false, columnDefinition = "jsonb")
	private String stepsJson;

	@Column(name = "enabled", nullable = false)
	@Builder.Default
	private Boolean enabled = true;

	@Column(name = "created_at", nullable = false, updatable = false)
	@Builder.Default
	private LocalDateTime createdAt = LocalDateTime.now();

	@Column(name = "updated_at", nullable = false)
	@Builder.Default
	private LocalDateTime updatedAt = LocalDateTime.now();

}
