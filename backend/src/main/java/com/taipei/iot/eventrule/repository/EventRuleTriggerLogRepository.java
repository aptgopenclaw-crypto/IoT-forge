package com.taipei.iot.eventrule.repository;

import java.time.LocalDateTime;

import com.taipei.iot.eventrule.entity.EventRuleTriggerLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * {@link EventRuleTriggerLog} JPA repository。查詢時以 {@code tenantId} 明確過濾（無 Hibernate
 * tenantFilter）。
 */
public interface EventRuleTriggerLogRepository extends JpaRepository<EventRuleTriggerLog, Long> {

	@Query("""
			SELECT l FROM EventRuleTriggerLog l
			WHERE l.tenantId = :tenantId AND l.ruleId = :ruleId
			  AND (:from IS NULL OR l.triggeredAt >= :from)
			  AND (:to IS NULL OR l.triggeredAt < :to)
			ORDER BY l.triggeredAt DESC
			""")
	Page<EventRuleTriggerLog> findByRuleIdInWindow(@Param("tenantId") String tenantId, @Param("ruleId") Long ruleId,
			@Param("from") LocalDateTime from, @Param("to") LocalDateTime to, Pageable pageable);

	@Query("""
			SELECT l FROM EventRuleTriggerLog l
			WHERE l.tenantId = :tenantId
			  AND (:from IS NULL OR l.triggeredAt >= :from)
			  AND (:to IS NULL OR l.triggeredAt < :to)
			  AND (:severity IS NULL OR l.severity = :severity)
			ORDER BY l.triggeredAt DESC
			""")
	Page<EventRuleTriggerLog> findByTenantInWindow(@Param("tenantId") String tenantId,
			@Param("from") LocalDateTime from, @Param("to") LocalDateTime to, @Param("severity") String severity,
			Pageable pageable);

}
