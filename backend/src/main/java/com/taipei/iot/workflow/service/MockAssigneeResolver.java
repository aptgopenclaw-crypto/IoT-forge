package com.taipei.iot.workflow.service;

import com.taipei.iot.tenant.TenantContext;
import com.taipei.iot.workflow.exception.WorkflowNotFoundException;
import com.taipei.iot.workflow.model.StepDefinition;
import com.taipei.iot.workflow.model.WorkflowContext;
import com.taipei.iot.workflow.repository.DelegateSettingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Map;

/**
 * POC 階段的 AssigneeResolver，使用固定角色對照表。
 * <p>
 * 已由 {@link OrgAssigneeResolver}（@Primary）取代，此類保留供單元測試直接實例化使用。<br>
 * 角色對照：<br>
 * ROLE_DEPT_USER → user_dept_user_001 （申請人）<br>
 * ROLE_DEPT_ADMIN → user_dept_admin_001（部門主管／財產管理）<br>
 * ROLE_FIELD_USER → user_field_001<br>
 * ROLE_OPERATOR → user_operator_001<br>
 * ROLE_MONITOR → user_monitor_001<br>
 */
@Slf4j
@RequiredArgsConstructor
public class MockAssigneeResolver implements IAssigneeResolver {

	private static final Map<String, String> ROLE_MAP = Map.of(
			// UC-1 use case IDs
			"ROLE_DEPT_USER", "f75a999a-6fc4-4b0f-a719-bc51b24a439f", // 申請人
			"ROLE_DEPT_ADMIN", "66f19b01-291a-4e4b-a15f-81ceb4a85675", // 部門主管
			"ROLE_PROPERTY_MANAGER", "d34b59ec-bd42-4f6e-b3aa-4f1c6aaa0e63", // 財產管理組
			"ROLE_FIELD_USER", "user_field_001", "ROLE_OPERATOR", "user_operator_001", "ROLE_MONITOR",
			"user_monitor_001");

	private final DelegateSettingRepository delegateSettingRepository;

	@Override
	public String resolve(StepDefinition stepDef, WorkflowContext context) {
		String roleCode = stepDef.getRoleCode();
		if (roleCode == null) {
			throw new WorkflowNotFoundException("步驟 [" + stepDef.getId() + "] 缺少 role_code");
		}

		String assignee = ROLE_MAP.get(roleCode);
		if (assignee == null) {
			throw new WorkflowNotFoundException("找不到角色對應的人員：" + roleCode);
		}

		// 代理人覆寫
		return delegateSettingRepository
			.findActiveDelegate(TenantContext.getCurrentTenantId(), assignee, context.getBusinessType(),
					LocalDate.now())
			.map(delegate -> {
				String delegateTo = delegate.getDelegateTo();

				// 利益衝突檢查：代理人不能審核自己發起的案件
				if (delegateTo.equals(context.getApplicantId())) {
					log.warn("[Delegate] 代理人 {} 與申請人相同，跳過代理，使用原審核人 {}", delegateTo, assignee);
					return assignee;
				}

				log.info("[Delegate] {} → {} (business_type={})", assignee, delegateTo, context.getBusinessType());
				return delegateTo;
			})
			.orElse(assignee);
	}

}
