package com.taipei.iot.vms.repository;

import com.taipei.iot.vms.entity.VmsStreamLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface VmsStreamLogRepository
		extends JpaRepository<VmsStreamLogEntity, Long>, JpaSpecificationExecutor<VmsStreamLogEntity> {

}
