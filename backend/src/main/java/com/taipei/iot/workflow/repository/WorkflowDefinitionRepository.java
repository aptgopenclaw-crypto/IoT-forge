package com.taipei.iot.workflow.repository;

import com.taipei.iot.tenant.TenantScopedRepository;
import com.taipei.iot.workflow.entity.WorkflowDefinitionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface WorkflowDefinitionRepository
		extends JpaRepository<WorkflowDefinitionEntity, Long>, TenantScopedRepository {

	@Query("SELECT d FROM WorkflowDefinitionEntity d WHERE d.code = :code AND d.enabled = true ORDER BY d.version DESC LIMIT 1")
	Optional<WorkflowDefinitionEntity> findLatestEnabledByCode(@Param("code") String code);

}
