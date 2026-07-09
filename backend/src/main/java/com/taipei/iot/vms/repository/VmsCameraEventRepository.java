package com.taipei.iot.vms.repository;

import com.taipei.iot.vms.entity.VmsCameraEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface VmsCameraEventRepository extends JpaRepository<VmsCameraEvent, Long> {

	Page<VmsCameraEvent> findByTenantIdAndCameraIdAndOccurredAtBetween(String tenantId, Long cameraId,
			LocalDateTime from, LocalDateTime to, Pageable pageable);

}
