package com.taipei.iot.ingest.client;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * {@link DeviceExternalRef} 資料存取。以明確 {@code (tenantId, externalCode)} 查詢內部設備碼。
 */
public interface DeviceExternalRefRepository extends JpaRepository<DeviceExternalRef, Long> {

	Optional<DeviceExternalRef> findByTenantIdAndExternalCode(String tenantId, String externalCode);

}
