package com.taipei.iot.device.repository;

import com.taipei.iot.device.entity.DeviceEvent;
import com.taipei.iot.common.tenant.TenantScopedRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DeviceEventRepository extends JpaRepository<DeviceEvent, Long>, TenantScopedRepository {

	Page<DeviceEvent> findByDeviceIdOrderByEventDateDesc(Long deviceId, Pageable pageable);

	List<DeviceEvent> findByDeviceId(Long deviceId);

	void deleteByRepairTicketId(Long repairTicketId);

	void deleteByReplacementItemIdIn(List<Long> replacementItemIds);

}
