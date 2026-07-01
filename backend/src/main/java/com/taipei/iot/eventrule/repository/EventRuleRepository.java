package com.taipei.iot.eventrule.repository;

import java.util.List;
import java.util.Optional;

import com.taipei.iot.common.tenant.TenantScopedRepository;
import com.taipei.iot.eventrule.entity.EventRule;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * {@link EventRule} JPA repository（租戶隔離由 Hibernate tenantFilter 保護）。
 */
public interface EventRuleRepository extends JpaRepository<EventRule, Long>, TenantScopedRepository {

	Optional<EventRule> findByTenantIdAndRuleCode(String tenantId, String ruleCode);

	boolean existsByTenantIdAndRuleCode(String tenantId, String ruleCode);

	/** 載入指定租戶 + 設備型別的所有啟用規則（供 RuleEvaluator 評估）。 */
	List<EventRule> findByTenantIdAndDeviceTypeAndEnabledTrue(String tenantId, String deviceType);

	@Query("""
			SELECT e FROM EventRule e
			WHERE (:deviceType IS NULL OR e.deviceType = :deviceType)
			  AND (:enabled IS NULL OR e.enabled = :enabled)
			ORDER BY e.createTime DESC
			""")
	Page<EventRule> findByFilters(@Param("deviceType") String deviceType, @Param("enabled") Boolean enabled,
			Pageable pageable);

}
