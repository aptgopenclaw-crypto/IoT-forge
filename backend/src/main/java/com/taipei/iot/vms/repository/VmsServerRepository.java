package com.taipei.iot.vms.repository;

import com.taipei.iot.vms.entity.VmsServer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VmsServerRepository extends JpaRepository<VmsServer, Long> {

	List<VmsServer> findByTenantIdAndIsActiveTrue(String tenantId);

	Optional<VmsServer> findByIdAndTenantId(Long id, String tenantId);

}
