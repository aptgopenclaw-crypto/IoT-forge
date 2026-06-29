package com.taipei.iot.rbac.service;

import com.taipei.iot.common.enums.ErrorCode;
import com.taipei.iot.common.exception.BusinessException;
import com.taipei.iot.rbac.dto.request.CreateMenuRequest;
import com.taipei.iot.rbac.dto.request.UpdateMenuRequest;
import com.taipei.iot.rbac.dto.response.MenuDto;
import com.taipei.iot.rbac.dto.response.UserMenuDto;
import com.taipei.iot.rbac.entity.MenuEntity;
import com.taipei.iot.rbac.repository.MenuRepository;
import com.taipei.iot.rbac.repository.PermissionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MenuService {

	private final MenuRepository menuRepository;

	private final PermissionRepository permissionRepository;

	@Transactional(readOnly = true)
	public List<MenuDto> getMenuTree() {
		List<MenuEntity> allMenus = menuRepository.findAllByOrderBySortOrder();
		return buildMenuTree(allMenus);
	}

	@Transactional(readOnly = true)
	public List<UserMenuDto> getMyMenus(List<String> roleIds, String tenantId) {
		// [Phase B] Scope-aware menu resolution.
		// - SUPER_ADMIN 在 SYSTEM context（無 tenantId）→ PLATFORM + PUBLIC scope
		// - SUPER_ADMIN 在 tenant context（impersonating）→ TENANT + PUBLIC scope（如該租戶管理員）
		// - 一般角色 → TENANT + PUBLIC scope，並依 permission_code 過濾
		// [Phase 3 / 3.1.3] 移除原 SUPER_ADMIN bypass（之前 super_admin 直接以
		// scope 收齊全部 menu、跳過 permission 過濾）。現在所有 role 一律走
		// findCodesByRoleIdsAndTenant + permission_code 過濾；super_admin 將
		// 只看到綁定 PLATFORM_* permission 的 menu（V65 種入 4 個）。
		boolean isSuperAdmin = roleIds.stream().anyMatch("ROLE_SUPER_ADMIN"::equals);
		boolean inTenantContext = tenantId != null;
		List<String> allowedScopes = (isSuperAdmin && !inTenantContext) ? List.of("PLATFORM", "PUBLIC")
				: List.of("TENANT", "PUBLIC");

		// 1. Get permission codes for the user's roles (single JOIN query)
		List<String> permissionCodes = permissionRepository.findCodesByRoleIdsAndTenant(roleIds, tenantId);

		// 無任何 permission → 無可見 menu（避免不必要的 DB 查詢）
		if (permissionCodes.isEmpty()) {
			return List.of();
		}

		// 2. Scope 候選清單：依角色身份決定 PLATFORM / TENANT / PUBLIC
		List<MenuEntity> scopeCandidates = menuRepository.findByScopeInAndVisibleTrue(allowedScopes);

		// 3. PAGE 項目以 permission_code 過濾；permission_code 為 null 視為公開
		Set<String> permissionCodeSet = new HashSet<>(permissionCodes);
		List<MenuEntity> result = scopeCandidates.stream()
			.filter(m -> !"DIRECTORY".equals(m.getMenuType()))
			.filter(m -> m.getPermissionCode() == null || permissionCodeSet.contains(m.getPermissionCode()))
			.collect(Collectors.toList());

		Set<Long> resultIds = result.stream().map(MenuEntity::getMenuId).collect(Collectors.toSet());

		// 4. 再加入有可見子節點的 DIRECTORY 父節點（限定在允許 scope 內）
		for (MenuEntity menu : scopeCandidates) {
			if ("DIRECTORY".equals(menu.getMenuType()) && !resultIds.contains(menu.getMenuId())
					&& hasVisibleChild(menu.getMenuId(), result)) {
				result.add(menu);
				resultIds.add(menu.getMenuId());
			}
		}

		return buildUserMenuTree(result);
	}

	@Transactional
	public MenuDto createMenu(CreateMenuRequest req) {
		MenuEntity entity = MenuEntity.builder()
			.parentId(req.getParentId())
			.name(req.getName())
			.menuType(req.getMenuType())
			.routeName(req.getRouteName())
			.routePath(req.getRoutePath())
			.component(req.getComponent())
			.permissionCode(req.getPermissionCode())
			.icon(req.getIcon())
			.sortOrder(req.getSortOrder())
			.visible(req.isVisible())
			.keepAlive(req.isKeepAlive())
			.redirect(req.getRedirect())
			.scope(req.getScope() != null ? req.getScope() : "TENANT")
			.createTime(LocalDateTime.now())
			.build();

		MenuEntity saved = menuRepository.save(entity);
		return toMenuDto(saved);
	}

	@Transactional
	public MenuDto updateMenu(UpdateMenuRequest req) {
		MenuEntity entity = menuRepository.findById(req.getMenuId())
			.orElseThrow(() -> new BusinessException(ErrorCode.MENU_NOT_FOUND));

		// Cycle detection: if parentId is changing, walk up from new parent to ensure no
		// cycle
		if (req.getParentId() != null && !req.getParentId().equals(entity.getParentId())) {
			detectCycle(req.getMenuId(), req.getParentId());
		}

		entity.setParentId(req.getParentId());
		entity.setName(req.getName());
		entity.setMenuType(req.getMenuType());
		entity.setRouteName(req.getRouteName());
		entity.setRoutePath(req.getRoutePath());
		entity.setComponent(req.getComponent());
		entity.setPermissionCode(req.getPermissionCode());
		entity.setIcon(req.getIcon());
		entity.setSortOrder(req.getSortOrder());
		entity.setVisible(req.isVisible());
		entity.setKeepAlive(req.isKeepAlive());
		entity.setRedirect(req.getRedirect());
		if (req.getScope() != null) {
			entity.setScope(req.getScope());
		}
		entity.setUpdateTime(LocalDateTime.now());

		MenuEntity saved = menuRepository.save(entity);
		return toMenuDto(saved);
	}

	@Transactional
	public void deleteMenu(Long menuId) {
		if (!menuRepository.existsById(menuId)) {
			throw new BusinessException(ErrorCode.MENU_NOT_FOUND);
		}
		if (menuRepository.existsByParentId(menuId)) {
			throw new BusinessException(ErrorCode.MENU_HAS_CHILDREN);
		}
		menuRepository.deleteById(menuId);
	}

	@Transactional
	public void toggleVisible(Long menuId, boolean visible) {
		MenuEntity entity = menuRepository.findById(menuId)
			.orElseThrow(() -> new BusinessException(ErrorCode.MENU_NOT_FOUND));
		entity.setVisible(visible);
		entity.setUpdateTime(LocalDateTime.now());
		menuRepository.save(entity);
	}

	// ---- private helpers ----

	/**
	 * Walk up from newParentId; if we encounter menuId, it means assigning this parent
	 * would create a cycle (menuId → ... → newParentId → ... → menuId).
	 */
	private void detectCycle(Long menuId, Long newParentId) {
		List<MenuEntity> allMenus = menuRepository.findAllByOrderBySortOrder();
		Map<Long, MenuEntity> menuMap = allMenus.stream().collect(Collectors.toMap(MenuEntity::getMenuId, m -> m));

		Set<Long> visited = new HashSet<>();
		Long current = newParentId;
		while (current != null) {
			if (current.equals(menuId)) {
				throw new BusinessException(ErrorCode.MENU_CYCLE_DETECTED);
			}
			if (!visited.add(current)) {
				// existing cycle in DB data — still reject
				throw new BusinessException(ErrorCode.MENU_CYCLE_DETECTED);
			}
			MenuEntity parent = menuMap.get(current);
			current = (parent != null) ? parent.getParentId() : null;
		}
	}

	private boolean hasVisibleChild(Long parentId, List<MenuEntity> permittedMenus) {
		return permittedMenus.stream().anyMatch(m -> parentId.equals(m.getParentId()));
	}

	private List<MenuDto> buildMenuTree(List<MenuEntity> allMenus) {
		Map<Long, List<MenuEntity>> groupedByParent = allMenus.stream()
			.filter(m -> m.getParentId() != null)
			.collect(Collectors.groupingBy(MenuEntity::getParentId));

		return allMenus.stream()
			.filter(m -> m.getParentId() == null)
			.map(m -> toMenuDtoWithChildren(m, groupedByParent, new HashSet<>()))
			.collect(Collectors.toList());
	}

	private MenuDto toMenuDtoWithChildren(MenuEntity entity, Map<Long, List<MenuEntity>> groupedByParent,
			Set<Long> visited) {
		MenuDto dto = toMenuDto(entity);
		if (!visited.add(entity.getMenuId())) {
			log.warn("Menu tree cycle detected at menuId={}, stopping recursion", entity.getMenuId());
			dto.setChildren(List.of());
			return dto;
		}
		List<MenuEntity> children = groupedByParent.getOrDefault(entity.getMenuId(), List.of());
		dto.setChildren(children.stream()
			.map(c -> toMenuDtoWithChildren(c, groupedByParent, visited))
			.collect(Collectors.toList()));
		return dto;
	}

	private List<UserMenuDto> buildUserMenuTree(List<MenuEntity> menus) {
		Map<Long, List<MenuEntity>> groupedByParent = menus.stream()
			.filter(m -> m.getParentId() != null)
			.collect(Collectors.groupingBy(MenuEntity::getParentId));

		return menus.stream()
			.filter(m -> m.getParentId() == null)
			.map(m -> toUserMenuDtoWithChildren(m, groupedByParent, new HashSet<>()))
			.collect(Collectors.toList());
	}

	private UserMenuDto toUserMenuDtoWithChildren(MenuEntity entity, Map<Long, List<MenuEntity>> groupedByParent,
			Set<Long> visited) {
		UserMenuDto dto = toUserMenuDto(entity);
		if (!visited.add(entity.getMenuId())) {
			log.warn("User menu tree cycle detected at menuId={}, stopping recursion", entity.getMenuId());
			dto.setChildren(List.of());
			return dto;
		}
		List<MenuEntity> children = groupedByParent.getOrDefault(entity.getMenuId(), List.of());
		dto.setChildren(children.stream()
			.map(c -> toUserMenuDtoWithChildren(c, groupedByParent, visited))
			.collect(Collectors.toList()));
		return dto;
	}

	private MenuDto toMenuDto(MenuEntity entity) {
		return MenuDto.builder()
			.menuId(entity.getMenuId())
			.parentId(entity.getParentId())
			.name(entity.getName())
			.menuType(entity.getMenuType())
			.routeName(entity.getRouteName())
			.routePath(entity.getRoutePath())
			.component(entity.getComponent())
			.permissionCode(entity.getPermissionCode())
			.icon(entity.getIcon())
			.sortOrder(entity.getSortOrder())
			.visible(entity.getVisible())
			.keepAlive(entity.getKeepAlive())
			.redirect(entity.getRedirect())
			.scope(entity.getScope())
			.children(new ArrayList<>())
			.build();
	}

	private UserMenuDto toUserMenuDto(MenuEntity entity) {
		return UserMenuDto.builder()
			.menuId(entity.getMenuId())
			.parentId(entity.getParentId())
			.name(entity.getName())
			.menuType(entity.getMenuType())
			.routeName(entity.getRouteName())
			.routePath(entity.getRoutePath())
			.component(entity.getComponent())
			.icon(entity.getIcon())
			.sortOrder(entity.getSortOrder())
			.redirect(entity.getRedirect())
			.scope(entity.getScope())
			.children(new ArrayList<>())
			.build();
	}

}
