package com.taipei.iot.eventrule.entity;

import java.time.LocalDateTime;
import java.util.List;

import com.taipei.iot.common.tenant.TenantAware;
import com.taipei.iot.common.tenant.TenantEntityListener;
import com.taipei.iot.eventrule.model.ActionConfig;
import com.taipei.iot.eventrule.model.ConditionNode;
import com.taipei.iot.eventrule.model.RuleScope;
import com.taipei.iot.eventrule.model.TriggerConfig;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * 事件規則定義實體，對應 {@code event_rule} 表。
 *
 * <p>
 * 租戶隔離：掛 {@code tenantFilter}，{@link TenantEntityListener} 防跨租戶篡改。 規則的即時執行狀態（duration /
 * cooldown）存 Redis，不落此表。
 */
@Entity
@Table(name = "event_rule")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@EntityListeners({ TenantEntityListener.class, AuditingEntityListener.class })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventRule implements TenantAware {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "tenant_id", nullable = false, length = 50)
	private String tenantId;

	@Column(name = "rule_code", nullable = false, length = 50)
	private String ruleCode;

	@Column(name = "name", nullable = false, length = 200)
	private String name;

	@Column(name = "device_type", nullable = false, length = 30)
	private String deviceType;

	@Builder.Default
	@Column(name = "enabled", nullable = false)
	private boolean enabled = true;

	@Column(name = "severity", nullable = false, length = 20)
	private String severity;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "scope", columnDefinition = "jsonb")
	private RuleScope scope;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "condition", nullable = false, columnDefinition = "jsonb")
	private ConditionNode condition;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "trigger_cfg", nullable = false, columnDefinition = "jsonb")
	private TriggerConfig triggerCfg;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "actions", nullable = false, columnDefinition = "jsonb")
	private List<ActionConfig> actions;

	@CreatedDate
	@Column(name = "create_time", updatable = false)
	private LocalDateTime createTime;

	@LastModifiedDate
	@Column(name = "update_time")
	private LocalDateTime updateTime;

	@Override
	public String getTenantId() {
		return tenantId;
	}

	@Override
	public void setTenantId(String tenantId) {
		this.tenantId = tenantId;
	}

}
