package com.taipei.iot.rbac.repository;

import com.taipei.iot.common.tenant.TenantScopeJpql;
import com.taipei.iot.rbac.entity.PermissionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface PermissionRepository extends JpaRepository<PermissionEntity, String> {

	List<PermissionEntity> findAllByOrderByGroupNameAscSortOrderAsc();

	List<PermissionEntity> findByCodeIn(Collection<String> codes);

	/**
	 * 取得所有 permission code（SUPER_ADMIN 用，全域查詢，無租戶條件）。
	 */
	@Query("SELECT p.code FROM PermissionEntity p ORDER BY p.code")
	List<String> findAllCodesOrderByCode();

	/**
	 * 取得指定 role 在指定租戶下的 permission code 集合。
	 * <p>
	 * tenant_id IS NULL 視為全租戶共用權限；tenant_id = :tenantId 為該租戶專屬權限。
	 * </p>
	 * <p>
	 * 用 JPQL（不使用 native SQL）以保持型別安全並集中於 repository 層。
	 * </p>
	 * <p>
	 * tenant scope 條件由 {@link TenantScopeJpql#RP_GLOBAL_OR_TENANT} 集中定義。
	 * </p>
	 */
	@Query("SELECT DISTINCT p.code FROM PermissionEntity p, RolePermissionEntity rp "
			+ "WHERE rp.permissionId = p.permissionId " + "AND rp.roleId = :roleId " + "AND "
			+ TenantScopeJpql.RP_GLOBAL_OR_TENANT + " " + "ORDER BY p.code")
	List<String> findCodesByRoleAndTenant(@Param("roleId") String roleId, @Param("tenantId") String tenantId);

	/**
	 * 取得多個 role 在指定租戶下的 permission code 集合（單一 JOIN，取代兩段查詢）。
	 */
	@Query("SELECT DISTINCT p.code FROM PermissionEntity p, RolePermissionEntity rp "
			+ "WHERE rp.permissionId = p.permissionId " + "AND rp.roleId IN :roleIds " + "AND "
			+ TenantScopeJpql.RP_GLOBAL_OR_TENANT)
	List<String> findCodesByRoleIdsAndTenant(@Param("roleIds") Collection<String> roleIds,
			@Param("tenantId") String tenantId);

}
