package com.taipei.iot.eventrule.entity;

import java.time.LocalDateTime;
import java.util.Map;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 事件規則觸發記錄，對應 {@code event_rule_trigger_log} 表。
 *
 * <p>
 * append-only，僅「條件成立且通過 cooldown / duration 收斂後實際觸發」才寫一筆，故遠比 {@code telemetry_data}
 * 稀疏。查詢層以 {@code tenant_id} 明確過濾（與 telemetry 同策略）。
 */
@Entity
@Table(name = "event_rule_trigger_log")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventRuleTriggerLog {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "tenant_id", nullable = false, length = 50)
	private String tenantId;

	@Column(name = "rule_id", nullable = false)
	private Long ruleId;

	@Column(name = "device_id", nullable = false)
	private Long deviceId;

	@Builder.Default
	@Column(name = "triggered_at", nullable = false)
	private LocalDateTime triggeredAt = LocalDateTime.now();

	@Column(name = "severity", length = 20)
	private String severity;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "matched_values", columnDefinition = "jsonb")
	private Map<String, Object> matchedValues;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "action_result", columnDefinition = "jsonb")
	private Map<String, Object> actionResult;

}
