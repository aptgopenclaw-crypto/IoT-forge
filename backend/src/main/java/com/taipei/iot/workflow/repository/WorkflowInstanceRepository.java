package com.taipei.iot.workflow.repository;

import com.taipei.iot.tenant.TenantScopedRepository;
import com.taipei.iot.workflow.entity.WorkflowInstanceEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface WorkflowInstanceRepository
		extends JpaRepository<WorkflowInstanceEntity, Long>, TenantScopedRepository {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT i FROM WorkflowInstanceEntity i WHERE i.id = :id")
	Optional<WorkflowInstanceEntity> findByIdForUpdate(@Param("id") Long id);

}
