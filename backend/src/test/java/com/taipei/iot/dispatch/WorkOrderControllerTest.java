package com.taipei.iot.dispatch;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taipei.iot.auth.security.JwtUtil;
import com.taipei.iot.common.exception.GlobalExceptionHandler;
import com.taipei.iot.common.config.CorsProperties;
import com.taipei.iot.auth.config.SecurityConfig;
import com.taipei.iot.dispatch.dto.WorkOrderRequest;
import com.taipei.iot.dispatch.dto.WorkOrderResponse;
import com.taipei.iot.dispatch.enums.WorkOrderStatus;
import com.taipei.iot.dispatch.service.WorkOrderService;
import com.taipei.iot.dispatch.controller.WorkOrderController;
import com.taipei.iot.tenant.cache.TenantEnabledCache;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.impl.DefaultClaims;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WorkOrderController.class)
@Import({ SecurityConfig.class, GlobalExceptionHandler.class, CorsProperties.class })
@TestPropertySource(properties = "cors.allowed-origins=http://localhost")
class WorkOrderControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@MockitoBean
	private WorkOrderService workOrderService;

	@MockitoBean
	private JwtUtil jwtUtil;

	@MockitoBean
	private StringRedisTemplate stringRedisTemplate;

	@MockitoBean
	private TenantEnabledCache tenantEnabledCache;

	private static final String TOKEN = "valid-token";

	private static final String AUTH_HEADER = "Bearer " + TOKEN;

	private void mockJwtValid(List<String> permissions) {
		Map<String, Object> claimsMap = new HashMap<>();
		claimsMap.put("uid", "user01");
		claimsMap.put("tenantId", "TENANT_A");
		claimsMap.put("roles", List.of("ADMIN"));
		claimsMap.put("permissions", permissions);
		claimsMap.put("sub", "user01");
		claimsMap.put("exp", new Date(System.currentTimeMillis() + 3600_000));
		claimsMap.put("iat", new Date());
		Claims claims = new DefaultClaims(claimsMap);
		when(jwtUtil.parseToken(TOKEN)).thenReturn(claims);
	}

	@Test
	void list_shouldReturnWorkOrders() throws Exception {
		mockJwtValid(List.of("WORK_ORDER_VIEW"));
		WorkOrderResponse wo = WorkOrderResponse.builder()
			.id(1L)
			.status(WorkOrderStatus.PENDING)
			.orderType("REPAIR")
			.build();
		when(workOrderService.list(any(), any(), any(), any(), any())).thenReturn(new PageImpl<>(List.of(wo)));
		mockMvc.perform(get("/v1/auth/work-orders").header("Authorization", AUTH_HEADER))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.body.content[0].status").value("PENDING"));
	}

	@Test
	void getById_shouldReturnWorkOrder() throws Exception {
		mockJwtValid(List.of("WORK_ORDER_VIEW"));
		WorkOrderResponse wo = WorkOrderResponse.builder()
			.id(1L)
			.status(WorkOrderStatus.PENDING)
			.orderType("REPAIR")
			.build();
		when(workOrderService.getById(1L)).thenReturn(wo);
		mockMvc.perform(get("/v1/auth/work-orders/1").header("Authorization", AUTH_HEADER))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.body.status").value("PENDING"));
	}

	@Test
	void create_shouldReturnWorkOrder() throws Exception {
		mockJwtValid(List.of("WORK_ORDER_MANAGE"));
		WorkOrderRequest request = WorkOrderRequest.builder()
			.deviceId(1L)
			.orderType("REPAIR")
			.sourceType("CITIZEN")
			.build();
		WorkOrderResponse response = WorkOrderResponse.builder().id(1L).status(WorkOrderStatus.PENDING).build();
		when(workOrderService.create(any())).thenReturn(response);
		mockMvc
			.perform(post("/v1/auth/work-orders").header("Authorization", AUTH_HEADER)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.body.id").value(1));
	}

	@Test
	void assign_shouldReturnAssigned() throws Exception {
		mockJwtValid(List.of("WORK_ORDER_MANAGE"));
		WorkOrderResponse response = WorkOrderResponse.builder()
			.id(1L)
			.status(WorkOrderStatus.ASSIGNED)
			.assignedTo("tech01")
			.build();
		when(workOrderService.assign(any(), any(), any())).thenReturn(response);
		mockMvc
			.perform(post("/v1/auth/work-orders/1/assign").header("Authorization", AUTH_HEADER)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"assigneeUserId\":\"tech01\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.body.status").value("ASSIGNED"));
	}

}
