package com.taipei.iot.user.repository;

import com.taipei.iot.tenant.TenantScopedRepository;
import com.taipei.iot.user.entity.UserInfoLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserInfoLogRepository extends JpaRepository<UserInfoLogEntity, Long>, TenantScopedRepository {

	List<UserInfoLogEntity> findByTargetUserIdOrderByCreateTimeDesc(String targetUserId);

}
