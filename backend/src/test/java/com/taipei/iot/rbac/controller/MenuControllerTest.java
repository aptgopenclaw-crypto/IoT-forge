package com.taipei.iot.rbac.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taipei.iot.auth.security.JwtAuthenticationFilter;
import com.taipei.iot.auth.security.JwtUtil;
import com.taipei.iot.common.exception.GlobalExceptionHandler;
import com.taipei.iot.config.CorsProperties;
import com.taipei.iot.config.SecurityConfig;
import com.taipei.iot.rbac.dto.request.CreateMenuRequest;
import com.taipei.iot.rbac.dto.response.MenuDto;
import com.taipei.iot.rbac.dto.response.UserMenuDto;
import com.taipei.iot.rbac.service.MenuService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.impl.DefaultClaims;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.taipei.iot.common.interceptor.RateLimitInterceptor;
import com.taipei.iot.tenant.TenantInterceptor;
import com.taipei.iot.tenant.TenantEnabledCache;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(MenuController.class)
@Import({ SecurityConfig.class, GlobalExceptionHandler.class, CorsProperties.class })
@TestPropertySource(properties = "cors.allowed-origins=http://localhost")
class MenuControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@MockitoBean
	private MenuService menuService;

	@MockitoBean
	private JwtUtil jwtUtil;

	@MockitoBean
	private StringRedisTemplate stringRedisTemplate;

	@MockitoBean
	private TenantEnabledCache tenantEnabledCache;

	private String validToken() {
		return "valid.jwt.token";
	}

	private void mockJwtValid(String token, String userId, String tenantId, List<String> roles) {
		mockJwtValid(token, userId, tenantId, roles, List.of());
	}

	private void mockJwtValid(String token, String userId, String tenantId, List<String> roles,
			List<String> permissions) {
		Map<String, Object> claimsMap = new HashMap<>();
		claimsMap.put("uid", userId);
		claimsMap.put("tenantId", tenantId);
		claimsMap.put("roles", roles);
		claimsMap.put("permissions", permissions);
		claimsMap.put("sub", "test@test.com");
		claimsMap.put("exp", new Date(System.currentTimeMillis() + 3600000));
		claimsMap.put("iat", new Date());
		Claims claims = new DefaultClaims(claimsMap);
		when(jwtUtil.parseToken(token)).thenReturn(claims);
	}

	@Test
	void createMenu_superAdmin_shouldReturn200() throws Exception {
		mockJwtValid(validToken(), "user-super-001", "TENANT_A", List.of("SUPER_ADMIN"),
				List.of("MENU_LIST", "MENU_CREATE", "MENU_UPDATE", "MENU_DELETE"));

		CreateMenuRequest request = CreateMenuRequest.builder()
			.name("New Menu")
			.menuType("PAGE")
			.sortOrder(10)
			.visible(true)
			.build();

		MenuDto response = MenuDto.builder()
			.menuId(100L)
			.name("New Menu")
			.menuType("PAGE")
			.sortOrder(10)
			.visible(true)
			.build();
		when(menuService.createMenu(any())).thenReturn(response);

		mockMvc
			.perform(post("/v1/auth/menus").header("Authorization", "Bearer " + validToken())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.errorCode").value("00000"))
			.andExpect(jsonPath("$.body.menuId").value(100));
	}

	@Test
	void createMenu_adminRole_shouldReturn403() throws Exception {
		mockJwtValid(validToken(), "user-admin-001", "TENANT_A", List.of("ADMIN"));

		CreateMenuRequest request = CreateMenuRequest.builder()
			.name("New Menu")
			.menuType("PAGE")
			.sortOrder(10)
			.visible(true)
			.build();

		mockMvc
			.perform(post("/v1/auth/menus").header("Authorization", "Bearer " + validToken())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isForbidden());
	}

	@Test
	void getMyMenus_authenticated_shouldReturn200() throws Exception {
		mockJwtValid(validToken(), "user-viewer-001", "TENANT_A", List.of("VIEWER"));

		List<UserMenuDto> menus = List
			.of(UserMenuDto.builder().menuId(1L).name("Device").menuType("PAGE").sortOrder(10).build());
		when(menuService.getMyMenus(anyList(), anyString())).thenReturn(menus);

		mockMvc.perform(get("/v1/auth/menus/my").header("Authorization", "Bearer " + validToken()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.errorCode").value("00000"))
			.andExpect(jsonPath("$.body[0].menuId").value(1));
	}

}
