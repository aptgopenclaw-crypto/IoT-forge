package com.taipei.iot.auth.provider.config.repository;

import com.taipei.iot.auth.provider.config.entity.TenantAuthConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TenantAuthConfigRepository extends JpaRepository<TenantAuthConfigEntity, Long> {

	Optional<TenantAuthConfigEntity> findByTenantId(String tenantId);

	void deleteByTenantId(String tenantId);

}
