package com.taipei.iot.rbac.repository;

import com.taipei.iot.rbac.entity.MenuEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface MenuRepository extends JpaRepository<MenuEntity, Long> {

	List<MenuEntity> findByParentIdOrderBySortOrder(Long parentId);

	List<MenuEntity> findAllByOrderBySortOrder();

	List<MenuEntity> findByPermissionCodeInAndVisibleTrue(Collection<String> codes);

	List<MenuEntity> findByPermissionCodeIsNullAndVisibleTrue();

	List<MenuEntity> findByScopeAndVisibleTrue(String scope);

	List<MenuEntity> findByScopeInAndVisibleTrue(Collection<String> scopes);

	boolean existsByParentId(Long parentId);

}
