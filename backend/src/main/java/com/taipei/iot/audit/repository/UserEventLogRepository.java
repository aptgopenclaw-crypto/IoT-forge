package com.taipei.iot.audit.repository;

import com.taipei.iot.audit.entity.UserEventLogEntity;
import com.taipei.iot.tenant.TenantScopedRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface UserEventLogRepository extends JpaRepository<UserEventLogEntity, Long>,
		JpaSpecificationExecutor<UserEventLogEntity>, TenantScopedRepository {

	@Modifying
	@Query("DELETE FROM UserEventLogEntity e WHERE e.createTime < :cutoff")
	int deleteByCreateTimeBefore(@Param("cutoff") LocalDateTime cutoff);

	@Modifying
	@Query("DELETE FROM UserEventLogEntity e WHERE e.tenantId = :tenantId AND e.createTime < :cutoff")
	int deleteByTenantIdAndCreateTimeBefore(@Param("tenantId") String tenantId, @Param("cutoff") LocalDateTime cutoff);

	@Modifying
	@Query("DELETE FROM UserEventLogEntity e WHERE e.tenantId IS NULL AND e.createTime < :cutoff")
	int deleteByTenantIdNullAndCreateTimeBefore(@Param("cutoff") LocalDateTime cutoff);

}
