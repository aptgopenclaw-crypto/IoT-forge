package com.taipei.iot.eventrule.action;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.taipei.iot.common.event.RuleTriggeredEvent;
import com.taipei.iot.eventrule.model.ActionConfig;
import com.taipei.iot.eventrule.model.ActionType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * 通知動作處理器：組裝 {@link RuleTriggeredEvent} 並發布。
 *
 * <p>
 * {@code notification} 模組的 {@code RuleTriggeredEventListener} 訂閱後解析
 * {@code recipients.roleCodes} → userId 並送出通知。此 handler 不直接相依 notification 模組。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotifyActionHandler implements RuleActionHandler {

	private final ApplicationEventPublisher eventPublisher;

	@Override
	public boolean supports(ActionType type) {
		return ActionType.NOTIFY == type;
	}

	@Override
	public void execute(RuleMatch match, ActionConfig action) {
		List<String> roleCodes = new ArrayList<>();
		List<String> userIds = new ArrayList<>();
		if (action.getRecipients() != null) {
			if (action.getRecipients().getRoleCodes() != null) {
				roleCodes.addAll(action.getRecipients().getRoleCodes());
			}
			if (action.getRecipients().getUserIds() != null) {
				userIds.addAll(action.getRecipients().getUserIds());
			}
		}

		RuleTriggeredEvent event = new RuleTriggeredEvent(match.tenantId(), match.deviceId(),
				match.rule().getRuleCode(), match.rule().getName(), match.rule().getSeverity(), Instant.now(),
				match.matchedValues(), action.getChannels() != null ? action.getChannels() : List.of(),
				new RuleTriggeredEvent.RecipientSpec(roleCodes, userIds), action.getTemplate());

		eventPublisher.publishEvent(event);
		log.debug("[NotifyActionHandler] published RuleTriggeredEvent: rule={} device={} severity={}",
				match.rule().getRuleCode(), match.deviceId(), match.rule().getSeverity());
	}

}
