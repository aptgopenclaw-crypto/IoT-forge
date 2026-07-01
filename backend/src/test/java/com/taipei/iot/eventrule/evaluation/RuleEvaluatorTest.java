package com.taipei.iot.eventrule.evaluation;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.taipei.iot.eventrule.action.RuleActionDispatcher;
import com.taipei.iot.eventrule.entity.EventRule;
import com.taipei.iot.eventrule.model.ConditionNode;
import com.taipei.iot.eventrule.model.ConditionOperator;
import com.taipei.iot.eventrule.model.TriggerConfig;
import com.taipei.iot.eventrule.model.TriggerMode;
import com.taipei.iot.eventrule.repository.EventRuleTriggerLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link RuleEvaluator} 單元測試：ON_MATCH / FOR_DURATION / ON_CHANGE / cooldown。
 */
@ExtendWith(MockitoExtension.class)
class RuleEvaluatorTest {

	@Mock
	private RuleStateStore stateStore;

	@Mock
	private RuleActionDispatcher actionDispatcher;

	@Mock
	private EventRuleTriggerLogRepository triggerLogRepository;

	private RuleEvaluator ruleEvaluator;

	private static final String TENANT = "DEFAULT";

	private static final Long DEVICE_ID = 42L;

	private static final Map<String, Object> HIGH_TEMP = Map.of("temperature", 80.0);

	private static final Map<String, Object> LOW_TEMP = Map.of("temperature", 60.0);

	@BeforeEach
	void setup() {
		ruleEvaluator = new RuleEvaluator(new ConditionEvaluator(), stateStore, actionDispatcher, triggerLogRepository);
	}

	private EventRule buildRule(TriggerMode mode, int durationSec, int cooldownSec) {
		ConditionNode condition = new ConditionNode();
		condition.setField("temperature");
		condition.setOperator(ConditionOperator.GT);
		condition.setValue(75);
		return EventRule.builder()
			.id(1L)
			.tenantId(TENANT)
			.ruleCode("RULE_TEMP_HIGH")
			.name("溫度過高")
			.deviceType("STREET_LIGHT")
			.severity("WARNING")
			.enabled(true)
			.condition(condition)
			.triggerCfg(new TriggerConfig(mode, durationSec, cooldownSec))
			.actions(List.of())
			.build();
	}

	@Test
	void onMatch_conditionTrue_notInCooldown_fires() {
		EventRule rule = buildRule(TriggerMode.ON_MATCH, 0, 0);
		when(stateStore.isInCooldown("1", DEVICE_ID, 0)).thenReturn(false);

		ruleEvaluator.evaluate(List.of(rule), TENANT, DEVICE_ID, Instant.now(), HIGH_TEMP);

		verify(actionDispatcher).dispatch(any());
		verify(stateStore).recordTrigger("1", DEVICE_ID, 0);
	}

	@Test
	void onMatch_conditionFalse_doesNotFire() {
		EventRule rule = buildRule(TriggerMode.ON_MATCH, 0, 0);

		ruleEvaluator.evaluate(List.of(rule), TENANT, DEVICE_ID, Instant.now(), LOW_TEMP);

		verify(actionDispatcher, never()).dispatch(any());
	}

	@Test
	void onMatch_inCooldown_doesNotFire() {
		EventRule rule = buildRule(TriggerMode.ON_MATCH, 0, 300);
		when(stateStore.isInCooldown("1", DEVICE_ID, 300)).thenReturn(true);

		ruleEvaluator.evaluate(List.of(rule), TENANT, DEVICE_ID, Instant.now(), HIGH_TEMP);

		verify(actionDispatcher, never()).dispatch(any());
	}

	@Test
	void forDuration_durationNotMet_doesNotFire() {
		EventRule rule = buildRule(TriggerMode.FOR_DURATION, 60, 0);
		when(stateStore.getFirstMatchTs("1", DEVICE_ID)).thenReturn(Instant.now()); // just
																					// now
																					// →
																					// not
																					// 60s
																					// yet

		ruleEvaluator.evaluate(List.of(rule), TENANT, DEVICE_ID, Instant.now(), HIGH_TEMP);

		verify(actionDispatcher, never()).dispatch(any());
	}

	@Test
	void forDuration_durationMet_fires() {
		EventRule rule = buildRule(TriggerMode.FOR_DURATION, 60, 0);
		// firstMatchTs 70 seconds ago → duration met
		Instant firstMatch = Instant.now().minusSeconds(70);
		when(stateStore.getFirstMatchTs("1", DEVICE_ID)).thenReturn(firstMatch);
		when(stateStore.isInCooldown("1", DEVICE_ID, 0)).thenReturn(false);

		ruleEvaluator.evaluate(List.of(rule), TENANT, DEVICE_ID, Instant.now(), HIGH_TEMP);

		verify(actionDispatcher).dispatch(any());
		verify(stateStore).clearFirstMatchTs("1", DEVICE_ID);
	}

	@Test
	void forDuration_conditionFalse_clearsFirstMatchTs() {
		EventRule rule = buildRule(TriggerMode.FOR_DURATION, 60, 0);

		ruleEvaluator.evaluate(List.of(rule), TENANT, DEVICE_ID, Instant.now(), LOW_TEMP);

		verify(stateStore).clearFirstMatchTs("1", DEVICE_ID);
		verify(actionDispatcher, never()).dispatch(any());
	}

	@Test
	void onChange_falseToTrue_fires() {
		EventRule rule = buildRule(TriggerMode.ON_CHANGE, 0, 0);
		when(stateStore.getLastResult("1", DEVICE_ID)).thenReturn(Boolean.FALSE);
		when(stateStore.isInCooldown("1", DEVICE_ID, 0)).thenReturn(false);

		ruleEvaluator.evaluate(List.of(rule), TENANT, DEVICE_ID, Instant.now(), HIGH_TEMP);

		verify(actionDispatcher).dispatch(any());
		verify(stateStore).setLastResult("1", DEVICE_ID, true);
	}

	@Test
	void onChange_trueToTrue_doesNotFire() {
		EventRule rule = buildRule(TriggerMode.ON_CHANGE, 0, 0);
		when(stateStore.getLastResult("1", DEVICE_ID)).thenReturn(Boolean.TRUE);

		ruleEvaluator.evaluate(List.of(rule), TENANT, DEVICE_ID, Instant.now(), HIGH_TEMP);

		verify(actionDispatcher, never()).dispatch(any());
	}

	@Test
	void exceptionInOneRule_doesNotAffectOthers() {
		EventRule failing = buildRule(TriggerMode.ON_MATCH, 0, 0);
		failing.setCondition(null); // null condition → will throw in ConditionEvaluator
		EventRule ok = buildRule(TriggerMode.ON_MATCH, 0, 0);
		ok.setId(2L);
		when(stateStore.isInCooldown("2", DEVICE_ID, 0)).thenReturn(false);

		ruleEvaluator.evaluate(List.of(failing, ok), TENANT, DEVICE_ID, Instant.now(), HIGH_TEMP);

		// second rule still fires
		verify(actionDispatcher, atLeastOnce()).dispatch(any());
	}

}
