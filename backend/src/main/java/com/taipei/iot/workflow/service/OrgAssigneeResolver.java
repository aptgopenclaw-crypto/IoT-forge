package com.taipei.iot.workflow.service;

import com.taipei.iot.auth.entity.RoleEntity;
import com.taipei.iot.auth.entity.UserTenantMappingEntity;
import com.taipei.iot.auth.repository.RoleRepository;
import com.taipei.iot.auth.repository.UserTenantMappingRepository;
import com.taipei.iot.tenant.TenantContext;
import com.taipei.iot.workflow.exception.WorkflowNotFoundException;
import com.taipei.iot.workflow.model.StepDefinition;
import com.taipei.iot.workflow.model.WorkflowContext;
import com.taipei.iot.workflow.repository.DelegateSettingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * 正式組織適配層實作。
 * <p>
 * 解析邏輯：<br>
 * ROLE_DEPT_USER → 直接從 context.applicantId 取得（申請人自己）<br>
 * ROLE_DEPT_ADMIN → 查詢 user_tenant_mapping：指定 tenant + dept + role 的管理者<br>
 * 其他 role → 查詢 user_tenant_mapping：指定 tenant + role（跨部門）<br>
 * 所有結果再套用代理人覆寫邏輯。
 */
@Slf4j
@Service
@Primary
@RequiredArgsConstructor
public class OrgAssigneeResolver implements IAssigneeResolver {

	private final RoleRepository roleRepository;

	private final UserTenantMappingRepository userTenantMappingRepository;

	private final DelegateSettingRepository delegateSettingRepository;

	@Override
	public String resolve(StepDefinition stepDef, WorkflowContext context) {
		String roleCode = stepDef.getRoleCode();
		if (roleCode == null) {
			// end 類型步驟不需要審核人
			return null;
		}

		String tenantId = TenantContext.getCurrentTenantId();
		String assignee = resolveByRole(roleCode, tenantId, context);
		return applyDelegate(assignee, context);
	}

	// ── Private helpers ────────────────────────────────────────────────────────

	private String resolveByRole(String roleCode, String tenantId, WorkflowContext context) {
		return switch (roleCode) {
			case "ROLE_DEPT_USER" -> {
				// 申請人步驟：直接從業務上下文取得
				if (context.getApplicantId() == null) {
					throw new WorkflowNotFoundException("context.applicantId 不可為空（步驟 role_code=ROLE_DEPT_USER）");
				}
				yield context.getApplicantId();
			}
			case "ROLE_DEPT_ADMIN" -> {
				// 部門主管：同一 tenant + 同一 dept + 指定 role
				RoleEntity role = findRole(roleCode);
				Long deptId = parseDeptId(context);
				List<UserTenantMappingEntity> mappings = userTenantMappingRepository
					.findByTenantIdAndDeptIdAndRoleIdAndEnabledTrueOrderByUserIdAsc(tenantId, deptId, role.getRoleId());
				yield firstUser(mappings, roleCode);
			}
			default -> {
				// 跨部門角色（如 ROLE_PROPERTY_MANAGER）：tenant + role
				RoleEntity role = findRole(roleCode);
				List<UserTenantMappingEntity> mappings = userTenantMappingRepository
					.findByTenantIdAndRoleIdAndEnabledTrueOrderByUserIdAsc(tenantId, role.getRoleId());
				yield firstUser(mappings, roleCode);
			}
		};
	}

	private RoleEntity findRole(String roleCode) {
		// workflow steps_json 的 role_code 對應 roles.role_id（如 ROLE_DEPT_ADMIN）
		// roles.code 欄位是不帶前綴的簡稱（如 DEPT_ADMIN），不應用於此查詢
		return roleRepository.findById(roleCode)
			.orElseThrow(() -> new WorkflowNotFoundException("找不到角色定義：" + roleCode));
	}

	private Long parseDeptId(WorkflowContext context) {
		if (context.getDepartmentId() == null) {
			throw new WorkflowNotFoundException("context.departmentId 不可為空（步驟 role_code=ROLE_DEPT_ADMIN）");
		}
		try {
			return Long.valueOf(context.getDepartmentId());
		}
		catch (NumberFormatException e) {
			throw new WorkflowNotFoundException("context.departmentId 必須為數字，收到：" + context.getDepartmentId());
		}
	}

	private String firstUser(List<UserTenantMappingEntity> mappings, String roleCode) {
		if (mappings.isEmpty()) {
			throw new WorkflowNotFoundException(
					"找不到符合條件的審核人 [role=" + roleCode + ", tenant=" + TenantContext.getCurrentTenantId() + "]");
		}
		return mappings.get(0).getUserId();
	}

	private String applyDelegate(String assignee, WorkflowContext context) {
		if (assignee == null) {
			return null;
		}
		return delegateSettingRepository
			.findActiveDelegate(TenantContext.getCurrentTenantId(), assignee, context.getBusinessType(),
					LocalDate.now())
			.map(delegate -> {
				String delegateTo = delegate.getDelegateTo();
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
