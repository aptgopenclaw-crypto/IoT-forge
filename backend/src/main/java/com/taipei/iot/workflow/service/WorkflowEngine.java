package com.taipei.iot.workflow.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taipei.iot.workflow.entity.WorkflowDefinitionEntity;
import com.taipei.iot.workflow.entity.WorkflowInstanceEntity;
import com.taipei.iot.workflow.entity.WorkflowStepLogEntity;
import com.taipei.iot.workflow.event.WorkflowStepAssignedEvent;
import com.taipei.iot.workflow.event.WorkflowStepCompletedEvent;
import com.taipei.iot.workflow.exception.WorkflowInvalidTransitionException;
import com.taipei.iot.workflow.exception.WorkflowInstanceNotFoundException;
import com.taipei.iot.workflow.exception.WorkflowInstanceNotInProgressException;
import com.taipei.iot.workflow.exception.WorkflowNoRejectHistoryException;
import com.taipei.iot.workflow.exception.WorkflowNotFoundException;
import com.taipei.iot.workflow.exception.WorkflowPermissionException;
import com.taipei.iot.workflow.exception.WorkflowStepAlreadyCompletedException;
import com.taipei.iot.workflow.model.StepDefinition;
import com.taipei.iot.workflow.model.WorkflowAction;
import com.taipei.iot.workflow.model.WorkflowContext;
import com.taipei.iot.workflow.model.WorkflowStatus;
import com.taipei.iot.workflow.model.WorkflowStepType;
import com.taipei.iot.workflow.model.WorkflowStepsJson;
import com.taipei.iot.workflow.repository.WorkflowDefinitionRepository;
import com.taipei.iot.workflow.repository.WorkflowInstanceRepository;
import com.taipei.iot.workflow.repository.WorkflowStepLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowEngine {

	private final WorkflowDefinitionRepository definitionRepo;

	private final WorkflowInstanceRepository instanceRepo;

	private final WorkflowStepLogRepository stepLogRepo;

	private final IAssigneeResolver assigneeResolver;

	private final ObjectMapper objectMapper;

	private final ApplicationEventPublisher eventPublisher;

	// ── Public API ─────────────────────────────────────────────────────────────

	/**
	 * 啟動新流程，建立實例並建立第一步驟待辦。
	 */
	@Transactional
	public WorkflowInstanceEntity start(String workflowCode, String businessId, String businessType,
			WorkflowContext context) {

		// 1. 讀取流程定義（取 version 最高的啟用版本）
		WorkflowDefinitionEntity def = definitionRepo.findLatestEnabledByCode(workflowCode)
			.orElseThrow(() -> new WorkflowNotFoundException(workflowCode));

		// 2. 建立流程實例
		WorkflowStepsJson stepsJson = parseStepsJson(def.getStepsJson());
		StepDefinition firstStep = findStep(stepsJson, stepsJson.getInitialStep());

		// 2. 建立流程實例
		WorkflowInstanceEntity instance = WorkflowInstanceEntity.builder()
			.workflowDefId(def.getId())
			.businessId(businessId)
			.businessType(businessType)
			.currentStepId(firstStep.getId())
			.status(WorkflowStatus.IN_PROGRESS)
			.contextJson(toJson(context))
			.build();

		// 3. 寫入資料庫
		instance = instanceRepo.save(instance);

		// 4. 建立第一步驟的待辦
		createStepLog(instance, firstStep, context);

		return instance;
	}

	/**
	 * 審核通過，推進到下一步驟。
	 */
	@Transactional
	public WorkflowInstanceEntity approve(Long instanceId, String comment, String userId) {

		// 加鎖讀取流程實例與目前步驟待辦，確保同一時間只有一個人能對同一個流程實例進行審核動作
		WorkflowInstanceEntity instance = instanceRepo.findByIdForUpdate(instanceId)
			.orElseThrow(() -> new WorkflowInstanceNotFoundException(instanceId));

		// 狀態機守衛：僅允許 IN_PROGRESS 的流程繼續操作，提供明確的錯誤訊息
		if (instance.getStatus() != WorkflowStatus.IN_PROGRESS) {
			throw new WorkflowInstanceNotInProgressException(instanceId, instance.getStatus());
		}

		// 讀取目前步驟待辦，驗證審核人員身份與步驟狀態
		WorkflowStepLogEntity currentLog = requireCurrentLog(instanceId);
		validateAssignee(currentLog, userId);

		// （這裡的狀態檢查主要是為了避免重複審核同一個步驟，造成流程狀態異常；
		// 實際上在 controller 層也會檢查待辦清單，理論上不太可能發生，但還是加上這層保險）
		if (currentLog.getCompletedAt() != null) {
			throw new WorkflowStepAlreadyCompletedException(currentLog.getStepName());
		}

		// 讀取流程定義與步驟資訊，驗證流程定義與下一步驟存在
		WorkflowStepsJson stepsJson = parseStepsJson(loadDef(instance).getStepsJson());

		// 讀取目前步驟資訊，驗證流程定義與目前步驟存在
		StepDefinition currentStep = findStep(stepsJson, currentLog.getStepId());

		// 完成目前步驟待辦
		completeLog(currentLog, WorkflowAction.APPROVE, comment, null);

		// 如果目前步驟是 end 就直接完成流程，否則推進到下一步
		if (currentStep.getType() == WorkflowStepType.END) {
			instance.setStatus(WorkflowStatus.COMPLETED);
			instance.setCompletedAt(LocalDateTime.now());
			return instanceRepo.save(instance);
		}

		// 推進到下一步，先完整計算最終狀態，再執行單次 save
		StepDefinition nextStep = findStep(stepsJson, currentStep.getNext());
		instance.setCurrentStepId(nextStep.getId());

		if (nextStep.getType() == WorkflowStepType.END) {
			// 下一步是結束步驟：直接完成流程，一次 save
			instance.setStatus(WorkflowStatus.COMPLETED);
			instance.setCompletedAt(LocalDateTime.now());
			return instanceRepo.save(instance);
		}

		// 下一步是正常步驟：save 後建立該步驟的待辦
		instance = instanceRepo.save(instance);
		WorkflowContext context = parseContext(instance.getContextJson());
		createStepLog(instance, nextStep, context);
		return instance;
	}

	/**
	 * 審核退回，跳到指定步驟。
	 */
	@Transactional
	public WorkflowInstanceEntity reject(Long instanceId, String targetStepId, String comment, String userId) {

		// 加鎖讀取流程實例與目前步驟待辦，確保同一時間只有一個人能對同一個流程實例進行審核動作
		WorkflowInstanceEntity instance = instanceRepo.findByIdForUpdate(instanceId)
			.orElseThrow(() -> new WorkflowInstanceNotFoundException(instanceId));

		// 狀態機守衛：僅允許 IN_PROGRESS 的流程繼續操作，提供明確的錯誤訊息
		if (instance.getStatus() != WorkflowStatus.IN_PROGRESS) {
			throw new WorkflowInstanceNotInProgressException(instanceId, instance.getStatus());
		}

		// 讀取目前步驟待辦，驗證審核人員身份與步驟狀態
		WorkflowStepLogEntity currentLog = requireCurrentLog(instanceId);

		// end step 的 assigneeUserId 為 null，不做審核人驗證；其他步驟則驗證 userId 是否與 assigneeUserId 相符
		validateAssignee(currentLog, userId);

		// （這裡的狀態檢查主要是為了避免重複審核同一個步驟，造成流程狀態異常；
		// 實際上在 controller 層也會檢查待辦清單，理論上不太可能發生，但還是加上這層保險）
		if (currentLog.getCompletedAt() != null) {
			throw new WorkflowStepAlreadyCompletedException(currentLog.getStepName());
		}

		// 讀取流程定義與步驟資訊，驗證流程定義與目標步驟存在，並驗證目標步驟是目前步驟的 reject_target
		WorkflowStepsJson stepsJson = parseStepsJson(loadDef(instance).getStepsJson());

		// 讀取目前步驟資訊，驗證流程定義與目前步驟存在
		StepDefinition currentStep = findStep(stepsJson, currentLog.getStepId());

		// 驗證目標步驟是目前步驟的 reject_target
		if (!targetStepId.equals(currentStep.getRejectTarget())) {
			throw new WorkflowInvalidTransitionException(currentStep.getId(), targetStepId);
		}

		// 完成目前步驟待辦，並註記退回目標步驟
		completeLog(currentLog, WorkflowAction.REJECT, comment, targetStepId);
		// 更新流程實例的 currentStepId 為退回目標步驟，並建立退回目標步驟的待辦
		StepDefinition targetStep = findStep(stepsJson, targetStepId);
		instance.setCurrentStepId(targetStep.getId());
		instance = instanceRepo.save(instance);

		// 建立退回目標步驟的待辦
		WorkflowContext context = parseContext(instance.getContextJson());

		createStepLog(instance, targetStep, context);
		return instance;
	}

	/**
	 * 補件重送，回到「最後一次執行退回操作的步驟」（即退回的發起方，非退回的目標方）。
	 *
	 * <p>
	 * <b>回傳步驟語意說明：</b><br>
	 * {@code returnStepId} 取自歷程中最後一筆 {@link WorkflowAction#REJECT} 記錄的
	 * {@code stepId}，代表「是哪個步驟做的退回」，而非「退回到哪個步驟」。<br>
	 * 例如流程為：申請人 → 主管 → 財產管理：
	 * <ul>
	 * <li>主管在 {@code step_manager} 退回給申請人 ({@code step_applicant})</li>
	 * <li>申請人補件後，resubmit 回到 {@code step_manager}（退回的發起步驟）， 而非
	 * {@code step_applicant}（退回的目標步驟）</li>
	 * </ul>
	 *
	 * <p>
	 * <b>多次退回的行為：</b><br>
	 * 若流程曾被財產管理退回給主管，主管又退回給申請人，此時 {@code lastReject}
	 * 為主管的退回記錄（{@code step_manager}），補件後直接回到主管那關， 跳過財產管理的再次審核。這是刻意設計，表示「最近一次要求補件的審核人」。
	 * 若業務需要回到最頂層重走，請改為取第一筆 reject 記錄。
	 * @param instanceId 流程實例 ID
	 * @param comment 申請人補件說明
	 * @param userId 操作者 user ID（必須是當前步驟的 assignee，即申請人）
	 */
	@Transactional
	public WorkflowInstanceEntity resubmit(Long instanceId, String comment, String userId) {

		// 加鎖讀取流程實例與目前步驟待辦，確保同一時間只有一個人能對同一個流程實例進行審核動作
		WorkflowInstanceEntity instance = instanceRepo.findByIdForUpdate(instanceId)
			.orElseThrow(() -> new WorkflowInstanceNotFoundException(instanceId));

		// 狀態機守衛：僅允許 IN_PROGRESS 的流程繼續操作，提供明確的錯誤訊息
		if (instance.getStatus() != WorkflowStatus.IN_PROGRESS) {
			throw new WorkflowInstanceNotInProgressException(instanceId, instance.getStatus());
		}

		// 讀取目前步驟待辦，驗證審核人員身份與步驟狀態
		WorkflowStepLogEntity currentLog = requireCurrentLog(instanceId);
		validateAssignee(currentLog, userId);

		List<WorkflowStepLogEntity> history = stepLogRepo.findByWorkflowInstanceIdOrderByEnteredAtAsc(instanceId);

		WorkflowStepLogEntity lastReject = history.stream()
			.filter(l -> WorkflowAction.REJECT == l.getAction())
			.reduce((a, b) -> b) // 最後一筆
			.orElseThrow(() -> new WorkflowNoRejectHistoryException(instanceId));

		String returnStepId = lastReject.getStepId();
		completeLog(currentLog, WorkflowAction.RESUBMIT, comment, null);

		WorkflowStepsJson stepsJson = parseStepsJson(loadDef(instance).getStepsJson());
		StepDefinition returnStep = findStep(stepsJson, returnStepId);
		instance.setCurrentStepId(returnStep.getId());
		instance = instanceRepo.save(instance);

		WorkflowContext context = parseContext(instance.getContextJson());
		createStepLog(instance, returnStep, context);
		return instance;
	}

	/**
	 * 申請人取消流程。
	 * <p>
	 * 僅限流程仍為 {@link WorkflowStatus#IN_PROGRESS} 時由申請人主動取消。 取消後目前步驟待辦標記為
	 * {@link WorkflowAction#CANCEL}， 流程實例狀態設為 {@link WorkflowStatus#CANCELLED}。
	 * @param instanceId 流程實例 ID
	 * @param comment 取消原因
	 * @param userId 操作者 user ID（必須是申請人）
	 */
	@Transactional
	public WorkflowInstanceEntity cancel(Long instanceId, String comment, String userId) {

		// 加鎖讀取流程實例
		WorkflowInstanceEntity instance = instanceRepo.findByIdForUpdate(instanceId)
			.orElseThrow(() -> new WorkflowInstanceNotFoundException(instanceId));

		// 狀態機守衛：僅允許 IN_PROGRESS 的流程被取消
		if (instance.getStatus() != WorkflowStatus.IN_PROGRESS) {
			throw new WorkflowInstanceNotInProgressException(instanceId, instance.getStatus());
		}

		// 驗證操作者是申請人（僅申請人可取消自己的流程）
		WorkflowContext context = parseContext(instance.getContextJson());
		if (!userId.equals(context.getApplicantId())) {
			throw new WorkflowPermissionException(userId, "cancel instance#" + instanceId);
		}

		// 完成目前步驟待辦（標記為取消）
		WorkflowStepLogEntity currentLog = requireCurrentLog(instanceId);
		if (currentLog.getCompletedAt() != null) {
			throw new WorkflowStepAlreadyCompletedException(currentLog.getStepName());
		}
		completeLog(currentLog, WorkflowAction.CANCEL, comment, null);

		// 流程實例狀態設為 CANCELLED
		instance.setStatus(WorkflowStatus.CANCELLED);
		instance.setCompletedAt(LocalDateTime.now());
		return instanceRepo.save(instance);
	}

	/**
	 * 取得目前步驟允許退回的目標步驟（若無退回目標則回傳 null）。
	 */
	@Transactional(readOnly = true)
	public StepDefinition getRejectTargetStep(Long instanceId) {
		WorkflowInstanceEntity instance = instanceRepo.findById(instanceId)
			.orElseThrow(() -> new WorkflowInstanceNotFoundException(instanceId));
		WorkflowStepLogEntity currentLog = requireCurrentLog(instanceId);
		if (currentLog.getCompletedAt() != null) {
			return null;
		}
		WorkflowStepsJson stepsJson = parseStepsJson(loadDef(instance).getStepsJson());
		StepDefinition currentStep = findStep(stepsJson, currentLog.getStepId());
		if (currentStep.getRejectTarget() == null) {
			return null;
		}
		return findStep(stepsJson, currentStep.getRejectTarget());
	}

	/**
	 * 查詢流程實例。
	 */
	@Transactional(readOnly = true)
	public WorkflowInstanceEntity getInstance(Long instanceId) {
		return instanceRepo.findById(instanceId).orElseThrow(() -> new WorkflowInstanceNotFoundException(instanceId));
	}

	/**
	 * 查詢步驟歷程。
	 */
	@Transactional(readOnly = true)
	public List<WorkflowStepLogEntity> getHistory(Long instanceId) {
		return stepLogRepo.findByWorkflowInstanceIdOrderByEnteredAtAsc(instanceId);
	}

	/**
	 * 檢查使用者是否有權限存取指定流程實例（申請人 OR 任一步驟的審核人）。
	 * <p>
	 * 若需要管理員例外，由 Controller 層在呼叫前以 {@code hasAuthority} 判斷並跳過此檢查。
	 */
	@Transactional(readOnly = true)
	public boolean hasAccessToInstance(WorkflowInstanceEntity instance, String userId) {
		WorkflowContext context = parseContext(instance.getContextJson());
		if (userId.equals(context.getApplicantId())) {
			return true;
		}
		return stepLogRepo.findByWorkflowInstanceIdOrderByEnteredAtAsc(instance.getId())
			.stream()
			.anyMatch(log -> userId.equals(log.getAssigneeUserId()));
	}

	// ── Private helpers ────────────────────────────────────────────────────────

	private WorkflowDefinitionEntity loadDef(WorkflowInstanceEntity instance) {
		return definitionRepo.findById(instance.getWorkflowDefId())
			.orElseThrow(() -> new WorkflowNotFoundException("id=" + instance.getWorkflowDefId()));
	}

	private void createStepLog(WorkflowInstanceEntity instance, StepDefinition step, WorkflowContext context) {
		String assignee = null;
		if (step.getRoleCode() != null) {
			assignee = assigneeResolver.resolve(step, context);
		}
		WorkflowStepLogEntity stepLog = WorkflowStepLogEntity.builder()
			.workflowInstanceId(instance.getId())
			.stepId(step.getId())
			.stepName(step.getName())
			.assigneeUserId(assignee)
			.build();
		stepLogRepo.save(stepLog);

		log.info("[Workflow] createStepLog: instance={}, step={}, assignee={}", instance.getId(), step.getId(),
				assignee);

		// 發布步驟指派事件（通知新任審核人）
		if (assignee != null) {
			log.info("[Workflow] publishing WorkflowStepAssignedEvent: assignee={}, step={}", assignee, step.getId());
			eventPublisher.publishEvent(new WorkflowStepAssignedEvent(this, instance.getTenantId(), assignee,
					instance.getId(), instance.getBusinessType(), step.getId(), step.getName()));
		}
	}

	private void completeLog(WorkflowStepLogEntity stepLog, WorkflowAction action, String comment,
			String targetStepId) {
		stepLog.setAction(action);
		stepLog.setComment(comment);
		stepLog.setTargetStepId(targetStepId);
		stepLog.setCompletedAt(LocalDateTime.now());
		stepLogRepo.save(stepLog);

		log.info("[Workflow] completeLog: instance={}, step={}, action={}", stepLog.getWorkflowInstanceId(),
				stepLog.getStepId(), action);

		// 發布步驟完成事件
		eventPublisher
			.publishEvent(new WorkflowStepCompletedEvent(this, stepLog.getTenantId(), stepLog.getWorkflowInstanceId(),
					null, stepLog.getStepId(), stepLog.getStepName(), action, stepLog.getAssigneeUserId()));
	}

	private void validateAssignee(WorkflowStepLogEntity log, String userId) {
		// end step 的 assigneeUserId 為 null，不做審核人驗證
		if (log.getAssigneeUserId() == null) {
			return;
		}
		if (!userId.equals(log.getAssigneeUserId())) {
			throw new WorkflowPermissionException(userId, log.getStepName());
		}
	}

	private WorkflowStepLogEntity requireCurrentLog(Long instanceId) {
		return stepLogRepo.findCurrentByInstanceId(instanceId)
			.orElseThrow(() -> new WorkflowInstanceNotFoundException(instanceId));
	}

	private StepDefinition findStep(WorkflowStepsJson stepsJson, String stepId) {
		return stepsJson.getSteps()
			.stream()
			.filter(s -> s.getId().equals(stepId))
			.findFirst()
			.orElseThrow(() -> new WorkflowNotFoundException("step=" + stepId));
	}

	private WorkflowStepsJson parseStepsJson(String json) {
		try {
			return objectMapper.readValue(json, WorkflowStepsJson.class);
		}
		catch (JsonProcessingException e) {
			throw new WorkflowNotFoundException("steps_json 解析失敗：" + e.getMessage());
		}
	}

	private WorkflowContext parseContext(String json) {
		if (json == null) {
			return WorkflowContext.builder().build();
		}
		try {
			return objectMapper.readValue(json, WorkflowContext.class);
		}
		catch (JsonProcessingException e) {
			return WorkflowContext.builder().build();
		}
	}

	private String toJson(Object obj) {
		try {
			return objectMapper.writeValueAsString(obj);
		}
		catch (JsonProcessingException e) {
			return "{}";
		}
	}

}
