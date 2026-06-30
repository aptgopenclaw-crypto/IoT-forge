package com.taipei.iot.common.event;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 事件規則觸發事件。
 *
 * <p>
 * 當 {@code event-rule} 模組的規則命中（且通過 cooldown / duration 收斂）時，由 {@code NotifyActionHandler}
 * 發出，供 {@code notification} 模組以 {@code @Async @EventListener} 訂閱後組裝
 * {@code NotificationPayload} 並送出通知。
 *
 * <p>
 * 採 Spring {@code ApplicationEventPublisher} 解耦：本事件定義於 {@code common}，{@code event-rule}
 * 發出、 {@code notification} 訂閱，雙方皆不直接相依。未來若改用 Redis Stream / Kafka，發布端與訂閱端皆無感。
 *
 * @param tenantId 租戶識別碼
 * @param deviceId 觸發規則的設備主鍵（{@code devices.id}）
 * @param ruleId 規則識別碼（{@code event_rule.rule_code}）
 * @param ruleName 規則名稱（用於通知標題）
 * @param severity 嚴重度（{@code INFO} / {@code WARNING} / {@code CRITICAL}）
 * @param triggeredAt 觸發時間
 * @param matchedValues 命中當下的遙測值快照（唯讀）
 * @param channels 欲送達的通知通道（如 {@code IN_APP}、{@code EMAIL}）
 * @param recipients 收件人指定（角色代碼 / 使用者）
 * @param template 通知模板代碼（由 notification 模組解析）
 */
public record RuleTriggeredEvent(String tenantId, Long deviceId, String ruleId, String ruleName, String severity,
		Instant triggeredAt, Map<String, Object> matchedValues, List<String> channels, RecipientSpec recipients,
		String template) {

	/**
	 * 收件人指定。
	 *
	 * @param roleCodes 以角色代碼指定收件人（該角色下使用者皆收）
	 * @param userIds 以使用者 ID 直接指定收件人
	 */
	public record RecipientSpec(List<String> roleCodes, List<String> userIds) {
	}
}
