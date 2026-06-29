package com.taipei.iot.platform.impersonation.repository;

import com.taipei.iot.platform.impersonation.entity.ImpersonationSessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ImpersonationSessionRepository extends JpaRepository<ImpersonationSessionEntity, String> {

	List<ImpersonationSessionEntity> findByOperatorUserIdOrderByStartedAtDesc(String operatorUserId);

	List<ImpersonationSessionEntity> findByOperatorUserIdAndStatusOrderByStartedAtDesc(String operatorUserId,
			String status);

}
