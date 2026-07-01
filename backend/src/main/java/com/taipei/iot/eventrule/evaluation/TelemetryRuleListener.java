package com.taipei.iot.eventrule.evaluation;

import java.util.List;

import com.taipei.iot.common.context.TenantContext;
import com.taipei.iot.common.event.TelemetryReceivedEvent;
import com.taipei.iot.eventrule.entity.EventRule;
import com.taipei.iot.eventrule.service.EventRuleCache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 訂閱 {@link TelemetryReceivedEvent}，在獨立執行緒中載入規則並評估。
 *
 * <p>
 * {@code @Async} 執行緒不繼承 ThreadLocal，故需顯式還原 {@link TenantContext}； {@code finally}
 * 區塊確保清除，防止執行緒池殘留。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TelemetryRuleListener {

	private final EventRuleCache ruleCache;

	private final RuleEvaluator ruleEvaluator;

	@Async("eventRuleExecutor")
	@EventListener
	public void onTelemetryReceived(TelemetryReceivedEvent event) {
		TenantContext.setCurrentTenantId(event.tenantId());
		try {
			List<EventRule> rules = ruleCache.getRules(event.tenantId(), event.deviceType());
			if (rules.isEmpty()) {
				return;
			}
			log.debug("[TelemetryRuleListener] tenant={} deviceType={} deviceId={} evaluating {} rules",
					event.tenantId(), event.deviceType(), event.deviceId(), rules.size());
			ruleEvaluator.evaluate(rules, event.tenantId(), event.deviceId(), event.ts(), event.values());
		}
		catch (Exception e) {
			log.error("[TelemetryRuleListener] evaluation error for tenant={} device={}: {}", event.tenantId(),
					event.deviceId(), e.getMessage(), e);
		}
		finally {
			TenantContext.clear();
		}
	}

}
