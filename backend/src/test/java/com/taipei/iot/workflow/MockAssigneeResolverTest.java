package com.taipei.iot.workflow;

import com.taipei.iot.workflow.entity.DelegateSettingEntity;
import com.taipei.iot.workflow.model.StepDefinition;
import com.taipei.iot.workflow.model.WorkflowContext;
import com.taipei.iot.workflow.model.WorkflowStepType;
import com.taipei.iot.workflow.repository.DelegateSettingRepository;
import com.taipei.iot.workflow.service.MockAssigneeResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MockAssigneeResolverTest {

	@Mock
	private DelegateSettingRepository delegateSettingRepository;

	@InjectMocks
	private MockAssigneeResolver resolver;

	// UC-1 IDs — must match MockAssigneeResolver.ROLE_MAP
	private static final String APPLICANT_ID = "f75a999a-6fc4-4b0f-a719-bc51b24a439f";

	private static final String DEPT_ADMIN_ID = "66f19b01-291a-4e4b-a15f-81ceb4a85675";

	private WorkflowContext context;

	@BeforeEach
	void setUp() {
		context = WorkflowContext.builder()
			.businessId("BIZ-001")
			.businessType("ASSET_TRANSFER")
			.applicantId(APPLICANT_ID)
			.build();

		when(delegateSettingRepository.findActiveDelegate(any(), any(), any(), any())).thenReturn(Optional.empty());
	}

	// ── 基本角色解析 ────────────────────────────────────────────────────────────

	@Test
	void resolve_shouldReturnMappedUser_forKnownRole() {
		StepDefinition step = step("step_applicant", "ROLE_DEPT_USER");
		assertThat(resolver.resolve(step, context)).isEqualTo(APPLICANT_ID);
	}

	@Test
	void resolve_shouldReturnDeptAdmin_forManagerRole() {
		StepDefinition step = step("step_manager", "ROLE_DEPT_ADMIN");
		assertThat(resolver.resolve(step, context)).isEqualTo(DEPT_ADMIN_ID);
	}

	@Test
	void resolve_shouldThrow_forUnknownRole() {
		StepDefinition step = step("step_x", "ROLE_UNKNOWN");
		assertThatThrownBy(() -> resolver.resolve(step, context))
			.isInstanceOf(com.taipei.iot.workflow.exception.WorkflowNotFoundException.class);
	}

	@Test
	void resolve_shouldThrow_whenRoleCodeIsNull() {
		StepDefinition step = step("step_end", null);
		assertThatThrownBy(() -> resolver.resolve(step, context))
			.isInstanceOf(com.taipei.iot.workflow.exception.WorkflowNotFoundException.class);
	}

	// ── 代理人覆寫 ──────────────────────────────────────────────────────────────

	@Test
	void resolve_shouldReturnDelegate_whenActiveDelegateExists() {
		DelegateSettingEntity delegate = DelegateSettingEntity.builder()
			.delegateFor(DEPT_ADMIN_ID)
			.delegateTo("user_deputy_001")
			.businessType("ASSET_TRANSFER")
			.effectiveFrom(LocalDate.now().minusDays(1))
			.effectiveTo(LocalDate.now().plusDays(10))
			.build();

		when(delegateSettingRepository.findActiveDelegate(any(), eq(DEPT_ADMIN_ID), any(), any()))
			.thenReturn(Optional.of(delegate));

		StepDefinition step = step("step_manager", "ROLE_DEPT_ADMIN");
		assertThat(resolver.resolve(step, context)).isEqualTo("user_deputy_001");
	}

	@Test
	void resolve_shouldSkipDelegate_whenDelegateIsApplicant() {
		// 代理人與申請人相同 → 跳過代理
		DelegateSettingEntity delegate = DelegateSettingEntity.builder()
			.delegateFor(DEPT_ADMIN_ID)
			.delegateTo(APPLICANT_ID) // 與 applicantId 相同
			.effectiveFrom(LocalDate.now().minusDays(1))
			.effectiveTo(LocalDate.now().plusDays(10))
			.build();

		when(delegateSettingRepository.findActiveDelegate(any(), eq(DEPT_ADMIN_ID), any(), any()))
			.thenReturn(Optional.of(delegate));

		StepDefinition step = step("step_manager", "ROLE_DEPT_ADMIN");
		// 應回傳原審核人，而非代理人
		assertThat(resolver.resolve(step, context)).isEqualTo(DEPT_ADMIN_ID);
	}

	// ── helpers ────────────────────────────────────────────────────────────────

	private StepDefinition step(String id, String roleCode) {
		return StepDefinition.builder().id(id).name("test").type(WorkflowStepType.NORMAL).roleCode(roleCode).build();
	}

}
