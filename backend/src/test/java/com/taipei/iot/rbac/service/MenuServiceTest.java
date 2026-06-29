package com.taipei.iot.rbac.service;

import com.taipei.iot.common.enums.ErrorCode;
import com.taipei.iot.common.exception.BusinessException;
import com.taipei.iot.rbac.dto.request.CreateMenuRequest;
import com.taipei.iot.rbac.dto.response.MenuDto;
import com.taipei.iot.rbac.dto.response.UserMenuDto;
import com.taipei.iot.rbac.entity.MenuEntity;
import com.taipei.iot.rbac.repository.MenuRepository;
import com.taipei.iot.rbac.repository.PermissionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MenuServiceTest {

	@InjectMocks
	private MenuService menuService;

	@Mock
	private MenuRepository menuRepository;

	@Mock
	private PermissionRepository permissionRepository;

	private MenuEntity directory(Long id, String name, int sort) {
		return MenuEntity.builder()
			.menuId(id)
			.parentId(null)
			.name(name)
			.menuType("DIRECTORY")
			.sortOrder(sort)
			.visible(true)
			.keepAlive(false)
			.build();
	}

	private MenuEntity page(Long id, Long parentId, String name, String permCode, int sort) {
		return MenuEntity.builder()
			.menuId(id)
			.parentId(parentId)
			.name(name)
			.menuType("PAGE")
			.permissionCode(permCode)
			.sortOrder(sort)
			.visible(true)
			.keepAlive(false)
			.routeName(name)
			.routePath("/test")
			.component("TestView.vue")
			.build();
	}

	@Test
	void getMenuTree_shouldReturnTreeStructure() {
		MenuEntity dir = directory(1L, "System", 10);
		MenuEntity pg = page(2L, 1L, "MenuManage", "MENU_LIST", 11);

		when(menuRepository.findAllByOrderBySortOrder()).thenReturn(List.of(dir, pg));

		List<MenuDto> result = menuService.getMenuTree();

		assertEquals(1, result.size());
		assertEquals("System", result.get(0).getName());
		assertEquals(1, result.get(0).getChildren().size());
		assertEquals("MenuManage", result.get(0).getChildren().get(0).getName());
	}

	@Test
	void getMyMenus_adminRole_shouldReturnAdminMenus() {
		List<String> roleIds = List.of("ROLE_ADMIN");
		String tenantId = "TENANT_A";

		when(permissionRepository.findCodesByRoleIdsAndTenant(roleIds, tenantId))
			.thenReturn(List.of("USER_LIST", "MENU_LIST"));

		MenuEntity userDir = directory(1L, "User Management", 10);
		MenuEntity userPage = page(2L, 1L, "UserList", "USER_LIST", 11);
		MenuEntity sysDir = directory(10L, "System", 20);
		MenuEntity menuPage = page(11L, 10L, "MenuManage", "MENU_LIST", 21);

		when(menuRepository.findByScopeInAndVisibleTrue(List.of("TENANT", "PUBLIC")))
			.thenReturn(List.of(userDir, userPage, sysDir, menuPage));

		List<UserMenuDto> result = menuService.getMyMenus(roleIds, tenantId);

		assertEquals(2, result.size()); // 2 directories
	}

	@Test
	void getMyMenus_viewerRole_shouldReturnOnlyViewMenus() {
		List<String> roleIds = List.of("ROLE_VIEWER");
		String tenantId = "TENANT_A";

		when(permissionRepository.findCodesByRoleIdsAndTenant(roleIds, tenantId)).thenReturn(List.of("DEVICE_VIEW"));

		MenuEntity devicePage = page(30L, null, "DeviceView", "DEVICE_VIEW", 50);
		when(menuRepository.findByScopeInAndVisibleTrue(List.of("TENANT", "PUBLIC"))).thenReturn(List.of(devicePage));

		List<UserMenuDto> result = menuService.getMyMenus(roleIds, tenantId);

		assertEquals(1, result.size());
		assertEquals("DeviceView", result.get(0).getName());
	}

	@Test
	void getMyMenus_shouldIncludeMenusWithNullPermissionCode() {
		List<String> roleIds = List.of("ROLE_TENANT_ADMIN");
		String tenantId = "TENANT_A";

		when(permissionRepository.findCodesByRoleIdsAndTenant(roleIds, tenantId)).thenReturn(List.of("USER_LIST"));

		MenuEntity userDir = directory(1L, "User Management", 70);
		MenuEntity userPage = page(2L, 1L, "UserList", "USER_LIST", 71);
		// 公告欄: no permission code, visible to all authenticated users
		MenuEntity announcementPage = MenuEntity.builder()
			.menuId(3L)
			.parentId(null)
			.name("公告欄")
			.menuType("PAGE")
			.permissionCode(null)
			.sortOrder(0)
			.visible(true)
			.keepAlive(false)
			.routeName("Announcements")
			.routePath("/announcements")
			.component("AnnouncementView.vue")
			.build();

		when(menuRepository.findByScopeInAndVisibleTrue(List.of("TENANT", "PUBLIC")))
			.thenReturn(List.of(announcementPage, userDir, userPage));

		List<UserMenuDto> result = menuService.getMyMenus(roleIds, tenantId);

		// Should include: 公告欄 (null perm) + User Management dir + UserList page
		assertEquals(2, result.size()); // 公告欄 (top-level) + User Management dir
		assertTrue(result.stream().anyMatch(m -> "公告欄".equals(m.getName())),
				"Menu with null permissionCode should be included for all authenticated users");
	}

	@Test
	void createMenu_shouldSucceed() {
		CreateMenuRequest req = CreateMenuRequest.builder()
			.name("New Menu")
			.menuType("PAGE")
			.sortOrder(99)
			.visible(true)
			.build();

		when(menuRepository.save(any(MenuEntity.class))).thenAnswer(inv -> {
			MenuEntity e = inv.getArgument(0);
			e.setMenuId(100L);
			return e;
		});

		MenuDto result = menuService.createMenu(req);

		assertEquals("New Menu", result.getName());
		assertEquals(100L, result.getMenuId());
		verify(menuRepository).save(any());
	}

	@Test
	void deleteMenu_withChildren_shouldThrowMenuHasChildren() {
		when(menuRepository.existsById(1L)).thenReturn(true);
		when(menuRepository.existsByParentId(1L)).thenReturn(true);

		BusinessException ex = assertThrows(BusinessException.class, () -> menuService.deleteMenu(1L));
		assertEquals(ErrorCode.MENU_HAS_CHILDREN, ex.getErrorCode());
		verify(menuRepository, never()).deleteById(any());
	}

	@Test
	void deleteMenu_noChildren_shouldSucceed() {
		when(menuRepository.existsById(1L)).thenReturn(true);
		when(menuRepository.existsByParentId(1L)).thenReturn(false);

		menuService.deleteMenu(1L);

		verify(menuRepository).deleteById(1L);
	}

	@Test
	void deleteMenu_notFound_shouldThrow() {
		when(menuRepository.existsById(999L)).thenReturn(false);

		BusinessException ex = assertThrows(BusinessException.class, () -> menuService.deleteMenu(999L));
		assertEquals(ErrorCode.MENU_NOT_FOUND, ex.getErrorCode());
	}

	@Test
	void toggleVisible_shouldSucceed() {
		MenuEntity entity = MenuEntity.builder()
			.menuId(1L)
			.name("Test")
			.menuType("PAGE")
			.visible(true)
			.keepAlive(false)
			.build();
		when(menuRepository.findById(1L)).thenReturn(Optional.of(entity));
		when(menuRepository.save(any())).thenReturn(entity);

		menuService.toggleVisible(1L, false);

		assertFalse(entity.getVisible());
		verify(menuRepository).save(entity);
	}

	@Test
	void toggleVisible_notFound_shouldThrow() {
		when(menuRepository.findById(999L)).thenReturn(Optional.empty());

		BusinessException ex = assertThrows(BusinessException.class, () -> menuService.toggleVisible(999L, false));
		assertEquals(ErrorCode.MENU_NOT_FOUND, ex.getErrorCode());
	}

}
