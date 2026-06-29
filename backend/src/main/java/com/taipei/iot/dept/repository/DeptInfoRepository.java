package com.taipei.iot.dept.repository;

import com.taipei.iot.dept.entity.DeptInfoEntity;
import com.taipei.iot.tenant.TenantScopedRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DeptInfoRepository extends JpaRepository<DeptInfoEntity, Long>, TenantScopedRepository {

	List<DeptInfoEntity> findAllByStatusOrderByDeptSortAsc(Short status);

	@Query("SELECT d FROM DeptInfoEntity d WHERE d.status = 1 AND d.hierarchyPath LIKE :prefix% ORDER BY d.deptSort ASC")
	List<DeptInfoEntity> findByHierarchyPathStartingWith(@Param("prefix") String prefix);

	Optional<DeptInfoEntity> findByDeptId(Long deptId);

	List<DeptInfoEntity> findByDeptIdIn(Collection<Long> deptIds);

	List<DeptInfoEntity> findByPid(Long pid);

	boolean existsByPid(Long pid);

	boolean existsByTenantIdAndDeptNameAndPid(String tenantId, String deptName, Long pid);

	Optional<DeptInfoEntity> findByTenantIdAndDeptName(String tenantId, String deptName);

}
