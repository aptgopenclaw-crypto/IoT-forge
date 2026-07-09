package com.taipei.iot.vms.repository;

import com.taipei.iot.common.tenant.TenantScopedRepository;
import com.taipei.iot.vms.entity.VmsCamera;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VmsCameraRepository extends JpaRepository<VmsCamera, Long>, TenantScopedRepository {

	List<VmsCamera> findByTenantId(String tenantId);

	Optional<VmsCamera> findByIdAndTenantId(Long id, String tenantId);

	List<VmsCamera> findByServerIdAndTenantId(Long serverId, String tenantId);

	List<VmsCamera> findByTenantIdAndDeptIdIn(String tenantId, java.util.Collection<Long> deptIds);

	List<VmsCamera> findByServerIdAndTenantIdAndDeptIdIn(Long serverId, String tenantId,
			java.util.Collection<Long> deptIds);

}
