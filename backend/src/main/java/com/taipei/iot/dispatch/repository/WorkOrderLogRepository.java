package com.taipei.iot.dispatch.repository;

import com.taipei.iot.dispatch.entity.WorkOrderLog;
import com.taipei.iot.tenant.TenantScopedRepository;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorkOrderLogRepository extends JpaRepository<WorkOrderLog, Long>, TenantScopedRepository {

	List<WorkOrderLog> findByWorkOrderIdOrderByCreatedAtAsc(Long workOrderId);

	List<WorkOrderLog> findByWorkOrderIdAndAction(Long workOrderId, String action);

}
