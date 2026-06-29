package com.taipei.iot.assettransfer.service;

import com.taipei.iot.assettransfer.dto.AssetTransferCreateRequest;
import com.taipei.iot.assettransfer.dto.AssetTransferResponse;
import com.taipei.iot.assettransfer.dto.RejectTargetDto;
import com.taipei.iot.assettransfer.dto.WorkflowStepLogDto;
import com.taipei.iot.assettransfer.entity.AssetTransferApplicationEntity;
import com.taipei.iot.assettransfer.enums.AssetTransferStatus;
import com.taipei.iot.assettransfer.repository.AssetTransferApplicationRepository;
import com.taipei.iot.auth.repository.UserRepository;
import com.taipei.iot.common.enums.ErrorCode;
import com.taipei.iot.common.exception.BusinessException;
import com.taipei.iot.dept.repository.DeptInfoRepository;
import com.taipei.iot.workflow.entity.WorkflowInstanceEntity;
import com.taipei.iot.workflow.entity.WorkflowStepLogEntity;
import com.taipei.iot.workflow.model.StepDefinition;
import com.taipei.iot.workflow.model.WorkflowContext;
import com.taipei.iot.workflow.model.WorkflowStatus;
import com.taipei.iot.workflow.repository.DelegateSettingRepository;
import com.taipei.iot.workflow.repository.WorkflowStepLogRepository;
import com.taipei.iot.workflow.service.WorkflowEngine;
import com.taipei.iot.workflow.service.WorkflowSlaService;
import com.taipei.iot.workflow.dto.WorkflowSlaDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.taipei.iot.tenant.TenantContext;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AssetTransferService {

	private static final String WORKFLOW_CODE = "asset_transfer";

	private static final String BUSINESS_TYPE = "ASSET_TRANSFER";

	private final AssetTransferApplicationRepository repository;

	private final WorkflowEngine workflowEngine;

	private final WorkflowSlaService workflowSlaService;

	private final WorkflowStepLogRepository stepLogRepository;

	private final DelegateSettingRepository delegateSettingRepository;

	private final UserRepository userRepository;

	private final DeptInfoRepository deptInfoRepository;

	// ── 建立申請（草稿）─────────────────────────────────────────────────────────

	@Transactional
	public AssetTransferResponse create(AssetTransferCreateRequest req, String applicantId) {
		// 先補齊顯示用資料，避免前端之後還要再查一次使用者與部門名稱。
		String applicantName = userRepository.findById(applicantId).map(u -> u.getDisplayName()).orElse(null);
		// findByDeptId 受 @Filter(tenantFilter) 保護：查無表示不存在或屬於他租戶，一律回 DEPT_NOT_FOUND。
		String departmentName = deptInfoRepository.findByDeptId(req.departmentId())
			.map(d -> d.getDeptName())
			.orElseThrow(() -> new BusinessException(ErrorCode.DEPT_NOT_FOUND));
		if (req.targetDepartmentId() != null) {
			// findByDeptId 受 @Filter(tenantFilter) 保護：查無表示不存在或屬於他租戶，一律回 DEPT_NOT_FOUND。
			deptInfoRepository.findByDeptId(req.targetDepartmentId())
				.orElseThrow(() -> new BusinessException(ErrorCode.DEPT_NOT_FOUND));
		}

		// 先建立草稿狀態的申請單，送出前只保存基本欄位與目前建立者資訊。
		AssetTransferApplicationEntity app = AssetTransferApplicationEntity.builder()
			.applicationNo(generateApplicationNo())
			.applicantId(applicantId)
			.applicantName(applicantName)
			.departmentId(req.departmentId())
			.departmentName(departmentName)
			.assetCode(req.assetCode())
			.assetName(req.assetName())
			.transferType(req.transferType())
			.targetDepartmentId(req.targetDepartmentId())
			.reason(req.reason())
			.assetValue(req.assetValue())
			.createdBy(applicantId)
			.build();
		return toResponse(repository.save(app));
	}

	// ── 建立並立即送出（原子操作）────────────────────────────────────────────────

	/**
	 * 建立草稿後立即啟動簽核流程，兩步在同一個 transaction 內完成。 解決前端 create → submit
	 * 兩步操作無原子性的問題：若流程啟動失敗，草稿也會隨 rollback 一併撤銷。
	 */
	@Transactional
	public AssetTransferResponse createAndSubmit(AssetTransferCreateRequest req, String applicantId) {
		AssetTransferResponse draft = create(req, applicantId);
		return submit(draft.id(), applicantId);
	}

	// ── 送出申請（啟動流程）──────────────────────────────────────────────────────

	@Transactional
	public AssetTransferResponse submit(Long applicationId, String applicantId) {
		AssetTransferApplicationEntity app = findById(applicationId);

		if (!app.getApplicantId().equals(applicantId)) {
			throw new BusinessException(ErrorCode.ASSET_TRANSFER_PERMISSION_DENIED);
		}
		if (app.getStatus() != AssetTransferStatus.DRAFT) {
			throw new BusinessException(ErrorCode.ASSET_TRANSFER_INVALID_STATUS, "目前狀態：" + app.getStatus());
		}

		WorkflowContext context = WorkflowContext.builder()
			.businessId(app.getApplicationNo())
			.businessType(BUSINESS_TYPE)
			.applicantId(applicantId)
			.departmentId(String.valueOf(app.getDepartmentId()))
			.build();

		WorkflowInstanceEntity instance = workflowEngine.start(WORKFLOW_CODE, app.getApplicationNo(), BUSINESS_TYPE,
				context);

		// 自動完成 ROLE_DEPT_USER 步驟（送出即代表申請人確認），推進到下一步驟（dept_admin）
		instance = workflowEngine.approve(instance.getId(), "申請人送出", applicantId);

		app.setWorkflowInstanceId(instance.getId());
		app.setStatus(AssetTransferStatus.PROCESSING);
		app.setCurrentAssignee(resolveCurrentAssignee(instance.getId()));
		return toResponse(repository.save(app));
	}

	// ── 審核通過 ───────────────────────────────────────────────────────────────

	@Transactional
	public AssetTransferResponse approve(Long applicationId, String userId, String comment) {
		AssetTransferApplicationEntity app = findByIdAndAssertProcessing(applicationId);

		assertCanAct(userId, app.getCurrentAssignee());

		WorkflowInstanceEntity instance = workflowEngine.approve(app.getWorkflowInstanceId(), comment,
				app.getCurrentAssignee());

		if (instance.getStatus() == WorkflowStatus.COMPLETED) {
			app.setStatus(AssetTransferStatus.COMPLETED);
			app.setApprovedAt(LocalDateTime.now());
			app.setApprovedBy(userId);
			app.setCurrentAssignee(null);
		}
		else {
			app.setCurrentAssignee(resolveCurrentAssignee(instance.getId()));
		}
		return toResponse(repository.save(app));
	}

	// ── 審核退回 ───────────────────────────────────────────────────────────────

	@Transactional
	public AssetTransferResponse reject(Long applicationId, String userId, String comment, String targetStepId) {
		AssetTransferApplicationEntity app = findByIdAndAssertProcessing(applicationId);

		assertCanAct(userId, app.getCurrentAssignee());

		WorkflowInstanceEntity instance = workflowEngine.reject(app.getWorkflowInstanceId(), targetStepId, comment,
				app.getCurrentAssignee());

		String newAssignee = resolveCurrentAssignee(instance.getId());
		// 僅當退回目標為申請人本身時，才將申請狀態改為 REJECTED；
		// 若退回到中間審核步驟（例如財產管理退回部門主管），申請仍在流程中，狀態維持 PROCESSING。
		if (app.getApplicantId().equals(newAssignee)) {
			app.setStatus(AssetTransferStatus.REJECTED);
		}
		app.setRejectReason(comment);
		app.setCurrentAssignee(newAssignee);
		return toResponse(repository.save(app));
	}

	// ── 補件重送 ───────────────────────────────────────────────────────────────

	@Transactional
	public AssetTransferResponse resubmit(Long applicationId, String userId, String comment) {
		AssetTransferApplicationEntity app = findByIdAndAssertHasWorkflow(applicationId);

		if (!app.getApplicantId().equals(userId)) {
			throw new BusinessException(ErrorCode.ASSET_TRANSFER_PERMISSION_DENIED);
		}

		WorkflowInstanceEntity instance = workflowEngine.resubmit(app.getWorkflowInstanceId(), comment, userId);

		app.setStatus(AssetTransferStatus.PROCESSING);
		app.setRejectReason(null);
		app.setCurrentAssignee(resolveCurrentAssignee(instance.getId()));
		return toResponse(repository.save(app));
	}

	// ── 取消申請 ───────────────────────────────────────────────────────────────

	@Transactional
	public AssetTransferResponse cancel(Long applicationId, String userId, String comment) {
		AssetTransferApplicationEntity app = findByIdAndAssertHasWorkflow(applicationId);

		if (!app.getApplicantId().equals(userId)) {
			throw new BusinessException(ErrorCode.ASSET_TRANSFER_PERMISSION_DENIED);
		}
		if (app.getStatus() != AssetTransferStatus.PROCESSING) {
			throw new BusinessException(ErrorCode.ASSET_TRANSFER_INVALID_STATUS, "目前狀態：" + app.getStatus());
		}

		workflowEngine.cancel(app.getWorkflowInstanceId(), comment, userId);

		app.setStatus(AssetTransferStatus.CANCELLED);
		app.setCurrentAssignee(null);
		return toResponse(repository.save(app));
	}

	// ── 查詢 ───────────────────────────────────────────────────────────────────

	@Transactional(readOnly = true)
	public AssetTransferResponse getById(Long id, String currentUserId) {
		return toResponse(findById(id), currentUserId);
	}

	@Transactional(readOnly = true)
	public List<AssetTransferResponse> getMyApplications(String applicantId) {
		return repository.findByApplicantIdOrderByCreatedAtDesc(applicantId).stream().map(this::toResponse).toList();
	}

	@Transactional(readOnly = true)
	public List<RejectTargetDto> getRejectTargets(Long applicationId) {
		AssetTransferApplicationEntity app = findById(applicationId);
		if (app.getWorkflowInstanceId() == null) {
			return List.of();
		}
		StepDefinition target = workflowEngine.getRejectTargetStep(app.getWorkflowInstanceId());
		if (target == null) {
			return List.of();
		}
		return List.of(new RejectTargetDto(target.getId(), target.getName()));
	}

	@Transactional(readOnly = true)
	public List<AssetTransferResponse> getPendingTasks(String userId) {
		// 1. 自己本人的待審
		List<String> assigneeIds = new ArrayList<>();
		assigneeIds.add(userId);

		// 2. 今日有效的代理：把被代理人的待審也一起撈入
		List<String> delegatedFrom = delegateSettingRepository.findDelegatedUserIds(TenantContext.getCurrentTenantId(),
				userId, BUSINESS_TYPE, LocalDate.now());
		assigneeIds.addAll(delegatedFrom);

		List<Long> instanceIds = stepLogRepository.findByAssigneeUserIdInAndCompletedAtIsNull(assigneeIds)
			.stream()
			.map(WorkflowStepLogEntity::getWorkflowInstanceId)
			.distinct()
			.collect(Collectors.toList());

		if (instanceIds.isEmpty()) {
			return List.of();
		}
		return repository.findByWorkflowInstanceIdIn(instanceIds).stream().map(this::toResponse).toList();
	}

	// ── 簽核軌跡 ────────────────────────────────────────────────────────────────

	/**
	 * 查詢指定資產異動申請的完整簽核軌跡。
	 * <p>
	 * 回傳該申請對應 workflow instance 的所有步驟記錄（依進入時間正序）， 並將 assigneeUserId 解析為使用者顯示名稱。
	 */
	@Transactional(readOnly = true)
	public List<WorkflowStepLogDto> getHistory(Long applicationId) {
		AssetTransferApplicationEntity app = findById(applicationId);
		if (app.getWorkflowInstanceId() == null) {
			return List.of();
		}
		return workflowEngine.getHistory(app.getWorkflowInstanceId()).stream().map(log -> {
			String assigneeName = null;
			if (log.getAssigneeUserId() != null) {
				assigneeName = userRepository.findById(log.getAssigneeUserId())
					.map(u -> u.getDisplayName())
					.orElse(log.getAssigneeUserId());
			}
			return WorkflowStepLogDto.from(log, assigneeName);
		}).toList();
	}

	/**
	 * 查詢指定資產異動申請的 SLA 時效 KPI。
	 * <p>
	 * 申請尚未送出（無 workflowInstanceId）時回傳 null。
	 */
	@Transactional(readOnly = true)
	public WorkflowSlaDto getApplicationSla(Long applicationId) {
		AssetTransferApplicationEntity app = findById(applicationId);
		if (app.getWorkflowInstanceId() == null) {
			return null;
		}
		return workflowSlaService.getSla(app.getWorkflowInstanceId());
	}

	// ── Private helpers ────────────────────────────────────────────────────────

	private AssetTransferApplicationEntity findById(Long id) {
		return repository.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.ASSET_TRANSFER_NOT_FOUND));
	}

	private AssetTransferApplicationEntity findByIdAndAssertProcessing(Long id) {
		AssetTransferApplicationEntity app = findById(id);
		if (app.getStatus() != AssetTransferStatus.PROCESSING) {
			throw new BusinessException(ErrorCode.ASSET_TRANSFER_INVALID_STATUS, "目前狀態：" + app.getStatus());
		}
		return app;
	}

	private AssetTransferApplicationEntity findByIdAndAssertHasWorkflow(Long id) {
		AssetTransferApplicationEntity app = findById(id);
		if (app.getWorkflowInstanceId() == null) {
			throw new BusinessException(ErrorCode.ASSET_TRANSFER_WORKFLOW_NOT_STARTED);
		}
		return app;
	}

	private String resolveCurrentAssignee(Long workflowInstanceId) {
		return stepLogRepository.findCurrentByInstanceId(workflowInstanceId)
			.map(WorkflowStepLogEntity::getAssigneeUserId)
			.orElse(null);
	}

	private AssetTransferResponse toResponse(AssetTransferApplicationEntity entity) {
		return toResponse(entity, null);
	}

	private AssetTransferResponse toResponse(AssetTransferApplicationEntity entity, String currentUserId) {
		String assigneeName = null;
		if (entity.getCurrentAssignee() != null) {
			assigneeName = userRepository.findById(entity.getCurrentAssignee())
				.map(u -> u.getDisplayName())
				.orElse(entity.getCurrentAssignee());
		}
		boolean canAct = false;
		if (currentUserId != null && entity.getStatus() == AssetTransferStatus.PROCESSING
				&& entity.getCurrentAssignee() != null) {
			// 本人就是 currentAssignee
			if (currentUserId.equals(entity.getCurrentAssignee())) {
				canAct = true;
			}
			else {
				// 或者今日有效代理：currentAssignee 的代理人是 currentUserId
				List<String> delegatedFrom = delegateSettingRepository.findDelegatedUserIds(
						TenantContext.getCurrentTenantId(), currentUserId, BUSINESS_TYPE, LocalDate.now());
				canAct = delegatedFrom.contains(entity.getCurrentAssignee());
			}
		}
		return AssetTransferResponse.from(entity, assigneeName, canAct);
	}

	private String generateApplicationNo() {
		return "AT-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
	}

	/**
	 * 驗證操作者是否為 currentAssignee 本人或今日有效的代理人。
	 */
	private void assertCanAct(String userId, String currentAssignee) {
		if (userId.equals(currentAssignee)) {
			return;
		}
		List<String> delegatedFrom = delegateSettingRepository.findDelegatedUserIds(TenantContext.getCurrentTenantId(),
				userId, BUSINESS_TYPE, LocalDate.now());
		if (!delegatedFrom.contains(currentAssignee)) {
			throw new BusinessException(ErrorCode.ASSET_TRANSFER_PERMISSION_DENIED);
		}
	}

}
