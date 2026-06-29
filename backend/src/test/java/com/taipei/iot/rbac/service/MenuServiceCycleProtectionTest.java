package com.taipei.iot.rbac.service;

import com.taipei.iot.common.enums.ErrorCode;
import com.taipei.iot.common.exception.BusinessException;
import com.taipei.iot.rbac.dto.request.UpdateMenuRequest;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * [RBAC N-5] 驗證 MenuService 的迴圈保護： 1. updateMenu 設定 parentId 時偵測祖先迴圈 → 拋出
 * MENU_CYCLE_DETECTED 2. buildMenuTree / buildUserMenuTree 遇到已存在迴圈資料時不會無限遞迴
 */
@ExtendWith(MockitoExtension.class)
class MenuServiceCycleProtectionTest {

	@InjectMocks
	private MenuService menuService;

	@Mock
	private MenuRepository menuRepository;

	@Mock
	private PermissionRepository permissionRepository;

	// ===== updateMenu cycle detection =====

	@Test
	void updateMenu_directSelfReference_shouldThrowCycleDetected() {
		// menu 1 tries to set parentId = 1 (self-reference)
		MenuEntity entity = MenuEntity.builder()
			.menuId(1L)
			.parentId(null)
			.name("Root")
			.menuType("DIRECTORY")
			.sortOrder(1)
			.visible(true)
			.keepAlive(false)
			.build();
		when(menuRepository.findById(1L)).thenReturn(Optional.of(entity));
		when(menuRepository.findAllByOrderBySortOrder()).thenReturn(List.of(entity));

		UpdateMenuRequest req = UpdateMenuRequest.builder()
			.menuId(1L)
			.parentId(1L)
			.name("Root")
			.menuType("DIRECTORY")
			.sortOrder(1)
			.build();

		assertThatThrownBy(() -> menuService.updateMenu(req)).isInstanceOf(BusinessException.class)
			.satisfies(
					ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.MENU_CYCLE_DETECTED));
	}

	@Test
	void updateMenu_indirectCycle_shouldThrowCycleDetected() {
		// Tree: 1 → 2 → 3. Now update menu 1 to set parentId = 3 → creates cycle
		// 1→3→...→1
		MenuEntity m1 = MenuEntity.builder()
			.menuId(1L)
			.parentId(null)
			.name("M1")
			.menuType("DIRECTORY")
			.sortOrder(1)
			.visible(true)
			.keepAlive(false)
			.build();
		MenuEntity m2 = MenuEntity.builder()
			.menuId(2L)
			.parentId(1L)
			.name("M2")
			.menuType("DIRECTORY")
			.sortOrder(2)
			.visible(true)
			.keepAlive(false)
			.build();
		MenuEntity m3 = MenuEntity.builder()
			.menuId(3L)
			.parentId(2L)
			.name("M3")
			.menuType("PAGE")
			.sortOrder(3)
			.visible(true)
			.keepAlive(false)
			.build();

		when(menuRepository.findById(1L)).thenReturn(Optional.of(m1));
		when(menuRepository.findAllByOrderBySortOrder()).thenReturn(List.of(m1, m2, m3));

		UpdateMenuRequest req = UpdateMenuRequest.builder()
			.menuId(1L)
			.parentId(3L)
			.name("M1")
			.menuType("DIRECTORY")
			.sortOrder(1)
			.build();

		assertThatThrownBy(() -> menuService.updateMenu(req)).isInstanceOf(BusinessException.class)
			.satisfies(
					ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.MENU_CYCLE_DETECTED));
	}

	@Test
	void updateMenu_validParentChange_shouldSucceed() {
		// Tree: 1 → 2, 3 (root). Moving menu 2 under menu 3 — no cycle.
		MenuEntity m1 = MenuEntity.builder()
			.menuId(1L)
			.parentId(null)
			.name("M1")
			.menuType("DIRECTORY")
			.sortOrder(1)
			.visible(true)
			.keepAlive(false)
			.build();
		MenuEntity m2 = MenuEntity.builder()
			.menuId(2L)
			.parentId(1L)
			.name("M2")
			.menuType("PAGE")
			.sortOrder(2)
			.visible(true)
			.keepAlive(false)
			.build();
		MenuEntity m3 = MenuEntity.builder()
			.menuId(3L)
			.parentId(null)
			.name("M3")
			.menuType("DIRECTORY")
			.sortOrder(3)
			.visible(true)
			.keepAlive(false)
			.build();

		when(menuRepository.findById(2L)).thenReturn(Optional.of(m2));
		when(menuRepository.findAllByOrderBySortOrder()).thenReturn(List.of(m1, m2, m3));
		when(menuRepository.save(any(MenuEntity.class))).thenReturn(m2);

		UpdateMenuRequest req = UpdateMenuRequest.builder()
			.menuId(2L)
			.parentId(3L)
			.name("M2")
			.menuType("PAGE")
			.sortOrder(2)
			.build();

		// Should not throw
		menuService.updateMenu(req);

		verify(menuRepository).save(any(MenuEntity.class));
	}

	@Test
	void updateMenu_sameParentId_shouldSkipCycleCheck() {
		// parentId not changing → no cycle detection needed
		MenuEntity entity = MenuEntity.builder()
			.menuId(2L)
			.parentId(1L)
			.name("M2")
			.menuType("PAGE")
			.sortOrder(2)
			.visible(true)
			.keepAlive(false)
			.build();
		when(menuRepository.findById(2L)).thenReturn(Optional.of(entity));
		when(menuRepository.save(any(MenuEntity.class))).thenReturn(entity);

		UpdateMenuRequest req = UpdateMenuRequest.builder()
			.menuId(2L)
			.parentId(1L)
			.name("M2 updated")
			.menuType("PAGE")
			.sortOrder(2)
			.build();

		menuService.updateMenu(req);

		// findAllByOrderBySortOrder should NOT be called for cycle detection
		verify(menuRepository, never()).findAllByOrderBySortOrder();
		verify(menuRepository).save(any(MenuEntity.class));
	}

	// ===== buildMenuTree visited guard =====

	@Test
	void getMenuTree_withCyclicData_shouldNotStackOverflow() {
		// Simulate corrupt data: m1→m2→m1 (groupedByParent forms cycle)
		// Since buildMenuTree groups by parentId, a cycle in parentId chains
		// would only cause infinite recursion if a node is both root and child.
		// We simulate: m1 (parentId=null) has child m2 (parentId=1), m2 has child m1
		// (parentId=2)
		// But buildMenuTree starts with parentId==null roots, so m1 is a root.
		// groupedByParent: {1: [m2], 2: [m1]}
		// m1 → children: [m2] → m2 → children: [m1] → visited guard stops
		MenuEntity m1 = MenuEntity.builder()
			.menuId(1L)
			.parentId(null)
			.name("M1")
			.menuType("DIRECTORY")
			.sortOrder(1)
			.visible(true)
			.keepAlive(false)
			.build();
		MenuEntity m2 = MenuEntity.builder()
			.menuId(2L)
			.parentId(1L)
			.name("M2")
			.menuType("PAGE")
			.sortOrder(2)
			.visible(true)
			.keepAlive(false)
			.build();
		// Create a third entity that claims parentId=2 but has menuId=1 (simulating
		// corrupt ref)
		// Actually we can't have two entities with menuId=1. Instead let's use 3 entities
		// in a cycle:
		// m1 (root), child: m2 (parentId=1), child of m2: m3 (parentId=2), and m3 claims
		// parentId points to m1
		// But that's not a cycle in recursion because groupedByParent won't re-visit m1
		// (m1's parentId is null).
		// Real test: create duplicate entries in groupedByParent that revisit the same
		// node
		// Simplest: m1 root, m2 child of m1, and m2 ALSO a child of m2 (self-ref in
		// groupedByParent)
		MenuEntity m2dup = MenuEntity.builder()
			.menuId(2L)
			.parentId(2L)
			.name("M2")
			.menuType("PAGE")
			.sortOrder(2)
			.visible(true)
			.keepAlive(false)
			.build();

		when(menuRepository.findAllByOrderBySortOrder()).thenReturn(List.of(m1, m2, m2dup));

		// Should not throw StackOverflowError
		var result = menuService.getMenuTree();
		assertThat(result).isNotNull();
	}

	@Test
	void getMenuTree_normalHierarchy_shouldBuildCorrectly() {
		MenuEntity root = MenuEntity.builder()
			.menuId(1L)
			.parentId(null)
			.name("System")
			.menuType("DIRECTORY")
			.sortOrder(1)
			.visible(true)
			.keepAlive(false)
			.build();
		MenuEntity child = MenuEntity.builder()
			.menuId(2L)
			.parentId(1L)
			.name("Users")
			.menuType("PAGE")
			.sortOrder(2)
			.visible(true)
			.keepAlive(false)
			.build();
		MenuEntity grandchild = MenuEntity.builder()
			.menuId(3L)
			.parentId(2L)
			.name("Add User")
			.menuType("BUTTON")
			.sortOrder(3)
			.visible(true)
			.keepAlive(false)
			.build();

		when(menuRepository.findAllByOrderBySortOrder()).thenReturn(List.of(root, child, grandchild));

		var result = menuService.getMenuTree();

		assertThat(result).hasSize(1);
		assertThat(result.get(0).getName()).isEqualTo("System");
		assertThat(result.get(0).getChildren()).hasSize(1);
		assertThat(result.get(0).getChildren().get(0).getName()).isEqualTo("Users");
		assertThat(result.get(0).getChildren().get(0).getChildren()).hasSize(1);
		assertThat(result.get(0).getChildren().get(0).getChildren().get(0).getName()).isEqualTo("Add User");
	}

}
