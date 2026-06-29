package com.taipei.iot.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taipei.iot.workflow.entity.WorkflowDefinitionEntity;
import com.taipei.iot.workflow.entity.WorkflowInstanceEntity;
import com.taipei.iot.workflow.entity.WorkflowStepLogEntity;
import com.taipei.iot.workflow.exception.WorkflowInvalidTransitionException;
import com.taipei.iot.workflow.exception.WorkflowInstanceNotInProgressException;
import com.taipei.iot.workflow.exception.WorkflowNoRejectHistoryException;
import com.taipei.iot.workflow.exception.WorkflowPermissionException;
import com.taipei.iot.workflow.exception.WorkflowStepAlreadyCompletedException;
import com.taipei.iot.workflow.model.WorkflowAction;
import com.taipei.iot.workflow.model.WorkflowContext;
import com.taipei.iot.workflow.model.WorkflowStatus;
import com.taipei.iot.workflow.repository.WorkflowDefinitionRepository;
import com.taipei.iot.workflow.repository.WorkflowInstanceRepository;
import com.taipei.iot.workflow.repository.WorkflowStepLogRepository;
import com.taipei.iot.workflow.service.IAssigneeResolver;
import com.taipei.iot.workflow.service.WorkflowEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WorkflowEngineTest {

	// 4-step asset_transfer 流程定義 JSON
	private static final String STEPS_JSON = """
			{
			  "initial_step": "step_applicant",
			  "steps": [
			    {"id":"step_applicant","name":"申請人送審","type":"normal",
			     "role_code":"ROLE_DEPT_USER","next":"step_manager","reject_target":null},
			    {"id":"step_manager","name":"部門主管審核","type":"normal",
			     "role_code":"ROLE_DEPT_ADMIN","next":"step_property","reject_target":"step_applicant"},
			    {"id":"step_property","name":"財產管理審核","type":"normal",
			     "role_code":"ROLE_PROPERTY_MANAGER","next":"step_end","reject_target":"step_manager"},
			    {"id":"step_end","name":"結案","type":"end",
			     "role_code":null,"next":null,"reject_target":null}
			  ]
			}
			""";

	@Mock
	private WorkflowDefinitionRepository definitionRepo;

	@Mock
	private WorkflowInstanceRepository instanceRepo;

	@Mock
	private WorkflowStepLogRepository stepLogRepo;

	@Mock
	private IAssigneeResolver assigneeResolver;

	@Mock
	private ApplicationEventPublisher eventPublisher;

	@Spy
	private ObjectMapper objectMapper = new ObjectMapper();

	@InjectMocks
	private WorkflowEngine engine;

	private WorkflowDefinitionEntity def;

	@BeforeEach
	void setUp() {
		def = WorkflowDefinitionEntity.builder()
			.id(1L)
			.code("asset_transfer")
			.name("資產異動審核")
			.stepsJson(STEPS_JSON)
			.build();

		when(assigneeResolver.resolve(any(), any())).thenAnswer(inv -> {
			var stepDef = inv.getArgument(0, com.taipei.iot.workflow.model.StepDefinition.class);
			if ("ROLE_DEPT_USER".equals(stepDef.getRoleCode())) {
				return "user_dept_user_001";
			}
			return "user_dept_admin_001";
		});

		when(instanceRepo.save(any())).thenAnswer(inv -> {
			WorkflowInstanceEntity e = inv.getArgument(0);
			if (e.getId() == null) {
				// 模擬 DB 自動生成 ID
				var field = WorkflowInstanceEntity.class.getDeclaredField("id");
				field.setAccessible(true);
				field.set(e, 1L);
			}
			return e;
		});

		when(stepLogRepo.save(any())).thenAnswer(inv -> {
			WorkflowStepLogEntity l = inv.getArgument(0);
			if (l.getId() == null) {
				var field = WorkflowStepLogEntity.class.getDeclaredField("id");
				field.setAccessible(true);
				field.set(l, 1L);
			}
			return l;
		});

		when(definitionRepo.findById(1L)).thenReturn(Optional.of(def));
	}

	// ── start() ────────────────────────────────────────────────────────────────

	@Test
	void start_shouldCreateInstanceAtFirstStep() {
		when(definitionRepo.findLatestEnabledByCode("asset_transfer")).thenReturn(Optional.of(def));

		WorkflowContext ctx = WorkflowContext.builder()
			.businessId("BIZ-001")
			.businessType("ASSET_TRANSFER")
			.applicantId("user_dept_user_001")
			.build();

		WorkflowInstanceEntity result = engine.start("asset_transfer", "BIZ-001", "ASSET_TRANSFER", ctx);

		assertThat(result.getCurrentStepId()).isEqualTo("step_applicant");
		assertThat(result.getStatus()).isEqualTo(WorkflowStatus.IN_PROGRESS);
	}

	// ── approve() ──────────────────────────────────────────────────────────────

	@Test
	void approve_shouldAdvanceToNextStep() {
		WorkflowInstanceEntity instance = buildInstance("step_applicant");
		WorkflowStepLogEntity log = buildLog(instance.getId(), "step_applicant", "申請人送審", "user_dept_user_001");

		when(instanceRepo.findByIdForUpdate(1L)).thenReturn(Optional.of(instance));
		when(instanceRepo.findById(1L)).thenReturn(Optional.of(instance));
		when(stepLogRepo.findCurrentByInstanceId(1L)).thenReturn(Optional.of(log));
		when(definitionRepo.findById(1L)).thenReturn(Optional.of(def));

		WorkflowInstanceEntity result = engine.approve(1L, "同意", "user_dept_user_001");

		assertThat(result.getCurrentStepId()).isEqualTo("step_manager");
	}

	@Test
	void approve_shouldThrowPermissionException_whenWrongUser() {
		WorkflowInstanceEntity instance = buildInstance("step_applicant");
		WorkflowStepLogEntity log = buildLog(instance.getId(), "step_applicant", "申請人送審", "user_dept_user_001");

		when(instanceRepo.findByIdForUpdate(1L)).thenReturn(Optional.of(instance));
		when(stepLogRepo.findCurrentByInstanceId(1L)).thenReturn(Optional.of(log));

		assertThatThrownBy(() -> engine.approve(1L, "同意", "wrong_user"))
			.isInstanceOf(WorkflowPermissionException.class);
	}

	@Test
	void approve_shouldThrowException_whenStepAlreadyCompleted() {
		WorkflowInstanceEntity instance = buildInstance("step_applicant");
		WorkflowStepLogEntity log = buildLog(instance.getId(), "step_applicant", "申請人送審", "user_dept_user_001");
		log.setCompletedAt(LocalDateTime.now()); // 已完成

		when(instanceRepo.findByIdForUpdate(1L)).thenReturn(Optional.of(instance));
		when(stepLogRepo.findCurrentByInstanceId(1L)).thenReturn(Optional.of(log));

		assertThatThrownBy(() -> engine.approve(1L, "同意", "user_dept_user_001"))
			.isInstanceOf(WorkflowStepAlreadyCompletedException.class);
	}

	@Test
	void approve_shouldCompleteInstance_whenApproveEndStep() {
		WorkflowInstanceEntity instance = buildInstance("step_end");
		WorkflowStepLogEntity log = buildLog(instance.getId(), "step_end", "結案", null);

		when(instanceRepo.findByIdForUpdate(1L)).thenReturn(Optional.of(instance));
		when(instanceRepo.findById(1L)).thenReturn(Optional.of(instance));
		when(stepLogRepo.findCurrentByInstanceId(1L)).thenReturn(Optional.of(log));
		when(definitionRepo.findById(1L)).thenReturn(Optional.of(def));

		// end step 的 assigneeUserId 為 null，允許任何人呼叫
		log.setAssigneeUserId(null);

		// 修改：end step 的 validateAssignee 邏輯 — 允許 null assignee 時任意人
		// 直接驗證引擎正確結案
		WorkflowInstanceEntity result = engine.approve(1L, "結案", null);
		assertThat(result.getStatus()).isEqualTo(WorkflowStatus.COMPLETED);
	}

	// ── reject() ───────────────────────────────────────────────────────────────

	@Test
	void reject_shouldRollbackToTargetStep() {
		WorkflowInstanceEntity instance = buildInstance("step_manager");
		WorkflowStepLogEntity log = buildLog(instance.getId(), "step_manager", "部門主管審核", "user_dept_admin_001");

		when(instanceRepo.findByIdForUpdate(1L)).thenReturn(Optional.of(instance));
		when(instanceRepo.findById(1L)).thenReturn(Optional.of(instance));
		when(stepLogRepo.findCurrentByInstanceId(1L)).thenReturn(Optional.of(log));
		when(definitionRepo.findById(1L)).thenReturn(Optional.of(def));

		WorkflowInstanceEntity result = engine.reject(1L, "step_applicant", "退回補件", "user_dept_admin_001");

		assertThat(result.getCurrentStepId()).isEqualTo("step_applicant");
	}

	@Test
	void reject_shouldThrowInvalidTransition_whenTargetNotAllowed() {
		WorkflowInstanceEntity instance = buildInstance("step_manager");
		WorkflowStepLogEntity log = buildLog(instance.getId(), "step_manager", "部門主管審核", "user_dept_admin_001");

		when(instanceRepo.findByIdForUpdate(1L)).thenReturn(Optional.of(instance));
		when(stepLogRepo.findCurrentByInstanceId(1L)).thenReturn(Optional.of(log));

		// step_manager 只能退回 step_applicant，不能退回 step_property
		assertThatThrownBy(() -> engine.reject(1L, "step_property", "不合法退回", "user_dept_admin_001"))
			.isInstanceOf(WorkflowInvalidTransitionException.class);
	}

	// ── resubmit() ─────────────────────────────────────────────────────────────

	@Test
	void resubmit_shouldReturnToRejectSourceStep() {
		WorkflowInstanceEntity instance = buildInstance("step_applicant");
		WorkflowStepLogEntity currentLog = buildLog(instance.getId(), "step_applicant", "申請人送審", "user_dept_user_001");

		WorkflowStepLogEntity rejectLog = buildLog(instance.getId(), "step_manager", "部門主管審核", "user_dept_admin_001");
		rejectLog.setAction(WorkflowAction.REJECT);
		rejectLog.setCompletedAt(LocalDateTime.now());

		when(instanceRepo.findByIdForUpdate(1L)).thenReturn(Optional.of(instance));
		when(instanceRepo.findById(1L)).thenReturn(Optional.of(instance));
		when(stepLogRepo.findCurrentByInstanceId(1L)).thenReturn(Optional.of(currentLog));
		when(stepLogRepo.findByWorkflowInstanceIdOrderByEnteredAtAsc(1L)).thenReturn(List.of(rejectLog, currentLog));
		when(definitionRepo.findById(1L)).thenReturn(Optional.of(def));

		WorkflowInstanceEntity result = engine.resubmit(1L, "已補件", "user_dept_user_001");

		assertThat(result.getCurrentStepId()).isEqualTo("step_manager");
	}

	@Test
	void resubmit_shouldThrow_whenNoRejectHistory() {
		WorkflowInstanceEntity instance = buildInstance("step_applicant");
		WorkflowStepLogEntity currentLog = buildLog(instance.getId(), "step_applicant", "申請人送審", "user_dept_user_001");

		when(instanceRepo.findByIdForUpdate(1L)).thenReturn(Optional.of(instance));
		when(stepLogRepo.findCurrentByInstanceId(1L)).thenReturn(Optional.of(currentLog));
		when(stepLogRepo.findByWorkflowInstanceIdOrderByEnteredAtAsc(1L)).thenReturn(List.of(currentLog));

		assertThatThrownBy(() -> engine.resubmit(1L, "補件", "user_dept_user_001"))
			.isInstanceOf(WorkflowNoRejectHistoryException.class);
	}

	@Test
	void resubmit_shouldReturnToLastRejectSourceStep_whenMultipleRejects() {
		// 情境：財產管理退回 → 主管退回 → 申請人補件，resubmit 應回到「最後退回的發起方」即主管
		WorkflowInstanceEntity instance = buildInstance("step_applicant");
		WorkflowStepLogEntity currentLog = buildLog(instance.getId(), "step_applicant", "申請人送審", "user_dept_user_001");

		// 第一次退回：財產管理退回給主管
		WorkflowStepLogEntity rejectByProperty = buildLog(instance.getId(), "step_property", "財產管理審核",
				"user_property_001");
		rejectByProperty.setAction(WorkflowAction.REJECT);
		rejectByProperty.setCompletedAt(LocalDateTime.now().minusHours(2));

		// 第二次退回：主管退回給申請人（最後一筆）
		WorkflowStepLogEntity rejectByManager = buildLog(instance.getId(), "step_manager", "部門主管審核",
				"user_dept_admin_001");
		rejectByManager.setAction(WorkflowAction.REJECT);
		rejectByManager.setCompletedAt(LocalDateTime.now().minusHours(1));

		when(instanceRepo.findByIdForUpdate(1L)).thenReturn(Optional.of(instance));
		when(instanceRepo.findById(1L)).thenReturn(Optional.of(instance));
		when(stepLogRepo.findCurrentByInstanceId(1L)).thenReturn(Optional.of(currentLog));
		when(stepLogRepo.findByWorkflowInstanceIdOrderByEnteredAtAsc(1L))
			.thenReturn(List.of(rejectByProperty, rejectByManager, currentLog));
		when(definitionRepo.findById(1L)).thenReturn(Optional.of(def));

		WorkflowInstanceEntity result = engine.resubmit(1L, "已補件", "user_dept_user_001");

		// 應回到最後退回的發起方：主管（step_manager），而非第一次退回的財產管理（step_property）
		assertThat(result.getCurrentStepId()).isEqualTo("step_manager");
	}

	// ── cancel() ──────────────────────────────────────────────────────────────

	@Test
	void cancel_byApplicant_shouldSetStatusToCancelled() {
		WorkflowInstanceEntity instance = buildInstance("step_manager");
		instance.setContextJson("{\"applicantId\":\"user_dept_user_001\"}");
		WorkflowStepLogEntity currentLog = buildLog(instance.getId(), "step_manager", "部門主管審核", "user_dept_admin_001");

		when(instanceRepo.findByIdForUpdate(1L)).thenReturn(Optional.of(instance));
		when(instanceRepo.findById(1L)).thenReturn(Optional.of(instance));
		when(stepLogRepo.findCurrentByInstanceId(1L)).thenReturn(Optional.of(currentLog));
		when(definitionRepo.findById(1L)).thenReturn(Optional.of(def));

		WorkflowInstanceEntity result = engine.cancel(1L, "業務調整取消", "user_dept_user_001");

		assertThat(result.getStatus()).isEqualTo(WorkflowStatus.CANCELLED);
		assertThat(currentLog.getAction()).isEqualTo(WorkflowAction.CANCEL);
		assertThat(currentLog.getCompletedAt()).isNotNull();
	}

	@Test
	void cancel_byNonApplicant_shouldThrowPermissionException() {
		WorkflowInstanceEntity instance = buildInstance("step_manager");
		instance.setContextJson("{\"applicantId\":\"user_dept_user_001\"}");
		WorkflowStepLogEntity currentLog = buildLog(instance.getId(), "step_manager", "部門主管審核", "user_dept_admin_001");

		when(instanceRepo.findByIdForUpdate(1L)).thenReturn(Optional.of(instance));
		when(stepLogRepo.findCurrentByInstanceId(1L)).thenReturn(Optional.of(currentLog));

		assertThatThrownBy(() -> engine.cancel(1L, "取消", "wrong_user")).isInstanceOf(WorkflowPermissionException.class);
	}

	@Test
	void cancel_whenInstanceAlreadyCompleted_shouldThrowException() {
		WorkflowInstanceEntity instance = buildInstance("step_end");
		instance.setStatus(WorkflowStatus.COMPLETED);

		when(instanceRepo.findByIdForUpdate(1L)).thenReturn(Optional.of(instance));

		assertThatThrownBy(() -> engine.cancel(1L, "取消", "user_dept_user_001"))
			.isInstanceOf(WorkflowInstanceNotInProgressException.class);
	}

	@Test
	void cancel_whenCurrentLogAlreadyCompleted_shouldThrowException() {
		WorkflowInstanceEntity instance = buildInstance("step_manager");
		instance.setContextJson("{\"applicantId\":\"user_dept_user_001\"}");
		WorkflowStepLogEntity currentLog = buildLog(instance.getId(), "step_manager", "部門主管審核", "user_dept_admin_001");
		currentLog.setCompletedAt(LocalDateTime.now());

		when(instanceRepo.findByIdForUpdate(1L)).thenReturn(Optional.of(instance));
		when(stepLogRepo.findCurrentByInstanceId(1L)).thenReturn(Optional.of(currentLog));

		assertThatThrownBy(() -> engine.cancel(1L, "取消", "user_dept_user_001"))
			.isInstanceOf(WorkflowStepAlreadyCompletedException.class);
	}

	// ── helpers ────────────────────────────────────────────────────────────────

	private WorkflowInstanceEntity buildInstance(String currentStepId) {
		return WorkflowInstanceEntity.builder()
			.id(1L)
			.workflowDefId(1L)
			.businessId("BIZ-001")
			.businessType("ASSET_TRANSFER")
			.currentStepId(currentStepId)
			.status(WorkflowStatus.IN_PROGRESS)
			.build();
	}

	private WorkflowStepLogEntity buildLog(Long instanceId, String stepId, String stepName, String assignee) {
		return WorkflowStepLogEntity.builder()
			.id(1L)
			.workflowInstanceId(instanceId)
			.stepId(stepId)
			.stepName(stepName)
			.assigneeUserId(assignee)
			.enteredAt(LocalDateTime.now())
			.build();
	}

}
