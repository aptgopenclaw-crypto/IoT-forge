package com.taipei.iot.workflow.repository;

import com.taipei.iot.tenant.TenantScopedRepository;
import com.taipei.iot.workflow.entity.DelegateSettingEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DelegateSettingRepository extends JpaRepository<DelegateSettingEntity, Long>, TenantScopedRepository {

	@Query("""
			SELECT d FROM DelegateSettingEntity d
			WHERE d.tenantId = :tenantId
			  AND d.delegateFor = :delegateFor
			  AND (d.businessType IS NULL OR d.businessType = :businessType)
			  AND d.effectiveFrom <= :today
			  AND d.effectiveTo   >= :today
			ORDER BY d.businessType DESC NULLS LAST
			LIMIT 1
			""")
	Optional<DelegateSettingEntity> findActiveDelegate(@Param("tenantId") String tenantId,
			@Param("delegateFor") String delegateFor, @Param("businessType") String businessType,
			@Param("today") LocalDate today);

	/**
	 * 查詢今日有效、代理給指定使用者的所有 delegateFor（原始被代理人）userId。
	 */
	@Query("""
			SELECT d.delegateFor FROM DelegateSettingEntity d
			WHERE d.tenantId = :tenantId
			  AND d.delegateTo = :delegateTo
			  AND (d.businessType IS NULL OR d.businessType = :businessType)
			  AND d.effectiveFrom <= :today
			  AND d.effectiveTo   >= :today
			""")
	List<String> findDelegatedUserIds(@Param("tenantId") String tenantId, @Param("delegateTo") String delegateTo,
			@Param("businessType") String businessType, @Param("today") LocalDate today);

	List<DelegateSettingEntity> findByDelegateForOrderByCreatedAtDesc(String delegateFor);

}
