package com.taipei.iot.vms.repository;

import com.taipei.iot.vms.entity.VmsServerEntity;
import com.taipei.iot.common.tenant.TenantScopedRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface VmsServerRepository extends JpaRepository<VmsServerEntity, Long>, TenantScopedRepository {

	List<VmsServerEntity> findByIsActiveTrue();

}
