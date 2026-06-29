package com.taipei.iot.workflow.controller;

import com.taipei.iot.common.response.BaseResponse;
import com.taipei.iot.common.util.SecurityContextUtils;
import com.taipei.iot.workflow.dto.DelegateSetRequest;
import com.taipei.iot.workflow.dto.WorkflowApproveRequest;
import com.taipei.iot.workflow.dto.WorkflowCancelRequest;
import com.taipei.iot.workflow.dto.WorkflowRejectRequest;
import com.taipei.iot.workflow.dto.WorkflowResubmitRequest;
import com.taipei.iot.workflow.dto.WorkflowStartRequest;
import com.taipei.iot.workflow.entity.DelegateSettingEntity;
import com.taipei.iot.workflow.entity.WorkflowInstanceEntity;
import com.taipei.iot.workflow.entity.WorkflowStepLogEntity;
import com.taipei.iot.workflow.model.WorkflowContext;
import com.taipei.iot.workflow.repository.DelegateSettingRepository;
import com.taipei.iot.workflow.exception.WorkflowPermissionException;
import com.taipei.iot.workflow.dto.WorkflowSlaDto;
import com.taipei.iot.workflow.service.WorkflowEngine;
import com.taipei.iot.workflow.service.WorkflowSlaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 簽核引擎 REST API。
 * <p>
 * 所有端點均要求已認證（路由層 {@code /v1/api/poc/**} → {@code authenticated()}， 方法層
 * {@code @PreAuthorize} 明示宣告），審核操作的 userId 由 SecurityContext 取得， 不允許由請求方自行指定。
 */
@RestController
@RequestMapping("/v1/api/poc/workflow")
@RequiredArgsConstructor
@Tag(name = "WorkflowPOC", description = "簽核流程 POC：啟動、審核、退回、重送與代理設定")
public class WorkflowPocController {

	private final WorkflowEngine workflowEngine;

	private final WorkflowSlaService workflowSlaService;

	private final DelegateSettingRepository delegateSettingRepository;

	@PostMapping("/start")
	@PreAuthorize("isAuthenticated()")
	@Operation(summary = "啟動流程", description = "依 workflowCode 建立新的流程實例並寫入第一個待辦步驟")
	public BaseResponse<WorkflowInstanceEntity> start(@Valid @RequestBody WorkflowStartRequest req) {
		String applicantId = SecurityContextUtils.requireCurrentUserIdStrict();
		WorkflowContext context = WorkflowContext.builder()
			.businessId(req.businessId())
			.businessType(req.businessType())
			.applicantId(applicantId)
			.departmentId(req.departmentId())
			.district(req.district())
			.contractId(req.contractId())
			.build();
		WorkflowInstanceEntity result = workflowEngine.start(req.workflowCode(), req.businessId(), req.businessType(),
				context);
		return BaseResponse.success(result);
	}

	@PostMapping("/approve")
	@PreAuthorize("isAuthenticated()")
	@Operation(summary = "流程審核通過", description = "將指定流程實例推進到下一步")
	public BaseResponse<WorkflowInstanceEntity> approve(@Valid @RequestBody WorkflowApproveRequest req) {
		String userId = SecurityContextUtils.requireCurrentUserIdStrict();
		WorkflowInstanceEntity result = workflowEngine.approve(req.instanceId(), req.comment(), userId);
		return BaseResponse.success(result);
	}

	@PostMapping("/reject")
	@PreAuthorize("isAuthenticated()")
	@Operation(summary = "流程退回", description = "將指定流程實例退回到 targetStepId")
	public BaseResponse<WorkflowInstanceEntity> reject(@Valid @RequestBody WorkflowRejectRequest req) {
		String userId = SecurityContextUtils.requireCurrentUserIdStrict();
		WorkflowInstanceEntity result = workflowEngine.reject(req.instanceId(), req.targetStepId(), req.comment(),
				userId);
		return BaseResponse.success(result);
	}

	@PostMapping("/resubmit")
	@PreAuthorize("isAuthenticated()")
	@Operation(summary = "流程重送", description = "將已退回的流程重送回最近一次退回來源步驟")
	public BaseResponse<WorkflowInstanceEntity> resubmit(@Valid @RequestBody WorkflowResubmitRequest req) {
		String userId = SecurityContextUtils.requireCurrentUserIdStrict();
		WorkflowInstanceEntity result = workflowEngine.resubmit(req.instanceId(), req.comment(), userId);
		return BaseResponse.success(result);
	}

	@PostMapping("/cancel")
	@PreAuthorize("isAuthenticated()")
	@Operation(summary = "取消流程", description = "申請人主動取消進行中的流程，僅限申請人本人操作")
	public BaseResponse<WorkflowInstanceEntity> cancel(@Valid @RequestBody WorkflowCancelRequest req) {
		String userId = SecurityContextUtils.requireCurrentUserIdStrict();
		WorkflowInstanceEntity result = workflowEngine.cancel(req.instanceId(), req.comment(), userId);
		return BaseResponse.success(result);
	}

	@GetMapping("/instance/{id}")
	@PreAuthorize("isAuthenticated()")
	@Operation(summary = "查詢流程實例", description = "依流程實例 ID 查詢目前狀態與流程資訊；僅申請人或曾任審核人可存取")
	public BaseResponse<WorkflowInstanceEntity> getInstance(@PathVariable Long id) {
		String currentUserId = SecurityContextUtils.requireCurrentUserIdStrict();
		WorkflowInstanceEntity instance = workflowEngine.getInstance(id);
		if (!workflowEngine.hasAccessToInstance(instance, currentUserId)) {
			throw new WorkflowPermissionException(currentUserId, "instance#" + id);
		}
		return BaseResponse.success(instance);
	}

	@GetMapping("/history/{id}")
	@PreAuthorize("isAuthenticated()")
	@Operation(summary = "查詢流程歷程", description = "回傳指定流程實例的步驟歷程清單；僅申請人或曾任審核人可存取")
	public BaseResponse<List<WorkflowStepLogEntity>> getHistory(@PathVariable Long id) {
		String currentUserId = SecurityContextUtils.requireCurrentUserIdStrict();
		WorkflowInstanceEntity instance = workflowEngine.getInstance(id);
		if (!workflowEngine.hasAccessToInstance(instance, currentUserId)) {
			throw new WorkflowPermissionException(currentUserId, "instance#" + id);
		}
		return BaseResponse.success(workflowEngine.getHistory(id));
	}

	@GetMapping("/sla/{id}")
	@PreAuthorize("isAuthenticated()")
	@Operation(summary = "查詢流程 SLA 時效 KPI", description = "回傳指定流程實例的 SLA 時效 KPI，包含整體及各步驟的預計天數與實際天數；僅申請人或曾任審核人可存取")
	public BaseResponse<WorkflowSlaDto> getSla(@PathVariable Long id) {
		String currentUserId = SecurityContextUtils.requireCurrentUserIdStrict();
		WorkflowInstanceEntity instance = workflowEngine.getInstance(id);
		if (!workflowEngine.hasAccessToInstance(instance, currentUserId)) {
			throw new WorkflowPermissionException(currentUserId, "instance#" + id);
		}
		return BaseResponse.success(workflowSlaService.getSla(id));
	}

	@PostMapping("/delegate")
	@PreAuthorize("isAuthenticated()")
	@Operation(summary = "設定代理人", description = "為當前登入使用者建立代理設定；delegateFor 由 SecurityContext 取得，不接受由請求方指定")
	public BaseResponse<DelegateSettingEntity> setDelegate(@Valid @RequestBody DelegateSetRequest req) {
		String currentUserId = SecurityContextUtils.requireCurrentUserIdStrict();
		if (currentUserId.equals(req.delegateTo())) {
			throw new IllegalArgumentException("代理人不可與被代理人相同");
		}
		DelegateSettingEntity entity = DelegateSettingEntity.builder()
			.delegateFor(currentUserId)
			.delegateTo(req.delegateTo())
			.businessType(req.businessType())
			.effectiveFrom(req.effectiveFrom())
			.effectiveTo(req.effectiveTo())
			.createdAt(LocalDateTime.now())
			.updatedAt(LocalDateTime.now())
			.build();
		return BaseResponse.success(delegateSettingRepository.save(entity));
	}

	@GetMapping("/delegate/my")
	@PreAuthorize("isAuthenticated()")
	@Operation(summary = "查詢我的代理設定", description = "回傳目前登入者的代理設定記錄，按建立時間倒排")
	public BaseResponse<List<DelegateSettingEntity>> myDelegates() {
		String currentUserId = SecurityContextUtils.requireCurrentUserIdStrict();
		return BaseResponse.success(delegateSettingRepository.findByDelegateForOrderByCreatedAtDesc(currentUserId));
	}

}
