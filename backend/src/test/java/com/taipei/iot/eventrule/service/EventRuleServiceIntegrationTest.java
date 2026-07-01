package com.taipei.iot.eventrule.service;

import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.taipei.iot.common.context.TenantContext;
import com.taipei.iot.common.exception.BusinessException;
import com.taipei.iot.common.schema.port.SchemaProviderPort;
import com.taipei.iot.device.entity.Device;
import com.taipei.iot.device.enums.DeviceStatus;
import com.taipei.iot.device.repository.DeviceRepository;
import com.taipei.iot.eventrule.dto.EventRuleRequest;
import com.taipei.iot.eventrule.dto.EventRuleResponse;
import com.taipei.iot.eventrule.model.ConditionNode;
import com.taipei.iot.eventrule.model.ConditionOperator;
import com.taipei.iot.eventrule.model.TriggerConfig;
import com.taipei.iot.eventrule.model.TriggerMode;
import com.taipei.iot.eventrule.repository.EventRuleRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * {@link EventRuleService} 整合測試（打真 PG）：CRUD、tenant 隔離、schema 白名單驗證。
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@Tag("integration")
class EventRuleServiceIntegrationTest {

	@Autowired
	private EventRuleService eventRuleService;

	@Autowired
	private EventRuleRepository eventRuleRepository;

	@Autowired
	private DeviceRepository deviceRepository;

	@MockitoBean
	private SchemaProviderPort schemaProviderPort;

	private static final String TENANT = "DEFAULT";

	@BeforeEach
	void setup() {
		TenantContext.setCurrentTenantId(TENANT);
		// schema: allow field "temperature"
		ObjectNode schema = JsonNodeFactory.instance.objectNode();
		ObjectNode properties = schema.putObject("properties");
		properties.putObject("temperature");
		when(schemaProviderPort.getTelemetrySchema(anyString())).thenReturn(Optional.of(schema));
	}

	@AfterEach
	void cleanup() {
		TenantContext.clear();
	}

	private EventRuleRequest buildRequest() {
		ConditionNode cond = new ConditionNode();
		cond.setField("temperature");
		cond.setOperator(ConditionOperator.GT);
		cond.setValue(75);
		return new EventRuleRequest("RULE_TEMP_HIGH_" + System.nanoTime(), "溫度過高", "STREET_LIGHT", "WARNING", null,
				cond, new TriggerConfig(TriggerMode.ON_MATCH, 0, 300), List.of());
	}

	@Test
	void create_persistsAndReturns() {
		EventRuleRequest req = buildRequest();
		EventRuleResponse resp = eventRuleService.create(req);

		assertThat(resp.id()).isNotNull();
		assertThat(resp.ruleCode()).isEqualTo(req.ruleCode());
		assertThat(resp.enabled()).isTrue();
	}

	@Test
	void list_returnsOwnTenantRules() {
		eventRuleService.create(buildRequest());
		eventRuleService.create(buildRequest());

		Page<EventRuleResponse> page = eventRuleService.list(null, null, PageRequest.of(0, 20));
		assertThat(page.getTotalElements()).isGreaterThanOrEqualTo(2);
		assertThat(page.getContent()).allMatch(r -> TENANT.equals(r.tenantId()));
	}

	@Test
	void toggleEnabled_changesEnabledFlag() {
		EventRuleResponse created = eventRuleService.create(buildRequest());
		assertThat(created.enabled()).isTrue();

		EventRuleResponse toggled = eventRuleService.toggleEnabled(created.id(), false);
		assertThat(toggled.enabled()).isFalse();
	}

	@Test
	void delete_removesRule() {
		EventRuleResponse created = eventRuleService.create(buildRequest());
		eventRuleService.delete(created.id());

		assertThat(eventRuleRepository.findById(created.id())).isEmpty();
	}

	@Test
	void create_withNonExistentField_throwsBusinessException() {
		// schema only has "temperature", try to create with "no_such_field"
		ConditionNode badCond = new ConditionNode();
		badCond.setField("no_such_field");
		badCond.setOperator(ConditionOperator.GT);
		badCond.setValue(75);
		EventRuleRequest req = new EventRuleRequest("RULE_BAD_FIELD", "bad", "STREET_LIGHT", "INFO", null, badCond,
				new TriggerConfig(TriggerMode.ON_MATCH, 0, 0), List.of());

		assertThatThrownBy(() -> eventRuleService.create(req)).isInstanceOf(BusinessException.class);
	}

	@Test
	void create_duplicateRuleCode_throwsBusinessException() {
		EventRuleRequest req = buildRequest();
		eventRuleService.create(req);

		assertThatThrownBy(() -> eventRuleService.create(req)).isInstanceOf(BusinessException.class);
	}

}
