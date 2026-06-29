package com.taipei.iot.user.repository;

import com.taipei.iot.user.entity.PasswordHistoryEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PasswordHistoryRepository extends JpaRepository<PasswordHistoryEntity, Long> {

	/**
	 * Returns the user's most recent password hashes ordered newest-first. Caller
	 * controls how many entries are fetched via {@link Pageable}, driven by the effective
	 * {@code password.history_count} policy value.
	 */
	List<PasswordHistoryEntity> findByUserIdOrderByCreateTimeDesc(String userId, Pageable pageable);

}
