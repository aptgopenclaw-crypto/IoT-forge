package com.taipei.iot.eventrule.evaluation;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.taipei.iot.eventrule.action.RuleActionDispatcher;
import com.taipei.iot.eventrule.action.RuleMatch;
import com.taipei.iot.eventrule.entity.EventRule;
import com.taipei.iot.eventrule.entity.EventRuleTriggerLog;
import com.taipei.iot.eventrule.model.TriggerConfig;
import com.taipei.iot.eventrule.model.TriggerMode;
import com.taipei.iot.eventrule.repository.EventRuleTriggerLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 規則評估協調器：條件求值 + 觸發語意（cooldown / FOR_DURATION / ON_CHANGE）+ 動作派發。
 *
 * <p>
 * 單一規則失敗不影響同批其他規則（例外隔離，best-effort）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RuleEvaluator {

	private final ConditionEvaluator conditionEvaluator;

	private final RuleStateStore stateStore;

	private final RuleActionDispatcher actionDispatcher;

	private final EventRuleTriggerLogRepository triggerLogRepository;

	/**
	 * 評估規則清單，命中者執行動作並寫觸發記錄。
	 */
	public void evaluate(List<EventRule> rules, String tenantId, Long deviceId, Instant ts,
			Map<String, Object> values) {
		for (EventRule rule : rules) {
			try {
				evaluateOne(rule, tenantId, deviceId, ts, values);
			}
			catch (Exception e) {
				log.warn("[RuleEvaluator] rule={} device={} evaluation error: {}", rule.getRuleCode(), deviceId,
						e.getMessage(), e);
			}
		}
	}

	private void evaluateOne(EventRule rule, String tenantId, Long deviceId, Instant ts, Map<String, Object> values) {
		// 1. scope 篩選：deviceIds 限定
		if (rule.getScope() != null && rule.getScope().deviceIds() != null && !rule.getScope().deviceIds().isEmpty()
				&& !rule.getScope().deviceIds().contains(deviceId)) {
			return;
		}

		// 2. 條件求值
		boolean matched = conditionEvaluator.evaluate(rule.getCondition(), values);

		// 3. 觸發語意（含 cooldown）
		String ruleKey = String.valueOf(rule.getId());
		TriggerConfig trigger = rule.getTriggerCfg();
		if (trigger == null) {
			trigger = new TriggerConfig(TriggerMode.ON_MATCH, 0, 0);
		}

		boolean shouldFire = appleTriggerLogic(ruleKey, deviceId, matched, trigger);

		if (!shouldFire) {
			return;
		}

		// 4. cooldown 檢查（所有 mode 均受 cooldown 限制）
		if (stateStore.isInCooldown(ruleKey, deviceId, trigger.cooldownSec())) {
			log.debug("[RuleEvaluator] rule={} device={} in cooldown, skipping", rule.getRuleCode(), deviceId);
			return;
		}

		// 5. 記錄觸發時間
		stateStore.recordTrigger(ruleKey, deviceId, trigger.cooldownSec());

		// 6. 寫觸發記錄
		RuleMatch match = new RuleMatch(rule, tenantId, deviceId, ts, values);
		writeTriggerLog(rule, tenantId, deviceId, values);

		// 7. 派發動作
		actionDispatcher.dispatch(match);
	}

	/**
	 * 依 trigger mode 判斷是否應觸發（不含 cooldown）。
	 */
	private boolean appleTriggerLogic(String ruleKey, Long deviceId, boolean matched, TriggerConfig trigger) {
		return switch (trigger.mode()) {
			case ON_MATCH -> matched;
			case FOR_DURATION -> {
				if (!matched) {
					stateStore.clearFirstMatchTs(ruleKey, deviceId);
					yield false;
				}
				stateStore.setFirstMatchTsIfAbsent(ruleKey, deviceId, trigger.durationSec());
				Instant firstMatch = stateStore.getFirstMatchTs(ruleKey, deviceId);
				if (firstMatch == null) {
					yield false;
				}
				boolean durationMet = Duration.between(firstMatch, Instant.now()).toSeconds() >= trigger.durationSec();
				if (durationMet) {
					stateStore.clearFirstMatchTs(ruleKey, deviceId);
				}
				yield durationMet;
			}
			case ON_CHANGE -> {
				Boolean lastResult = stateStore.getLastResult(ruleKey, deviceId);
				boolean prevMatched = lastResult != null && lastResult;
				stateStore.setLastResult(ruleKey, deviceId, matched);
				// 邊緣觸發：false → true
				yield matched && !prevMatched;
			}
		};
	}

	private void writeTriggerLog(EventRule rule, String tenantId, Long deviceId, Map<String, Object> values) {
		try {
			triggerLogRepository.save(EventRuleTriggerLog.builder()
				.tenantId(tenantId)
				.ruleId(rule.getId())
				.deviceId(deviceId)
				.severity(rule.getSeverity())
				.matchedValues(values)
				.build());
		}
		catch (Exception e) {
			log.warn("[RuleEvaluator] failed to write trigger log for rule={}: {}", rule.getRuleCode(), e.getMessage());
		}
	}

}
