package com.taipei.iot.auth.repository;

import com.taipei.iot.auth.entity.ChangePasswordLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChangePasswordLogRepository extends JpaRepository<ChangePasswordLogEntity, Long> {

}
