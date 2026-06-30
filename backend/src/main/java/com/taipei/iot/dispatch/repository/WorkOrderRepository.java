package com.taipei.iot.dispatch.repository;

import com.taipei.iot.dispatch.entity.WorkOrder;
import com.taipei.iot.dispatch.enums.WorkOrderSourceType;
import com.taipei.iot.dispatch.enums.WorkOrderStatus;
import com.taipei.iot.common.tenant.TenantScopedRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface WorkOrderRepository extends JpaRepository<WorkOrder, Long>, TenantScopedRepository {

	@Query("""
			SELECT w FROM WorkOrder w
			WHERE (:deviceId IS NULL OR w.deviceId = :deviceId)
			  AND (:status IS NULL OR w.status = :status)
			  AND (:sourceType IS NULL OR w.sourceType = :sourceType)
			  AND (:keyword IS NULL
			       OR LOWER(w.description) LIKE LOWER(CONCAT('%', CAST(:keyword AS string), '%'))
			       OR LOWER(w.reporterName) LIKE LOWER(CONCAT('%', CAST(:keyword AS string), '%')))
			ORDER BY w.createdAt DESC
			""")
	Page<WorkOrder> findByFilters(@Param("deviceId") Long deviceId, @Param("status") WorkOrderStatus status,
			@Param("sourceType") WorkOrderSourceType sourceType, @Param("keyword") String keyword, Pageable pageable);

	@Query("""
			SELECT w FROM WorkOrder w
			WHERE w.assignedTo = :userId
			  AND w.status IN :activeStatuses
			ORDER BY w.priority, w.createdAt
			""")
	List<WorkOrder> findActiveByAssignee(@Param("userId") String userId,
			@Param("activeStatuses") List<WorkOrderStatus> activeStatuses);

	long countByDeviceId(Long deviceId);

	long countByDeviceIdAndStatusNot(Long deviceId, WorkOrderStatus status);

	@Query("SELECT COUNT(w) FROM WorkOrder w WHERE w.status NOT IN :closedStatuses")
	long countOpenWorkOrders(@Param("closedStatuses") List<WorkOrderStatus> closedStatuses);

	Optional<WorkOrder> findByReviewWorkflowInstanceId(Long reviewWorkflowInstanceId);

	@Query("""
			SELECT w FROM WorkOrder w
			WHERE w.status IN :statuses
			ORDER BY w.createdAt DESC
			""")
	Page<WorkOrder> findByStatusIn(@Param("statuses") List<WorkOrderStatus> statuses, Pageable pageable);

	@Query("""
			SELECT w FROM WorkOrder w
			WHERE w.assignedTo = :userId
			  AND w.status IN :statuses
			ORDER BY w.createdAt DESC
			""")
	Page<WorkOrder> findByAssignedToAndStatusIn(@Param("userId") String userId,
			@Param("statuses") List<WorkOrderStatus> statuses, Pageable pageable);

}
