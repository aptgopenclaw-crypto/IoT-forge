package com.taipei.iot.user.repository;

import com.taipei.iot.user.entity.ChangePasswordLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChangePasswordLogRepository extends JpaRepository<ChangePasswordLogEntity, Long> {

}
