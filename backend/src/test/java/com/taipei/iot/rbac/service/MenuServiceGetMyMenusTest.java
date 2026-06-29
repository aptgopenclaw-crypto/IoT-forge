package com.taipei.iot.rbac.service;

import com.taipei.iot.rbac.entity.MenuEntity;
import com.taipei.iot.rbac.repository.MenuRepository;
import com.taipei.iot.rbac.repository.PermissionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * [RBAC N-3] 驗證 getMyMenus() 使用單一 JOIN 查詢取得 permission codes。 [Phase B] 驗證 scope-aware
 * 行為：SUPER_ADMIN(SYSTEM)→PLATFORM+PUBLIC、
 * SUPER_ADMIN(tenant)→TENANT+PUBLIC、一般使用者→TENANT+PUBLIC。
 */
@ExtendWith(MockitoExtension.class)
class MenuServiceGetMyMenusTest {

	@InjectMocks
	private MenuService menuService;

	@Mock
	private MenuRepository menuRepository;

	@Mock
	private PermissionRepository permissionRepository;

	private MenuEntity dir(Long id, String name) {
		return MenuEntity.builder()
			.menuId(id)
			.name(name)
			.menuType("DIRECTORY")
			.parentId(null)
			.sortOrder(1)
			.visible(true)
			.keepAlive(false)
			.build();
	}

	private MenuEntity page(Long id, Long parentId, String name, String permCode) {
		return MenuEntity.builder()
			.menuId(id)
			.name(name)
			.menuType("PAGE")
			.parentId(parentId)
			.permissionCode(permCode)
			.sortOrder(1)
			.visible(true)
			.keepAlive(false)
			.build();
	}

	@Test
	void getMyMenus_shouldUseSingleJoinQuery() {
		List<String> roleIds = List.of("ROLE_A", "ROLE_B");
		String tenantId = "T1";

		when(permissionRepository.findCodesByRoleIdsAndTenant(roleIds, tenantId))
			.thenReturn(List.of("menu:read", "user:list"));
		when(menuRepository.findByScopeInAndVisibleTrue(List.of("TENANT", "PUBLIC")))
			.thenReturn(List.of(dir(1L, "System"), page(10L, 1L, "Menu Mgmt", "menu:read")));

		var result = menuService.getMyMenus(roleIds, tenantId);

		verify(permissionRepository).findCodesByRoleIdsAndTenant(roleIds, tenantId);
		verify(permissionRepository, never()).findAllById(any());
		assertThat(result).isNotEmpty();
	}

	@Test
	void getMyMenus_withEmptyPermissionCodes_shouldReturnEmptyList() {
		List<String> roleIds = List.of("ROLE_NO_PERMS");
		String tenantId = "T1";

		when(permissionRepository.findCodesByRoleIdsAndTenant(roleIds, tenantId)).thenReturn(List.of());

		var result = menuService.getMyMenus(roleIds, tenantId);

		assertThat(result).isEmpty();
		verify(menuRepository, never()).findByScopeInAndVisibleTrue(any());
	}

	@Test
	void getMyMenus_superAdmin_systemContext_shouldReturnPlatformScope() {
		// [Phase 3 / 3.1.3] super_admin 一律走 permission 過濾路徑（無 bypass）。
		// 預期 V65 種入 PLATFORM_TENANT_MANAGE 等 4 個 PLATFORM_* perms，
		// 在 SYSTEM context 下取得 PLATFORM + PUBLIC scope 中對應 menu。
		List<String> roleIds = List.of("ROLE_SUPER_ADMIN");
		String tenantId = null; // SYSTEM context

		when(permissionRepository.findCodesByRoleIdsAndTenant(roleIds, null))
			.thenReturn(List.of("PLATFORM_TENANT_MANAGE"));
		when(menuRepository.findByScopeInAndVisibleTrue(List.of("PLATFORM", "PUBLIC")))
			.thenReturn(List.of(dir(1L, "Platform Admin"), page(10L, 1L, "Tenant Mgmt", "PLATFORM_TENANT_MANAGE")));

		var result = menuService.getMyMenus(roleIds, tenantId);

		// 必須走 permission 查詢（不再 bypass）
		verify(permissionRepository).findCodesByRoleIdsAndTenant(roleIds, null);
		verify(menuRepository, never()).findByScopeInAndVisibleTrue(List.of("TENANT", "PUBLIC"));
		assertThat(result).hasSize(1);
		assertThat(result.get(0).getName()).isEqualTo("Platform Admin");
		assertThat(result.get(0).getChildren()).hasSize(1);
		assertThat(result.get(0).getChildren().get(0).getName()).isEqualTo("Tenant Mgmt");
	}

	@Test
	void getMyMenus_superAdmin_tenantContext_shouldReturnTenantScope() {
		// [Phase B] SUPER_ADMIN 進入 tenant context（impersonating）→ 看到該租戶的 TENANT scope 選單
		// [Phase 3 / 3.1.3] 一樣走 permission 過濾；impersonation token 在 3.1.5 會帶
		// 目標租戶 ROLE_ADMIN 的 perms。
		List<String> roleIds = List.of("ROLE_SUPER_ADMIN");
		String tenantId = "T1";

		when(permissionRepository.findCodesByRoleIdsAndTenant(roleIds, tenantId)).thenReturn(List.of("USER_LIST"));
		when(menuRepository.findByScopeInAndVisibleTrue(List.of("TENANT", "PUBLIC")))
			.thenReturn(List.of(dir(2L, "Tenant Admin"), page(20L, 2L, "Users", "USER_LIST")));

		var result = menuService.getMyMenus(roleIds, tenantId);

		verify(permissionRepository).findCodesByRoleIdsAndTenant(roleIds, tenantId);
		assertThat(result).hasSize(1);
		assertThat(result.get(0).getName()).isEqualTo("Tenant Admin");
	}

	/**
	 * [Phase 3 / 3.1.3] 驗證 bypass 已徹底移除：當 super_admin 在 DB 沒有任何 permission 綁定（理論上不應發生 —
	 * V65 必須先跑），getMyMenus 不會再回傳 所有 PLATFORM scope menu，而是回空集合。
	 */
	@Test
	void getMyMenus_superAdmin_withNoPermissions_returnsEmpty_noBypass() {
		List<String> roleIds = List.of("ROLE_SUPER_ADMIN");
		when(permissionRepository.findCodesByRoleIdsAndTenant(roleIds, null)).thenReturn(List.of());

		var result = menuService.getMyMenus(roleIds, null);

		assertThat(result).isEmpty();
		verify(menuRepository, never()).findByScopeInAndVisibleTrue(any());
	}

	@Test
	void getMyMenus_shouldIncludeParentDirectories() {
		List<String> roleIds = List.of("ROLE_A");
		String tenantId = "T1";

		MenuEntity parentDir = dir(1L, "System");
		MenuEntity childPage = page(10L, 1L, "Users", "user:list");

		when(permissionRepository.findCodesByRoleIdsAndTenant(roleIds, tenantId)).thenReturn(List.of("user:list"));
		when(menuRepository.findByScopeInAndVisibleTrue(List.of("TENANT", "PUBLIC")))
			.thenReturn(List.of(parentDir, childPage));

		var result = menuService.getMyMenus(roleIds, tenantId);

		assertThat(result).hasSize(1);
		assertThat(result.get(0).getName()).isEqualTo("System");
		assertThat(result.get(0).getChildren()).hasSize(1);
		assertThat(result.get(0).getChildren().get(0).getName()).isEqualTo("Users");
	}

}
