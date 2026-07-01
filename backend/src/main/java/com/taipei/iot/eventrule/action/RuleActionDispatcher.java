package com.taipei.iot.eventrule.action;

import java.util.List;

import com.taipei.iot.eventrule.model.ActionConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 動作派發器：依 {@link ActionConfig#getType()} 路由到對應的 {@link RuleActionHandler}。
 *
 * <p>
 * 單一動作失敗不影響同規則其他動作（例外隔離）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RuleActionDispatcher {

	private final List<RuleActionHandler> handlers;

	public void dispatch(RuleMatch match) {
		if (match.rule().getActions() == null) {
			return;
		}
		for (ActionConfig action : match.rule().getActions()) {
			if (action.getType() == null) {
				continue;
			}
			handlers.stream().filter(h -> h.supports(action.getType())).findFirst().ifPresentOrElse(h -> {
				try {
					h.execute(match, action);
				}
				catch (Exception e) {
					log.warn("[RuleActionDispatcher] action={} rule={} failed: {}", action.getType(),
							match.rule().getRuleCode(), e.getMessage());
				}
			}, () -> log.warn("[RuleActionDispatcher] no handler for action type={}", action.getType()));
		}
	}

}
