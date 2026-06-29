package com.taipei.iot.workflow.repository;

import com.taipei.iot.tenant.TenantScopedRepository;
import com.taipei.iot.workflow.entity.WorkflowStepLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface WorkflowStepLogRepository extends JpaRepository<WorkflowStepLogEntity, Long>, TenantScopedRepository {

	List<WorkflowStepLogEntity> findByWorkflowInstanceIdOrderByEnteredAtAsc(Long workflowInstanceId);

	List<WorkflowStepLogEntity> findByAssigneeUserIdAndCompletedAtIsNull(String assigneeUserId);

	List<WorkflowStepLogEntity> findByAssigneeUserIdInAndCompletedAtIsNull(List<String> assigneeUserIds);

	@Query("""
			SELECT l FROM WorkflowStepLogEntity l
			WHERE l.workflowInstanceId = :instanceId
			  AND l.completedAt IS NULL
			ORDER BY l.enteredAt DESC
			LIMIT 1
			""")
	Optional<WorkflowStepLogEntity> findCurrentByInstanceId(@Param("instanceId") Long instanceId);

}
