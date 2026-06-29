package com.taipei.iot.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taipei.iot.workflow.entity.WorkflowDefinitionEntity;
import com.taipei.iot.workflow.entity.WorkflowInstanceEntity;
import com.taipei.iot.workflow.entity.WorkflowStepLogEntity;
import com.taipei.iot.workflow.model.StepDefinition;
import com.taipei.iot.workflow.model.WorkflowAction;
import com.taipei.iot.workflow.model.WorkflowContext;
import com.taipei.iot.workflow.model.WorkflowStatus;
import com.taipei.iot.workflow.repository.WorkflowDefinitionRepository;
import com.taipei.iot.workflow.repository.WorkflowInstanceRepository;
import com.taipei.iot.workflow.repository.WorkflowStepLogRepository;
import com.taipei.iot.workflow.service.IAssigneeResolver;
import com.taipei.iot.workflow.service.WorkflowEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * UC-1：正常流程自動化測試
 * <p>
 * 驗證 asset_transfer 流程的完整通過路徑：<br>
 * 申請人送審（user1）→ 部門主管審核（admin1）→ 財產管理審核（admin2）→ 結案
 *
 * <p>
 * 對應 use case：01-docs/workflow/UseCase-1/user-case.md
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("UC-1: 資產異動 正常流程（申請 → 主管 → 財產管理 → 結案）")
class WorkflowUseCase1Test {

	// ── 對應 use case 的實際使用者 ID ───────────────────────────────────────────
	/** 申請人：user1@flow.com, dept_id=12 */
	private static final String APPLICANT_ID = "f75a999a-6fc4-4b0f-a719-bc51b24a439f";

	/** 部門主管：admin1@flow.com, dept_id=12 */
	private static final String MANAGER_ID = "66f19b01-291a-4e4b-a15f-81ceb4a85675";

	/** 財產管理：admin2@flow.com, dept_id=13 */
	private static final String PROPERTY_MANAGER_ID = "d34b59ec-bd42-4f6e-b3aa-4f1c6aaa0e63";

	// ── 流程定義 JSON（4 步驟 asset_transfer）──────────────────────────────────
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

	@Spy
	private ObjectMapper objectMapper = new ObjectMapper();

	@Mock
	private ApplicationEventPublisher eventPublisher;

	private WorkflowEngine engine;

	// 模擬資料庫的 in-memory 儲存
	private WorkflowInstanceEntity savedInstance;

	private final List<WorkflowStepLogEntity> logStore = new ArrayList<>();

	private final AtomicLong logIdSeq = new AtomicLong(1);

	@BeforeEach
	void setUp() throws Exception {
		// AssigneeResolver：依 role_code 對應 use case 中的實際 userId
		IAssigneeResolver resolver = (StepDefinition step, WorkflowContext ctx) -> switch (step.getRoleCode()) {
			case "ROLE_DEPT_USER" -> APPLICANT_ID;
			case "ROLE_DEPT_ADMIN" -> MANAGER_ID;
			case "ROLE_PROPERTY_MANAGER" -> PROPERTY_MANAGER_ID;
			default -> throw new IllegalArgumentException("Unknown role: " + step.getRoleCode());
		};

		engine = new WorkflowEngine(definitionRepo, instanceRepo, stepLogRepo, resolver, objectMapper, eventPublisher);

		WorkflowDefinitionEntity def = WorkflowDefinitionEntity.builder()
			.id(1L)
			.code("asset_transfer")
			.name("資產異動審核")
			.stepsJson(STEPS_JSON)
			.build();

		when(definitionRepo.findLatestEnabledByCode("asset_transfer")).thenReturn(Optional.of(def));
		when(definitionRepo.findById(1L)).thenReturn(Optional.of(def));

		// instance save/findById：操作同一個 savedInstance 物件
		when(instanceRepo.save(any())).thenAnswer(inv -> {
			WorkflowInstanceEntity e = inv.getArgument(0);
			if (e.getId() == null) {
				var f = WorkflowInstanceEntity.class.getDeclaredField("id");
				f.setAccessible(true);
				f.set(e, 1L);
			}
			savedInstance = e;
			return e;
		});
		when(instanceRepo.findById(1L)).thenAnswer(inv -> Optional.ofNullable(savedInstance));
		when(instanceRepo.findByIdForUpdate(1L)).thenAnswer(inv -> Optional.ofNullable(savedInstance));

		// stepLog save：存入 logStore
		when(stepLogRepo.save(any())).thenAnswer(inv -> {
			WorkflowStepLogEntity l = inv.getArgument(0);
			if (l.getId() == null) {
				var f = WorkflowStepLogEntity.class.getDeclaredField("id");
				f.setAccessible(true);
				f.set(l, logIdSeq.getAndIncrement());
				logStore.add(l);
			}
			return l;
		});

		// findCurrentByInstanceId：找 completedAt == null 的最新一筆
		when(stepLogRepo.findCurrentByInstanceId(1L))
			.thenAnswer(inv -> logStore.stream().filter(l -> l.getCompletedAt() == null).reduce((a, b) -> b));

		// findByWorkflowInstanceIdOrderByEnteredAtAsc：回傳全部 log
		when(stepLogRepo.findByWorkflowInstanceIdOrderByEnteredAtAsc(1L)).thenAnswer(inv -> new ArrayList<>(logStore));
	}

