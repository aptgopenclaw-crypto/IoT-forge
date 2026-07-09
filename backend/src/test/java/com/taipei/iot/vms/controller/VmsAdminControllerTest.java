package com.taipei.iot.vms.controller;

import com.taipei.iot.auth.config.SecurityConfig;
import com.taipei.iot.auth.security.JwtUtil;
import com.taipei.iot.common.config.CorsProperties;
import com.taipei.iot.common.exception.GlobalExceptionHandler;
import com.taipei.iot.vms.dto.VmsServerResponse;
import com.taipei.iot.vms.enums.VmsAuthType;
import com.taipei.iot.vms.enums.VmsType;
import com.taipei.iot.vms.service.VmsAdminService;
import com.taipei.iot.tenant.cache.TenantEnabledCache;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.impl.DefaultClaims;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link VmsAdminController} MVC slice 測試。
 */
@WebMvcTest(VmsAdminController.class)
@Import({ SecurityConfig.class, GlobalExceptionHandler.class, CorsProperties.class })
@TestPropertySource(properties = "cors.allowed-origins=http://localhost")
class VmsAdminControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private VmsAdminService vmsAdminService;

	@MockitoBean
	private JwtUtil jwtUtil;

	@MockitoBean
	private StringRedisTemplate stringRedisTemplate;

	@MockitoBean
	private TenantEnabledCache tenantEnabledCache;

	private static final String TOKEN = "valid.jwt.token";

	private void mockJwt(List<String> permissions) {
		Map<String, Object> claims = new HashMap<>();
		claims.put("uid", "user-1");
		claims.put("tenantId", "DEFAULT");
		claims.put("roles", List.of("ADMIN"));
		claims.put("permissions", permissions);
		claims.put("deptId", "1");
		claims.put("dataScope", "ALL");
		claims.put("sub", "admin@test.com");
		claims.put("exp", new Date(System.currentTimeMillis() + 3600000));
		claims.put("iat", new Date());
		Claims c = new DefaultClaims(claims);
		when(jwtUtil.parseToken(TOKEN)).thenReturn(c);
	}

	@Test
	void listServers_withVmsManage_returns200() throws Exception {
		mockJwt(List.of("VMS_MANAGE"));
		when(vmsAdminService.listServers()).thenReturn(List
			.of(new VmsServerResponse(1L, "Nx", VmsType.NX_WITNESS, "http://nx:7001", VmsAuthType.BASIC, true, null)));

		mockMvc.perform(get("/v1/auth/vms/servers").header("Authorization", "Bearer " + TOKEN))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.body[0].name").value("Nx"));
	}

	@Test
	void createServer_withVmsManage_returns200() throws Exception {
		mockJwt(List.of("VMS_MANAGE"));
		when(vmsAdminService.createServer(any())).thenReturn(
				new VmsServerResponse(1L, "New", VmsType.NX_WITNESS, "http://new", VmsAuthType.BASIC, true, null));

		mockMvc
			.perform(post("/v1/auth/vms/servers").header("Authorization", "Bearer " + TOKEN)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name":"New Server","vmsType":"NX_WITNESS","baseUrl":"http://new:7001"}
						"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.body.name").value("New"));
	}

	@Test
	void createServer_missingName_returns400() throws Exception {
		mockJwt(List.of("VMS_MANAGE"));

		mockMvc
			.perform(post("/v1/auth/vms/servers").header("Authorization", "Bearer " + TOKEN)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"vmsType":"NX_WITNESS","baseUrl":"http://new"}
						"""))
			.andExpect(status().isBadRequest());
	}

	@Test
	void deleteServer_withVmsManage_returns200() throws Exception {
		mockJwt(List.of("VMS_MANAGE"));

		mockMvc.perform(delete("/v1/auth/vms/servers/1").header("Authorization", "Bearer " + TOKEN))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.errorCode").value("00000"));

		verify(vmsAdminService).deleteServer(1L);
	}

	@Test
	void testConnection_withVmsManage_returns200() throws Exception {
		mockJwt(List.of("VMS_MANAGE"));
		when(vmsAdminService.testConnection(1L)).thenReturn(
				new VmsServerResponse(1L, "Nx", VmsType.NX_WITNESS, "http://nx", VmsAuthType.BASIC, true, null));

		mockMvc.perform(post("/v1/auth/vms/servers/1/test-connection").header("Authorization", "Bearer " + TOKEN))
			.andExpect(status().isOk());
	}

	@Test
	void adminEndpoints_withoutPermission_returns403() throws Exception {
		mockJwt(List.of("VMS_VIEW"));

		mockMvc.perform(get("/v1/auth/vms/servers").header("Authorization", "Bearer " + TOKEN))
			.andExpect(status().isForbidden());

		mockMvc
			.perform(post("/v1/auth/vms/servers").header("Authorization", "Bearer " + TOKEN)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name":"Test","vmsType":"NX_WITNESS","baseUrl":"http://t"}
						"""))
			.andExpect(status().isForbidden());
	}

}
