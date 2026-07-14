package com.taipei.iot.vms.repository;

import com.taipei.iot.vms.entity.VmsServerEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface VmsServerRepository extends JpaRepository<VmsServerEntity, Long> {

	List<VmsServerEntity> findByIsActiveTrue();

}
