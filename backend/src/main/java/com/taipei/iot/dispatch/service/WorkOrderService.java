package com.taipei.iot.dispatch.service;

import com.taipei.iot.common.enums.ErrorCode;
import com.taipei.iot.common.exception.BusinessException;
import com.taipei.iot.dispatch.dto.WorkOrderRequest;
import com.taipei.iot.dispatch.dto.WorkOrderResponse;
import com.taipei.iot.dispatch.dto.WorkOrderResponse.WorkOrderLogEntry;
import com.taipei.iot.user.entity.UserEntity;
import com.taipei.iot.user.repository.UserRepository;
import com.taipei.iot.device.entity.Device;
import com.taipei.iot.device.repository.DeviceRepository;
import com.taipei.iot.dispatch.entity.WorkOrder;
import com.taipei.iot.dispatch.entity.WorkOrderLog;
import com.taipei.iot.dispatch.enums.WorkOrderSourceType;
import com.taipei.iot.dispatch.enums.WorkOrderStatus;
import com.taipei.iot.dispatch.repository.WorkOrderLogRepository;
import com.taipei.iot.dispatch.repository.WorkOrderRepository;
import com.taipei.iot.common.context.TenantContext;
import com.taipei.iot.common.util.SecurityContextUtils;
import com.taipei.iot.workflow.model.WorkflowContext;
import com.taipei.iot.workflow.service.WorkflowEngine;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WorkOrderService {

	private final WorkOrderRepository workOrderRepository;

	private final WorkOrderLogRepository workOrderLogRepository;

	private final DeviceRepository deviceRepository;

	private final UserRepository userRepository;

	private final WorkflowEngine workflowEngine;

	private static final int EXPENSIVE_THRESHOLD = 100_000;

	// ── 查詢 ─────────────────────────────────────────────────────────

	public Page<WorkOrderResponse> list(Long deviceId, WorkOrderStatus status, WorkOrderSourceType sourceType,
			String keyword, Pageable pageable) {
		// Data scope：ADMIN / DEPT_ADMIN 可見全部；其餘角色只能看自己建立的工單
		String createdByFilter = null;
		if (!SecurityContextUtils.hasAnyAuthority("ROLE_SUPER_ADMIN", "ROLE_ADMIN", "ROLE_DEPT_ADMIN")) {
			createdByFilter = SecurityContextUtils.requireCurrentUserIdStrict();
		}
		return workOrderRepository.findByFilters(deviceId, status, sourceType, createdByFilter, keyword, pageable)
			.map(this::toResponse);
	}

	public Page<WorkOrderResponse> listMyTasks(String userId, Pageable pageable) {
		List<WorkOrderStatus> activeStatuses = List.of(WorkOrderStatus.ASSIGNED, WorkOrderStatus.IN_PROGRESS);
		return workOrderRepository.findByStatusIn(activeStatuses, pageable).map(this::toResponse);
	}

	public WorkOrderResponse getById(Long id) {
		WorkOrder wo = workOrderRepository.findById(id)
			.orElseThrow(() -> new BusinessException(ErrorCode.WORK_ORDER_NOT_FOUND));
		return toDetailResponse(wo);
	}

	// ── 建立工單 ─────────────────────────────────────────────────────

	@Transactional
	public WorkOrderResponse create(WorkOrderRequest request) {
		// 若提供 deviceCode 則解析為 deviceId
		Long resolvedDeviceId = request.getDeviceId();
		if (resolvedDeviceId == null && request.getDeviceCode() != null) {
			resolvedDeviceId = deviceRepository
				.findByTenantIdAndDeviceCode(TenantContext.getCurrentTenantId(), request.getDeviceCode())
				.map(Device::getId)
				.orElseThrow(
						() -> new BusinessException(ErrorCode.DEVICE_NOT_FOUND, "找不到設備代碼：" + request.getDeviceCode()));
		}

		WorkOrder wo = WorkOrder.builder()
			.tenantId(TenantContext.getCurrentTenantId())
			.deviceId(resolvedDeviceId)
			.circuitId(request.getCircuitId())
			.orderType(request.getOrderType())
			.sourceType(WorkOrderSourceType.valueOf(request.getSourceType()))
			.status(WorkOrderStatus.PENDING)
			.priority(request.getPriority())
			.reporterName(request.getReporterName())
			.reporterContact(request.getReporterContact())
			.reportedAt(LocalDateTime.now())
			.description(request.getDescription())
			.locationSnapshot(request.getLocationSnapshot())
			.createdBy(SecurityContextUtils.requireCurrentUserIdStrict())
			.build();

		wo = workOrderRepository.save(wo);

		// 取得設備所屬部門（提供給 AssigneeResolver 判斷主管）
		String deptId = null;
		if (resolvedDeviceId != null) {
			deptId = deviceRepository.findById(resolvedDeviceId)
				.map(d -> d.getDeptId() != null ? String.valueOf(d.getDeptId()) : null)
				.orElse(null);
		}

		// 啟動 workflow（設備障礙派工簽核）
		WorkflowContext context = WorkflowContext.builder()
			.businessId(wo.getId().toString())
			.businessType("WORK_ORDER")
			.applicantId(request.getReporterName())
			.departmentId(deptId)
			.build();
		var instance = workflowEngine.start("work_order_dispatch", wo.getId().toString(), "WORK_ORDER", context);

		// 自動通過通報步驟（step_report），進入派工審核（step_dispatch）
		workflowEngine.approve(instance.getId(), "通報送出", request.getReporterName());

		// 紀錄 workflow instance ID
		wo.setReviewWorkflowInstanceId(instance.getId());
		wo = workOrderRepository.save(wo);

		addLog(wo.getId(), "CREATED", null, WorkOrderStatus.PENDING, "工單建立");
		return toDetailResponse(wo);
	}

	// ── 指派 ─────────────────────────────────────────────────────────

	@Transactional
	public WorkOrderResponse assign(Long id, String assigneeUserId, String currentUserId) {
		WorkOrder wo = getWorkOrder(id);
		assertStatus(wo, WorkOrderStatus.PENDING);

		if (wo.getAssignedTo() != null) {
			throw new BusinessException(ErrorCode.WORK_ORDER_ALREADY_ASSIGNED);
		}

		wo.setAssignedTo(assigneeUserId);
		wo.setAssignedAt(LocalDateTime.now());
		wo.setStatus(WorkOrderStatus.ASSIGNED);
		wo = workOrderRepository.save(wo);

		// 推進 workflow：派工審核（step_dispatch）→ 施工執行（step_execution）
		// 由 DEPT_ADMIN（currentUserId）核准，自動指派給 OPERATOR（assigneeUserId）
		if (wo.getReviewWorkflowInstanceId() != null) {
			workflowEngine.approve(wo.getReviewWorkflowInstanceId(), "派工核准，指派給 " + assigneeUserId, currentUserId);
		}

		addLog(wo.getId(), "ASSIGNED", WorkOrderStatus.PENDING, WorkOrderStatus.ASSIGNED, "指派給 " + assigneeUserId);
		return toDetailResponse(wo);
	}

	// ── 到場打卡 ─────────────────────────────────────────────────────

	@Transactional
	public WorkOrderResponse startWork(Long id, BigDecimal latitude, BigDecimal longitude, String userId) {
		WorkOrder wo = getWorkOrder(id);
		assertStatus(wo, WorkOrderStatus.ASSIGNED);

		// 接手任務：若尚未指派或非當前使用者，更新為當前使用者
		if (userId != null && !userId.equals(wo.getAssignedTo())) {
			wo.setAssignedTo(userId);
			wo.setAssignedAt(LocalDateTime.now());
		}
		wo.setStatus(WorkOrderStatus.IN_PROGRESS);
		wo.setStartedAt(LocalDateTime.now());
		wo.setStartLat(latitude);
		wo.setStartLng(longitude);
		wo = workOrderRepository.save(wo);

		addLog(wo.getId(), "STARTED", WorkOrderStatus.ASSIGNED, WorkOrderStatus.IN_PROGRESS, "技師到場");
		return toDetailResponse(wo);
	}

	// ── 完成維修 ─────────────────────────────────────────────────────

	@Transactional
	public WorkOrderResponse complete(Long id, String remark, String faultCause, Integer repairCost) {
		WorkOrder wo = getWorkOrder(id);
		assertStatus(wo, WorkOrderStatus.IN_PROGRESS);

		wo.setStatus(WorkOrderStatus.REVIEWING);
		wo.setCompletedAt(LocalDateTime.now());
		wo.setCompletionRemark(remark);
		wo.setFaultCause(faultCause);
		wo.setRepairCost(repairCost);
		wo = workOrderRepository.save(wo);

		// 推進 workflow：施工執行（step_execution）→ 施工驗證（step_verify）
		if (wo.getReviewWorkflowInstanceId() != null) {
			workflowEngine.approve(wo.getReviewWorkflowInstanceId(),
					"維修完成" + (repairCost != null ? "，費用：" + repairCost : ""),
					wo.getAssignedTo() != null ? wo.getAssignedTo() : "system");
		}

		addLog(wo.getId(), "COMPLETED", WorkOrderStatus.IN_PROGRESS, WorkOrderStatus.REVIEWING,
				"維修完成" + (repairCost != null ? "，費用：" + repairCost : ""));
		return toDetailResponse(wo);
	}

	// ── 覆核 ─────────────────────────────────────────────────────────

	@Transactional
	public WorkOrderResponse approve(Long id, String reviewerId) {
		WorkOrder wo = getWorkOrder(id);
		assertStatus(wo, WorkOrderStatus.REVIEWING);

		wo.setStatus(WorkOrderStatus.COMPLETED);
		wo.setReviewerId(reviewerId);
		wo.setReviewedAt(LocalDateTime.now());
		wo = workOrderRepository.save(wo);

		addLog(wo.getId(), "APPROVED", WorkOrderStatus.REVIEWING, WorkOrderStatus.COMPLETED, "覆核通過");
		return toDetailResponse(wo);
	}

	@Transactional
	public WorkOrderResponse reject(Long id, String reviewerId, String reason) {
		WorkOrder wo = getWorkOrder(id);
		assertStatus(wo, WorkOrderStatus.REVIEWING);

		wo.setStatus(WorkOrderStatus.REJECTED);
		wo.setReviewerId(reviewerId);
		wo.setReviewedAt(LocalDateTime.now());
		wo.setRejectReason(reason);
		wo = workOrderRepository.save(wo);

		addLog(wo.getId(), "REJECTED", WorkOrderStatus.REVIEWING, WorkOrderStatus.REJECTED, "駁回原因：" + reason);
		return toDetailResponse(wo);
	}

	// ── 結案 ─────────────────────────────────────────────────────────

	@Transactional
	public WorkOrderResponse close(Long id, String closedBy) {
		WorkOrder wo = getWorkOrder(id);
		if (wo.getStatus() != WorkOrderStatus.COMPLETED && wo.getStatus() != WorkOrderStatus.REJECTED) {
			throw new BusinessException(ErrorCode.WORK_ORDER_INVALID_STATUS);
		}

		wo.setStatus(WorkOrderStatus.CLOSED);
		wo.setClosedAt(LocalDateTime.now());
		wo.setClosedBy(closedBy);
		wo = workOrderRepository.save(wo);

		addLog(wo.getId(), "CLOSED", wo.getStatus(), WorkOrderStatus.CLOSED, "結案");
		return toDetailResponse(wo);
	}

	// ── Timeline ─────────────────────────────────────────────────────

	public List<WorkOrderLogEntry> getTimeline(Long workOrderId) {
		return workOrderLogRepository.findByWorkOrderIdOrderByCreatedAtAsc(workOrderId)
			.stream()
			.map(log -> WorkOrderLogEntry.builder()
				.action(log.getAction())
				.operatorName(log.getOperatorName())
				.latitude(log.getLatitude())
				.longitude(log.getLongitude())
				.note(log.getNote())
				.createdAt(log.getCreatedAt())
				.build())
			.toList();
	}

	// ── 內部方法 ─────────────────────────────────────────────────────

	private WorkOrder getWorkOrder(Long id) {
		return workOrderRepository.findById(id)
			.orElseThrow(() -> new BusinessException(ErrorCode.WORK_ORDER_NOT_FOUND));
	}

	private void assertStatus(WorkOrder wo, WorkOrderStatus expected) {
		if (wo.getStatus() != expected) {
			throw new BusinessException(ErrorCode.WORK_ORDER_INVALID_STATUS);
		}
	}

	private void addLog(Long workOrderId, String action, WorkOrderStatus from, WorkOrderStatus to, String note) {
		WorkOrderLog log = WorkOrderLog.builder()
			.tenantId(TenantContext.getCurrentTenantId())
			.workOrderId(workOrderId)
			.action(action)
			.fromStatus(from != null ? from.name() : null)
			.toStatus(to != null ? to.name() : null)
			.note(note)
			.build();
		workOrderLogRepository.save(log);
	}

	private WorkOrderResponse toResponse(WorkOrder wo) {
		// 解析設備代碼與名稱
		String deviceCode = null;
		String deviceName = null;
		if (wo.getDeviceId() != null) {
			Device device = deviceRepository.findById(wo.getDeviceId()).orElse(null);
			if (device != null) {
				deviceCode = device.getDeviceCode();
				deviceName = device.getDeviceName();
			}
		}

		// 解析派工人員姓名
		String assignedToName = null;
		if (wo.getAssignedTo() != null) {
			assignedToName = userRepository.findById(wo.getAssignedTo()).map(UserEntity::getDisplayName).orElse(null);
		}

		return WorkOrderResponse.builder()
			.id(wo.getId())
			.deviceId(wo.getDeviceId())
			.deviceCode(deviceCode)
			.deviceName(deviceName)
			.orderType(wo.getOrderType())
			.sourceType(wo.getSourceType())
			.status(wo.getStatus())
			.priority(wo.getPriority())
			.reporterName(wo.getReporterName())
			.reportedAt(wo.getReportedAt())
			.description(wo.getDescription())
			.assignedTo(wo.getAssignedTo())
			.assignedToName(assignedToName)
			.assignedAt(wo.getAssignedAt())
			.startedAt(wo.getStartedAt())
			.completedAt(wo.getCompletedAt())
			.completionRemark(wo.getCompletionRemark())
			.faultCause(wo.getFaultCause())
			.repairCost(wo.getRepairCost())
			.reviewerId(wo.getReviewerId())
			.reviewedAt(wo.getReviewedAt())
			.rejectReason(wo.getRejectReason())
			.closedAt(wo.getClosedAt())
			.createdAt(wo.getCreatedAt())
			.updatedAt(wo.getUpdatedAt())
			.build();
	}

	private WorkOrderResponse toDetailResponse(WorkOrder wo) {
		WorkOrderResponse response = toResponse(wo);
		response.setTimeline(getTimeline(wo.getId()));
		response.setCreatedBy(wo.getCreatedBy());
		return response;
	}

}
