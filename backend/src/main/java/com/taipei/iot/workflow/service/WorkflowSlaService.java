package com.taipei.iot.workflow.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taipei.iot.workflow.dto.WorkflowSlaDto;
import com.taipei.iot.workflow.dto.WorkflowSlaDto.StepSlaDto;
import com.taipei.iot.workflow.entity.WorkflowDefinitionEntity;
import com.taipei.iot.workflow.entity.WorkflowInstanceEntity;
import com.taipei.iot.workflow.entity.WorkflowStepLogEntity;
import com.taipei.iot.workflow.exception.WorkflowInstanceNotFoundException;
import com.taipei.iot.workflow.exception.WorkflowNotFoundException;
import com.taipei.iot.workflow.model.StepDefinition;
import com.taipei.iot.workflow.model.WorkflowStepsJson;
import com.taipei.iot.workflow.repository.WorkflowDefinitionRepository;
import com.taipei.iot.workflow.repository.WorkflowInstanceRepository;
import com.taipei.iot.workflow.repository.WorkflowStepLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 流程 SLA 時效 KPI 計算服務。
 * <p>
 * 計算邏輯：
 * <ul>
 * <li>每個步驟的時效：completedAt - enteredAt（尚未完成則取 now - enteredAt）</li>
 * <li>整體流程時效：completedAt - createdAt（尚未完成則取 now - createdAt）</li>
 * <li>slaTotalDays：各步驟 slaDays 加總；任一步驟未設定則為 null</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class WorkflowSlaService {

	private final WorkflowInstanceRepository instanceRepo;

	private final WorkflowStepLogRepository stepLogRepo;

	private final WorkflowDefinitionRepository definitionRepo;

	private final ObjectMapper objectMapper;

	/**
	 * 計算指定流程實例的 SLA KPI。
	 */
	@Transactional(readOnly = true)
	public WorkflowSlaDto getSla(Long instanceId) {
		WorkflowInstanceEntity instance = instanceRepo.findById(instanceId)
			.orElseThrow(() -> new WorkflowInstanceNotFoundException(instanceId));

		// 讀取步驟定義，建立 stepId → slaDays 對照表
		WorkflowDefinitionEntity def = definitionRepo.findById(instance.getWorkflowDefId())
			.orElseThrow(() -> new WorkflowNotFoundException("id=" + instance.getWorkflowDefId()));
		WorkflowStepsJson stepsJson = parseStepsJson(def.getStepsJson());
		Map<String, Integer> slaMap = stepsJson.getSteps()
			.stream()
			.filter(s -> s.getSlaDays() != null)
			.collect(Collectors.toMap(StepDefinition::getId, StepDefinition::getSlaDays));

		// 讀取步驟歷程
		List<WorkflowStepLogEntity> logs = stepLogRepo.findByWorkflowInstanceIdOrderByEnteredAtAsc(instanceId);

		LocalDateTime now = LocalDateTime.now();

		// 計算各步驟 SLA 明細
		List<StepSlaDto> stepDtos = logs.stream().map(log -> {
			Integer stepSla = slaMap.get(log.getStepId());
			Double actualDays = computeDays(log.getEnteredAt(),
					log.getCompletedAt() != null ? log.getCompletedAt() : now);
			boolean overdue = stepSla != null && actualDays != null && actualDays > stepSla;
			return new StepSlaDto(log.getStepId(), log.getStepName(), log.getAssigneeUserId(), log.getAction(), stepSla,
					actualDays, overdue, log.getEnteredAt(), log.getCompletedAt());
		}).toList();

		// 計算整體 SLA：只加總有設定 sla_days 的步驟；全部步驟皆未設定才回傳 null
		List<StepDefinition> stepsWithSla = stepsJson.getSteps().stream().filter(s -> s.getSlaDays() != null).toList();
		Integer slaTotalDays = stepsWithSla.isEmpty() ? null
				: stepsWithSla.stream().mapToInt(StepDefinition::getSlaDays).sum();

		LocalDateTime endTime = instance.getCompletedAt() != null ? instance.getCompletedAt() : now;
		Double actualTotalDays = computeDays(instance.getCreatedAt(), endTime);
		boolean overdue = slaTotalDays != null && actualTotalDays != null && actualTotalDays > slaTotalDays;

		return new WorkflowSlaDto(instance.getId(), instance.getBusinessId(), instance.getBusinessType(),
				instance.getStatus(), instance.getCreatedAt(), instance.getCompletedAt(), slaTotalDays, actualTotalDays,
				overdue, stepDtos);
	}

	private Double computeDays(LocalDateTime from, LocalDateTime to) {
		if (from == null || to == null) {
			return null;
		}
		long minutes = ChronoUnit.MINUTES.between(from, to);
		return Math.round(minutes / 1440.0 * 100.0) / 100.0; // 精確到小數點後兩位
	}

	private WorkflowStepsJson parseStepsJson(String json) {
		try {
			return objectMapper.readValue(json, WorkflowStepsJson.class);
		}
		catch (JsonProcessingException e) {
			throw new IllegalStateException("Failed to parse workflow steps JSON", e);
		}
	}

}
