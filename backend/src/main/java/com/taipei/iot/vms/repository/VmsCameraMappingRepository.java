package com.taipei.iot.vms.repository;

import com.taipei.iot.vms.entity.VmsCameraMappingEntity;
import com.taipei.iot.common.tenant.TenantScopedRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface VmsCameraMappingRepository
		extends JpaRepository<VmsCameraMappingEntity, Long>, TenantScopedRepository {

	List<VmsCameraMappingEntity> findByServerId(Long serverId);

	Optional<VmsCameraMappingEntity> findByVmsCameraId(String vmsCameraId);

	long countByServerId(Long serverId);

}
