package com.taipei.iot.tenant;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TenantRepository extends JpaRepository<TenantEntity, String> {

	Optional<TenantEntity> findByTenantCode(String tenantCode);

	List<TenantEntity> findByEnabledTrue();

}
