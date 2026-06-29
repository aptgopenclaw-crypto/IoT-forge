package com.taipei.iot.rbac.service;

import com.taipei.iot.rbac.entity.MenuEntity;
import com.taipei.iot.rbac.repository.MenuRepository;
import com.taipei.iot.rbac.repository.PermissionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * [RBAC N-2] 驗證 MenuService 已移除 in-process cache，每次呼叫均走 DB。 方案 A：選單資料量小（< 200
 * 筆）且僅登入後拉一次，無需本機快取； 移除後多實例部署不再有漂移問題。
 */
@ExtendWith(MockitoExtension.class)
class MenuServiceCacheTest {

	@InjectMocks
	private MenuService menuService;

	@Mock
	private MenuRepository menuRepository;

	@Mock
	private PermissionRepository permissionRepository;

	private List<MenuEntity> sampleMenus() {
		return List.of(MenuEntity.builder()
			.menuId(1L)
			.name("System")
			.menuType("DIRECTORY")
			.parentId(null)
			.sortOrder(10)
			.visible(true)
			.keepAlive(false)
			.build());
	}

	@Test
	void getMenuTree_shouldQueryDbEveryCall_noInProcessCache() {
		when(menuRepository.findAllByOrderBySortOrder()).thenReturn(sampleMenus());

		menuService.getMenuTree();
		menuService.getMenuTree();
		menuService.getMenuTree();

		verify(menuRepository, times(3)).findAllByOrderBySortOrder();
	}

	@Test
	void menuService_shouldNotHaveVolatileCacheField() {
		boolean hasCacheField = Arrays.stream(MenuService.class.getDeclaredFields())
			.anyMatch(f -> f.getName().contains("cache") || f.getName().contains("Cache"));

		assertThat(hasCacheField).as("MenuService should not have any in-process cache field (N-2 Method A)").isFalse();
	}

	@Test
	void menuService_shouldNotHaveInvalidateMethod() {
		boolean hasInvalidateMethod = Arrays.stream(MenuService.class.getDeclaredMethods())
			.anyMatch(m -> m.getName().contains("invalidate") || m.getName().contains("Invalidate"));

		assertThat(hasInvalidateMethod).as("MenuService should not have cache invalidation method (N-2 Method A)")
			.isFalse();
	}

	@Test
	void getMenuTree_afterUpdate_shouldReturnFreshData() {
		List<MenuEntity> oldMenus = List.of(MenuEntity.builder()
			.menuId(1L)
			.name("Old")
			.menuType("DIRECTORY")
			.parentId(null)
			.sortOrder(10)
			.visible(true)
			.keepAlive(false)
			.build());
		List<MenuEntity> newMenus = List.of(MenuEntity.builder()
			.menuId(1L)
			.name("New")
			.menuType("DIRECTORY")
			.parentId(null)
			.sortOrder(10)
			.visible(true)
			.keepAlive(false)
			.build());
		when(menuRepository.findAllByOrderBySortOrder()).thenReturn(oldMenus, newMenus);

		var first = menuService.getMenuTree();
		var second = menuService.getMenuTree();

		assertThat(first.get(0).getName()).isEqualTo("Old");
		assertThat(second.get(0).getName()).isEqualTo("New");
	}

}
