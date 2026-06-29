package com.taipei.iot.dept.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taipei.iot.auth.security.JwtUtil;
import com.taipei.iot.common.enums.ErrorCode;
import com.taipei.iot.common.exception.BusinessException;
import com.taipei.iot.common.exception.GlobalExceptionHandler;
import com.taipei.iot.config.CorsProperties;
import com.taipei.iot.config.SecurityConfig;
import com.taipei.iot.dept.dto.CreateDeptRequest;
import com.taipei.iot.dept.dto.DeptDto;
import com.taipei.iot.dept.dto.DeptOptionVO;
import com.taipei.iot.dept.dto.UpdateDeptRequest;
import com.taipei.iot.dept.service.DeptService;
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

@WebMvcTest(DeptController.class)
@Import({ SecurityConfig.class, GlobalExceptionHandler.class, CorsProperties.class })
@TestPropertySource(properties = "cors.allowed-origins=http://localhost")
class DeptControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@MockitoBean
	private DeptService deptService;

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
		claimsMap.put("deptId", "1");
		claimsMap.put("dataScope", "ALL");
		claimsMap.put("sub", "test@test.com");
		claimsMap.put("exp", new Date(System.currentTimeMillis() + 3600000));
		claimsMap.put("iat", new Date());
		Claims claims = new DefaultClaims(claimsMap);
		when(jwtUtil.parseToken(token)).thenReturn(claims);
	}

	@Test
	void getDeptTree_authenticated_shouldReturn200() throws Exception {
		mockJwtValid(validToken(), "user-admin-001", "TENANT_A", List.of("ADMIN"),
				List.of("DEPT_LIST", "DEPT_CREATE", "DEPT_UPDATE", "DEPT_DELETE"));

		DeptDto root = DeptDto.builder().id(1L).deptName("總公司").build();
		when(deptService.getDeptTree()).thenReturn(List.of(root));

		mockMvc.perform(get("/v1/auth/dept/list").header("Authorization", "Bearer " + validToken()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.errorCode").value("00000"))
			.andExpect(jsonPath("$.body[0].deptName").value("總公司"));
	}

	@Test
	void getDeptTree_noToken_shouldReturn401() throws Exception {
		mockMvc.perform(get("/v1/auth/dept/list")).andExpect(status().isUnauthorized());
	}

	@Test
	void getDeptOptions_authenticated_shouldReturn200() throws Exception {
		mockJwtValid(validToken(), "user-001", "TENANT_A", List.of("VIEWER"));

		DeptOptionVO option = DeptOptionVO.builder().value(1L).label("總公司").build();
		when(deptService.getDeptOptions()).thenReturn(List.of(option));

		mockMvc.perform(get("/v1/auth/dept/options").header("Authorization", "Bearer " + validToken()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.errorCode").value("00000"))
			.andExpect(jsonPath("$.body[0].label").value("總公司"));
	}

	@Test
	void getDeptById_authenticated_shouldReturn200() throws Exception {
		mockJwtValid(validToken(), "user-001", "TENANT_A", List.of("ADMIN"));

		DeptDto dept = DeptDto.builder().id(1L).deptName("總公司").status((short) 1).build();
		when(deptService.getDeptById(1L)).thenReturn(dept);

		mockMvc.perform(get("/v1/auth/dept/1").header("Authorization", "Bearer " + validToken()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.body.id").value(1))
			.andExpect(jsonPath("$.body.deptName").value("總公司"));
	}

	@Test
	void getDeptById_notFound_shouldReturn404() throws Exception {
		mockJwtValid(validToken(), "user-001", "TENANT_A", List.of("ADMIN"));

		when(deptService.getDeptById(999L)).thenThrow(new BusinessException(ErrorCode.DEPT_NOT_FOUND));

		mockMvc.perform(get("/v1/auth/dept/999").header("Authorization", "Bearer " + validToken()))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.errorCode").value("40001"));
	}

	@Test
	void createDept_adminRole_shouldReturn200() throws Exception {
		mockJwtValid(validToken(), "user-admin-001", "TENANT_A", List.of("ADMIN"),
				List.of("DEPT_CREATE", "DEPT_UPDATE", "DEPT_DELETE"));

		CreateDeptRequest request = CreateDeptRequest.builder().deptName("新部門").pid(1L).deptSort(5).build();

		DeptDto response = DeptDto.builder().id(10L).deptName("新部門").pid(1L).build();
		when(deptService.createDept(any())).thenReturn(response);

		mockMvc
			.perform(post("/v1/auth/dept").header("Authorization", "Bearer " + validToken())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.errorCode").value("00000"))
			.andExpect(jsonPath("$.body.id").value(10));
	}

	@Test
	void createDept_viewerRole_shouldReturn403() throws Exception {
		mockJwtValid(validToken(), "user-viewer-001", "TENANT_A", List.of("VIEWER"));

		CreateDeptRequest request = CreateDeptRequest.builder().deptName("新部門").pid(1L).deptSort(5).build();

		mockMvc
			.perform(post("/v1/auth/dept").header("Authorization", "Bearer " + validToken())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isForbidden());
	}

	@Test
	void updateDept_adminRole_shouldReturn200() throws Exception {
		mockJwtValid(validToken(), "user-admin-001", "TENANT_A", List.of("ADMIN"),
				List.of("DEPT_CREATE", "DEPT_UPDATE", "DEPT_DELETE"));

		UpdateDeptRequest request = UpdateDeptRequest.builder().deptId(2L).deptName("新名稱").deptSort(10).build();

		DeptDto response = DeptDto.builder().id(2L).deptName("新名稱").deptSort(10).build();
		when(deptService.updateDept(any())).thenReturn(response);

		mockMvc
			.perform(put("/v1/auth/dept").header("Authorization", "Bearer " + validToken())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.body.deptName").value("新名稱"));
	}

	@Test
	void deleteDept_adminRole_shouldReturn200() throws Exception {
		mockJwtValid(validToken(), "user-admin-001", "TENANT_A", List.of("ADMIN"),
				List.of("DEPT_CREATE", "DEPT_UPDATE", "DEPT_DELETE"));

		doNothing().when(deptService).deleteDept(2L);

		mockMvc.perform(delete("/v1/auth/dept/2").header("Authorization", "Bearer " + validToken()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.errorCode").value("00000"));
	}

	@Test
	void deleteDept_noToken_shouldReturn401() throws Exception {
		mockMvc.perform(delete("/v1/auth/dept/2")).andExpect(status().isUnauthorized());
	}

}