	@Test
	@DisplayName("UC-1 完整正常流程：申請 → 主管審核 → 財產管理審核 → 結案")
	void uc1_normalFlow_shouldCompleteSuccessfully() {
		// ── Step 1: 啟動流程 ─────────────────────────────────────────────────────
		WorkflowContext context = WorkflowContext.builder()
			.businessId("UC1-001")
			.businessType("ASSET_TRANSFER")
			.applicantId(APPLICANT_ID)
			.departmentId("12")
			.build();

		WorkflowInstanceEntity instance = engine.start("asset_transfer", "UC1-001", "ASSET_TRANSFER", context);

		assertThat(instance.getCurrentStepId()).isEqualTo("step_applicant");
		assertThat(instance.getStatus()).isEqualTo(WorkflowStatus.IN_PROGRESS);

		WorkflowStepLogEntity firstLog = currentLog();
		assertThat(firstLog.getStepName()).isEqualTo("申請人送審");
		assertThat(firstLog.getAssigneeUserId()).isEqualTo(APPLICANT_ID);

		// ── Step 2: 申請人送審 ───────────────────────────────────────────────────
		instance = engine.approve(1L, "申請資產移轉", APPLICANT_ID);

		assertThat(instance.getCurrentStepId()).isEqualTo("step_manager");
		assertThat(instance.getStatus()).isEqualTo(WorkflowStatus.IN_PROGRESS);

		WorkflowStepLogEntity managerLog = currentLog();
		assertThat(managerLog.getStepName()).isEqualTo("部門主管審核");
		assertThat(managerLog.getAssigneeUserId()).isEqualTo(MANAGER_ID);

		// ── Step 3: 主管審核通過 ─────────────────────────────────────────────────
		instance = engine.approve(1L, "同意", MANAGER_ID);

		assertThat(instance.getCurrentStepId()).isEqualTo("step_property");
		assertThat(instance.getStatus()).isEqualTo(WorkflowStatus.IN_PROGRESS);

		WorkflowStepLogEntity propertyLog = currentLog();
		assertThat(propertyLog.getStepName()).isEqualTo("財產管理審核");
		assertThat(propertyLog.getAssigneeUserId()).isEqualTo(PROPERTY_MANAGER_ID);

		// ── Step 4: 財產管理審核通過 → 結案 ─────────────────────────────────────
		instance = engine.approve(1L, "核准", PROPERTY_MANAGER_ID);

		assertThat(instance.getStatus()).isEqualTo(WorkflowStatus.COMPLETED);

		// ── 驗證歷程完整性 ────────────────────────────────────────────────────────
		List<WorkflowStepLogEntity> history = engine.getHistory(1L);
		// step_applicant + step_manager + step_property（end step 不建立 log）
		assertThat(history).hasSize(3);

		// 驗證每一步驟的 action 正確記錄
		assertThat(history.get(0).getStepId()).isEqualTo("step_applicant");
		assertThat(history.get(0).getAssigneeUserId()).isEqualTo(APPLICANT_ID);
		assertThat(history.get(0).getAction()).isEqualTo(WorkflowAction.APPROVE);

		assertThat(history.get(1).getStepId()).isEqualTo("step_manager");
		assertThat(history.get(1).getAssigneeUserId()).isEqualTo(MANAGER_ID);
		assertThat(history.get(1).getAction()).isEqualTo(WorkflowAction.APPROVE);

		assertThat(history.get(2).getStepId()).isEqualTo("step_property");
		assertThat(history.get(2).getAssigneeUserId()).isEqualTo(PROPERTY_MANAGER_ID);
		assertThat(history.get(2).getAction()).isEqualTo(WorkflowAction.APPROVE);
	}

	// ── helper ────────────────────────────────────────────────────────────────

	private WorkflowStepLogEntity currentLog() {
		return logStore.stream()
			.filter(l -> l.getCompletedAt() == null)
			.reduce((a, b) -> b)
			.orElseThrow(() -> new AssertionError("找不到進行中的步驟 log"));
	}

}
