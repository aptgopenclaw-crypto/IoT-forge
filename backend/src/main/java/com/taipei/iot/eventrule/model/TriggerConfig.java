package com.taipei.iot.eventrule.model;

/**
 * 觸發語意設定（對應 {@code event_rule.trigger_cfg} JSONB）。
 *
 * @param mode 觸發模式
 * @param durationSec FOR_DURATION：條件需持續滿足的秒數（0 等同 ON_MATCH）
 * @param cooldownSec 冷卻秒數（0 = 不冷卻）；觸發後 N 秒內不重複觸發
 */
public record TriggerConfig(TriggerMode mode, int durationSec, int cooldownSec) {

	public TriggerConfig {
		if (mode == null) {
			mode = TriggerMode.ON_MATCH;
		}
	}

}
