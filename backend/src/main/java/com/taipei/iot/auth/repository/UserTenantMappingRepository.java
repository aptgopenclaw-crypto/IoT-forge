package com.taipei.iot.auth.repository;

import com.taipei.iot.auth.entity.UserTenantMappingEntity;
import com.taipei.iot.tenant.TenantScopedRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserTenantMappingRepository
		extends JpaRepository<UserTenantMappingEntity, Long>, TenantScopedRepository {

	List<UserTenantMappingEntity> findByUserIdAndEnabledTrue(String userId);

	Optional<UserTenantMappingEntity> findByUserIdAndTenantId(String userId, String tenantId);

	List<UserTenantMappingEntity> findByTenantIdAndEnabledTrue(String tenantId);

	List<UserTenantMappingEntity> findByTenantIdAndDeptIdAndEnabledTrue(String tenantId, Long deptId);

	Page<UserTenantMappingEntity> findByTenantId(String tenantId, Pageable pageable);

	Page<UserTenantMappingEntity> findByTenantIdAndDeptId(String tenantId, Long deptId, Pageable pageable);

	Page<UserTenantMappingEntity> findByTenantIdAndDeptIdIn(String tenantId, List<Long> deptIds, Pageable pageable);

	List<UserTenantMappingEntity> findByUserId(String userId);

	@Query("SELECT m FROM UserTenantMappingEntity m JOIN m.tenant t LEFT JOIN FETCH m.role "
			+ "WHERE m.userId = :userId AND t.enabled = true")
	List<UserTenantMappingEntity> findByUserIdAndTenantEnabled(@Param("userId") String userId);

	@Modifying
	@Query("UPDATE UserTenantMappingEntity m SET m.deptId = NULL WHERE m.tenantId = :tenantId AND m.deptId = :deptId")
	void clearDeptIdByTenantIdAndDeptId(@Param("tenantId") String tenantId, @Param("deptId") Long deptId);

	// ---- User list queries with deleted/keyword filter pushed to DB ----

	@Query("SELECT m FROM UserTenantMappingEntity m JOIN FETCH m.user u LEFT JOIN FETCH m.role " + "JOIN m.tenant t "
			+ "WHERE m.tenantId = :tenantId AND u.deleted = false AND t.enabled = true "
			+ "AND (CAST(:keyword AS string) IS NULL OR LOWER(u.email) LIKE LOWER(CONCAT('%', CAST(:keyword AS string),'%')) "
			+ "OR LOWER(u.displayName) LIKE LOWER(CONCAT('%', CAST(:keyword AS string),'%')))")
	Page<UserTenantMappingEntity> findActiveByTenantId(@Param("tenantId") String tenantId,
			@Param("keyword") String keyword, Pageable pageable);

	@Query("SELECT m FROM UserTenantMappingEntity m JOIN FETCH m.user u LEFT JOIN FETCH m.role " + "JOIN m.tenant t "
			+ "WHERE m.tenantId = :tenantId AND m.deptId = :deptId AND u.deleted = false AND t.enabled = true "
			+ "AND (CAST(:keyword AS string) IS NULL OR LOWER(u.email) LIKE LOWER(CONCAT('%', CAST(:keyword AS string),'%')) "
			+ "OR LOWER(u.displayName) LIKE LOWER(CONCAT('%', CAST(:keyword AS string),'%')))")
	Page<UserTenantMappingEntity> findActiveByTenantIdAndDeptId(@Param("tenantId") String tenantId,
			@Param("deptId") Long deptId, @Param("keyword") String keyword, Pageable pageable);

	@Query("SELECT m FROM UserTenantMappingEntity m JOIN FETCH m.user u LEFT JOIN FETCH m.role " + "JOIN m.tenant t "
			+ "WHERE m.tenantId = :tenantId AND m.deptId IN :deptIds AND u.deleted = false AND t.enabled = true "
			+ "AND (CAST(:keyword AS string) IS NULL OR LOWER(u.email) LIKE LOWER(CONCAT('%', CAST(:keyword AS string),'%')) "
			+ "OR LOWER(u.displayName) LIKE LOWER(CONCAT('%', CAST(:keyword AS string),'%')))")
	Page<UserTenantMappingEntity> findActiveByTenantIdAndDeptIdIn(@Param("tenantId") String tenantId,
			@Param("deptIds") Collection<Long> deptIds, @Param("keyword") String keyword, Pageable pageable);

	@Query("SELECT m FROM UserTenantMappingEntity m JOIN FETCH m.user u LEFT JOIN FETCH m.role " + "JOIN m.tenant t "
			+ "WHERE u.deleted = false AND t.enabled = true "
			+ "AND (CAST(:keyword AS string) IS NULL OR LOWER(u.email) LIKE LOWER(CONCAT('%', CAST(:keyword AS string),'%')) "
			+ "OR LOWER(u.displayName) LIKE LOWER(CONCAT('%', CAST(:keyword AS string),'%')))")
	Page<UserTenantMappingEntity> findAllActive(@Param("keyword") String keyword, Pageable pageable);

	List<UserTenantMappingEntity> findByTenantIdAndRoleIdAndEnabledTrue(String tenantId, String roleId);

	List<UserTenantMappingEntity> findByTenantIdAndRoleIdAndEnabledTrueOrderByUserIdAsc(String tenantId, String roleId);

	List<UserTenantMappingEntity> findByTenantIdAndDeptIdAndRoleIdAndEnabledTrue(String tenantId, Long deptId,
			String roleId);

	List<UserTenantMappingEntity> findByTenantIdAndDeptIdAndRoleIdAndEnabledTrueOrderByUserIdAsc(String tenantId,
			Long deptId, String roleId);

}
