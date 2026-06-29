package com.taipei.iot.device.repository;

import com.taipei.iot.device.entity.DeviceManager;
import com.taipei.iot.tenant.TenantScopedRepository;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DeviceManagerRepository extends JpaRepository<DeviceManager, Long>, TenantScopedRepository {

	List<DeviceManager> findByDeviceId(Long deviceId);

	boolean existsByDeviceIdAndUserId(Long deviceId, String userId);

	void deleteByDeviceIdAndUserId(Long deviceId, String userId);

}
