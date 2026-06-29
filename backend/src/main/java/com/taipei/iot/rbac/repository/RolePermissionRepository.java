package com.taipei.iot.rbac.repository;

import com.taipei.iot.common.tenant.TenantScopeJpql;
import com.taipei.iot.rbac.entity.RolePermissionEntity;
import com.taipei.iot.rbac.entity.RolePermissionId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface RolePermissionRepository extends JpaRepository<RolePermissionEntity, RolePermissionId> {

	@Query("SELECT rp FROM RolePermissionEntity rp WHERE rp.roleId = :roleId AND "
			+ TenantScopeJpql.RP_GLOBAL_OR_TENANT)
	List<RolePermissionEntity> findByRoleIdAndTenantScope(@Param("roleId") String roleId,
			@Param("tenantId") String tenantId);

	@Query("SELECT rp FROM RolePermissionEntity rp WHERE rp.roleId IN :roleIds AND "
			+ TenantScopeJpql.RP_GLOBAL_OR_TENANT)
	List<RolePermissionEntity> findByRoleIdInAndTenantScope(@Param("roleIds") Collection<String> roleIds,
			@Param("tenantId") String tenantId);

	List<RolePermissionEntity> findByRoleId(String roleId);

	@Modifying
	@Query("DELETE FROM RolePermissionEntity rp WHERE rp.roleId = :roleId")
	void deleteByRoleId(@Param("roleId") String roleId);

}
