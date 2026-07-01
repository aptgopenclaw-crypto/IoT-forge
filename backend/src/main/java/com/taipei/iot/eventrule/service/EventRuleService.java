package com.taipei.iot.eventrule.service;

import java.util.Optional;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.taipei.iot.common.context.TenantContext;
import com.taipei.iot.common.enums.ErrorCode;
import com.taipei.iot.common.exception.BusinessException;
import com.taipei.iot.common.schema.port.SchemaProviderPort;
import com.taipei.iot.eventrule.dto.EventRuleRequest;
import com.taipei.iot.eventrule.dto.EventRuleResponse;
import com.taipei.iot.eventrule.entity.EventRule;
import com.taipei.iot.eventrule.model.ConditionNode;
import com.taipei.iot.eventrule.repository.EventRuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 事件規則 CRUD 服務（含 schema 欄位白名單驗證）。
 *
 * <p>
 * 建立 / 更新規則時，遞迴走訪條件樹的葉節點，以 {@link SchemaProviderPort} 驗證 {@code field} 存在於該設備型別的
 * {@code schema.telemetry.properties}，防止規則引用不存在欄位。
 */
@Service
@RequiredArgsConstructor
public class EventRuleService {

	private final EventRuleRepository ruleRepository;

	private final SchemaProviderPort schemaProviderPort;

	private final EventRuleCache ruleCache;

	@Transactional(readOnly = true)
	public Page<EventRuleResponse> list(String deviceType, Boolean enabled, Pageable pageable) {
		return ruleRepository.findByFilters(deviceType, enabled, pageable).map(EventRuleResponse::from);
	}

	@Transactional(readOnly = true)
	public EventRuleResponse getById(Long id) {
		return EventRuleResponse.from(findOrThrow(id));
	}

	@Transactional
	public EventRuleResponse create(EventRuleRequest req) {
		String tenantId = TenantContext.getCurrentTenantId();
		if (ruleRepository.existsByTenantIdAndRuleCode(tenantId, req.ruleCode())) {
			throw new BusinessException(ErrorCode.IOT_EVENT_RULE_DUPLICATE, "rule_code '" + req.ruleCode() + "' 已存在");
		}
		validateConditionFields(req.deviceType(), req.condition());
		EventRule entity = EventRule.builder()
			.ruleCode(req.ruleCode())
			.name(req.name())
			.deviceType(req.deviceType())
			.severity(req.severity())
			.scope(req.scope())
			.condition(req.condition())
			.triggerCfg(req.triggerCfg())
			.actions(req.actions())
			.enabled(true)
			.build();
		EventRule saved = ruleRepository.save(entity);
		ruleCache.invalidate(tenantId, req.deviceType());
		return EventRuleResponse.from(saved);
	}

	@Transactional
	public EventRuleResponse update(Long id, EventRuleRequest req) {
		String tenantId = TenantContext.getCurrentTenantId();
		EventRule entity = findOrThrow(id);
		// ruleCode uniqueness: only error if changed AND collides
		if (!entity.getRuleCode().equals(req.ruleCode())
				&& ruleRepository.existsByTenantIdAndRuleCode(tenantId, req.ruleCode())) {
			throw new BusinessException(ErrorCode.IOT_EVENT_RULE_DUPLICATE, "rule_code '" + req.ruleCode() + "' 已存在");
		}
		validateConditionFields(req.deviceType(), req.condition());
		String oldDeviceType = entity.getDeviceType();
		entity.setRuleCode(req.ruleCode());
		entity.setName(req.name());
		entity.setDeviceType(req.deviceType());
		entity.setSeverity(req.severity());
		entity.setScope(req.scope());
		entity.setCondition(req.condition());
		entity.setTriggerCfg(req.triggerCfg());
		entity.setActions(req.actions());
		EventRule saved = ruleRepository.save(entity);
		ruleCache.invalidate(tenantId, oldDeviceType);
		ruleCache.invalidate(tenantId, req.deviceType());
		return EventRuleResponse.from(saved);
	}

	@Transactional
	public EventRuleResponse toggleEnabled(Long id, boolean enabled) {
		EventRule entity = findOrThrow(id);
		entity.setEnabled(enabled);
		EventRule saved = ruleRepository.save(entity);
		ruleCache.invalidate(saved.getTenantId(), saved.getDeviceType());
		return EventRuleResponse.from(saved);
	}

	@Transactional
	public void delete(Long id) {
		EventRule entity = findOrThrow(id);
		ruleRepository.delete(entity);
		ruleCache.invalidate(entity.getTenantId(), entity.getDeviceType());
	}

	// ---- schema whitelist validation ----

	/**
	 * 遞迴驗證條件樹中所有葉節點的 {@code field} 存在於 schema.telemetry.properties。
	 */
	void validateConditionFields(String deviceType, ConditionNode node) {
		Optional<JsonNode> schemaOpt = schemaProviderPort.getTelemetrySchema(deviceType);
		Set<String> allowedFields;
		if (schemaOpt.isEmpty()) {
			// schema 不存在：寬鬆模式（允許任意欄位），僅記錄
			return;
		}
		JsonNode properties = schemaOpt.get().path("properties");
		if (properties.isMissingNode() || properties.isEmpty()) {
			return;
		}
		allowedFields = new java.util.HashSet<>();
		properties.fieldNames().forEachRemaining(allowedFields::add);
		validateNode(node, allowedFields);
	}

	private void validateNode(ConditionNode node, Set<String> allowed) {
		if (node == null) {
			return;
		}
		if (node.isLeaf()) {
			if (!allowed.contains(node.getField())) {
				throw new BusinessException(ErrorCode.IOT_TELEMETRY_FORMAT_FIELD_IN_USE,
						"欄位 '" + node.getField() + "' 不在 schema.telemetry.properties 白名單中");
			}
			return;
		}
		if (node.getChildren() != null) {
			node.getChildren().forEach(child -> validateNode(child, allowed));
		}
	}

	private EventRule findOrThrow(Long id) {
		return ruleRepository.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.IOT_EVENT_RULE_NOT_FOUND));
	}

}
