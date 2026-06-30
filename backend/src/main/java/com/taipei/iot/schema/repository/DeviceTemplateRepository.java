package com.taipei.iot.schema.repository;

import com.taipei.iot.schema.entity.DeviceTemplate;
import com.taipei.iot.common.tenant.TenantScopedRepository;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DeviceTemplateRepository extends JpaRepository<DeviceTemplate, Long>, TenantScopedRepository {

	Optional<DeviceTemplate> findByDeviceType(String deviceType);

}
