package com.taipei.iot.user.controller;

import com.taipei.iot.common.response.BaseResponse;
import com.taipei.iot.user.dto.request.AddTenantRoleRequest;
import com.taipei.iot.user.dto.response.UserTenantMappingDto;
import com.taipei.iot.user.service.UserAdminService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * [2.1.3] Unit test for {@link PlatformUserTenantMappingController}: verifies the
 * controller delegates to {@link UserAdminService} using the {@code userId} path variable
 * and that class-level routing / authorisation annotations are set correctly.
 */
@ExtendWith(MockitoExtension.class)
class PlatformUserTenantMappingControllerTest {

	@Mock
	private UserAdminService userAdminService;

	@InjectMocks
	private PlatformUserTenantMappingController controller;

	private static final String ADMIN_ID = "11111111-1111-1111-1111-111111111111";

	private static final String USER_ID = "22222222-2222-2222-2222-222222222222";

	@Test
	void classLevel_routesUnderPlatformUsersPath() {
		RequestMapping rm = PlatformUserTenantMappingController.class.getAnnotation(RequestMapping.class);
		assertThat(rm).isNotNull();
		assertThat(rm.value()).containsExactly("/v1/platform/users/{userId}/tenant-roles");
	}

	@Test
	void classLevel_requiresPlatformUserTenantMappingPermission() {
		PreAuthorize pa = PlatformUserTenantMappingController.class.getAnnotation(PreAuthorize.class);
		assertThat(pa).isNotNull();
		assertThat(pa.value()).isEqualTo("hasAuthority('PLATFORM_USER_TENANT_MAPPING')");
	}

	@Test
	void list_delegatesWithPathVariableUserId() {
		List<UserTenantMappingDto> expected = List.of(UserTenantMappingDto.builder().build());
		when(userAdminService.getUserTenantMappings(USER_ID)).thenReturn(expected);

		BaseResponse<List<UserTenantMappingDto>> resp = controller.list(USER_ID);

		assertThat(resp.getBody()).isSameAs(expected);
		verify(userAdminService).getUserTenantMappings(USER_ID);
	}

	@Test
	void add_delegatesWithAdminIdFromAuthenticationAndPathUserId() {
		Authentication auth = mock(Authentication.class);
		when(auth.getPrincipal()).thenReturn(ADMIN_ID);
		AddTenantRoleRequest req = AddTenantRoleRequest.builder().build();
		UserTenantMappingDto expected = UserTenantMappingDto.builder().build();
		when(userAdminService.addTenantRole(ADMIN_ID, USER_ID, req)).thenReturn(expected);

		BaseResponse<UserTenantMappingDto> resp = controller.add(auth, USER_ID, req);

		assertThat(resp.getBody()).isSameAs(expected);
		verify(userAdminService).addTenantRole(ADMIN_ID, USER_ID, req);
	}

	@Test
	void remove_delegatesWithAdminIdFromAuthenticationAndPathParams() {
		Authentication auth = mock(Authentication.class);
		when(auth.getPrincipal()).thenReturn(ADMIN_ID);
		Long mappingId = 42L;

		BaseResponse<Void> resp = controller.remove(auth, USER_ID, mappingId);

		assertThat(resp.getBody()).isNull();
		verify(userAdminService).removeTenantRole(ADMIN_ID, USER_ID, mappingId);
	}

}
