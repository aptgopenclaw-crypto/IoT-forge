package com.taipei.iot.eventrule.controller;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taipei.iot.auth.config.SecurityConfig;
import com.taipei.iot.auth.security.JwtUtil;
import com.taipei.iot.common.config.CorsProperties;
import com.taipei.iot.common.exception.GlobalExceptionHandler;
import com.taipei.iot.eventrule.dto.EventRuleRequest;
import com.taipei.iot.eventrule.dto.EventRuleResponse;
import com.taipei.iot.eventrule.model.ActionConfig;
import com.taipei.iot.eventrule.model.ActionType;
import com.taipei.iot.eventrule.model.ConditionNode;
import com.taipei.iot.eventrule.model.ConditionOperator;
import com.taipei.iot.eventrule.model.TriggerConfig;
import com.taipei.iot.eventrule.model.TriggerMode;
import com.taipei.iot.eventrule.service.EventRuleService;
import com.taipei.iot.tenant.cache.TenantEnabledCache;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.impl.DefaultClaims;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link EventRuleController} MVC slice 測試：端點契約 + EVENT_RULE_VIEW/MANAGE 權限。
 */
@WebMvcTest({ EventRuleController.class, EventRuleTriggerLogController.class })
@Import({ SecurityConfig.class, GlobalExceptionHandler.class, CorsProperties.class })
@TestPropertySource(properties = { "cors.allowed-origins=http://localhost", "features.eventrule.enabled=true" })
class EventRuleControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@MockitoBean
	private EventRuleService eventRuleService;

	@MockitoBean
	private com.taipei.iot.eventrule.repository.EventRuleTriggerLogRepository logRepository;

	@MockitoBean
	private com.taipei.iot.eventrule.repository.EventRuleRepository eventRuleRepositoryForLog;

	@MockitoBean
	private com.taipei.iot.device.repository.DeviceRepository deviceRepository;

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

	private static ActionConfig notifyAction() {
		ActionConfig a = new ActionConfig();
		a.setType(ActionType.NOTIFY);
		a.setChannels(List.of("IN_APP"));
		return a;
	}

	private EventRuleResponse stubResponse() {
		ConditionNode cond = new ConditionNode();
		cond.setField("temperature");
		cond.setOperator(ConditionOperator.GT);
		cond.setValue(75);
		return new EventRuleResponse(1L, "DEFAULT", "RULE_TEMP_HIGH", "溫度過高告警", "STREET_LIGHT", true, "WARNING", null,
				cond, new TriggerConfig(TriggerMode.ON_MATCH, 0, 300), List.of(), null, null);
	}

	@Test
	void list_withEventRuleView_returns200() throws Exception {
		mockJwt(List.of("EVENT_RULE_VIEW"));
		when(eventRuleService.list(any(), any(), any()))
			.thenReturn(new PageImpl<>(List.of(stubResponse()), PageRequest.of(0, 20), 1));

		mockMvc.perform(get("/v1/auth/event-rules").header("Authorization", "Bearer " + TOKEN))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.errorCode").value("00000"))
			.andExpect(jsonPath("$.body.totalElements").value(1))
			.andExpect(jsonPath("$.body.content[0].ruleCode").value("RULE_TEMP_HIGH"));
	}

	@Test
	void create_withEventRuleManage_returns200() throws Exception {
		mockJwt(List.of("EVENT_RULE_MANAGE"));
		when(eventRuleService.create(any())).thenReturn(stubResponse());

		ConditionNode cond = new ConditionNode();
		cond.setField("temperature");
		cond.setOperator(ConditionOperator.GT);
		cond.setValue(75);
		EventRuleRequest req = new EventRuleRequest("RULE_TEMP_HIGH", "溫度過高告警", "STREET_LIGHT", "WARNING", null, cond,
				new TriggerConfig(TriggerMode.ON_MATCH, 0, 300), List.of(notifyAction()));

		mockMvc
			.perform(post("/v1/auth/event-rules").header("Authorization", "Bearer " + TOKEN)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(req)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.body.ruleCode").value("RULE_TEMP_HIGH"));
	}

	@Test
	void list_withoutPermission_returns403() throws Exception {
		mockJwt(List.of("DEVICE_VIEW")); // no EVENT_RULE_VIEW

		mockMvc.perform(get("/v1/auth/event-rules").header("Authorization", "Bearer " + TOKEN))
			.andExpect(status().isForbidden());
	}

	@Test
	void delete_withEventRuleManage_returns200() throws Exception {
		mockJwt(List.of("EVENT_RULE_MANAGE"));

		mockMvc.perform(delete("/v1/auth/event-rules/1").header("Authorization", "Bearer " + TOKEN))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.errorCode").value("00000"));

		verify(eventRuleService).delete(eq(1L));
	}

}
