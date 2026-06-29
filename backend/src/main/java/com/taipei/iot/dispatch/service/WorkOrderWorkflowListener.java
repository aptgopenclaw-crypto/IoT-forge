package com.taipei.iot.dispatch.service;

import com.taipei.iot.dispatch.entity.WorkOrder;
import com.taipei.iot.dispatch.enums.WorkOrderStatus;
import com.taipei.iot.dispatch.repository.WorkOrderRepository;
import com.taipei.iot.workflow.event.WorkflowStepAssignedEvent;
import com.taipei.iot.workflow.event.WorkflowStepCompletedEvent;
import com.taipei.iot.workflow.model.WorkflowAction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDateTime;

/**
 * 監聽 workflow 事件，同步更新工單狀態。
 * <p>
 * 只在 businessType = "WORK_ORDER" 時處理。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkOrderWorkflowListener {

	private final WorkOrderRepository workOrderRepository;

	// ── 施工執行步驟被指派 → 更新工單指派資訊 ─────────────────────

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onStepAssigned(WorkflowStepAssignedEvent event) {
		if (!"WORK_ORDER".equals(event.getBusinessType())) {
			return;
		}
		if (!"step_execution".equals(event.getStepId())) {
			return;
		}

		Long workflowInstanceId = parseLong(event.getWorkflowInstanceId());
		Long workOrderId = parseWorkOrderId(workflowInstanceId);
		if (workOrderId == null) {
			return;
		}

		workOrderRepository.findById(workOrderId).ifPresent(wo -> {
			wo.setAssignedTo(event.getAssigneeUserId());
			wo.setAssignedAt(LocalDateTime.now());
			wo.setStatus(WorkOrderStatus.ASSIGNED);
			workOrderRepository.save(wo);
			log.info("[WorkOrder] assigned wo={} to operator={} via workflow", workOrderId, event.getAssigneeUserId());
		});
	}

	// ── 施工驗證步驟完成 → 更新工單覆核結果 ─────────────────────

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onStepCompleted(WorkflowStepCompletedEvent event) {
		if (!"WORK_ORDER".equals(event.getBusinessType())) {
			return;
		}
		if (!"step_verify".equals(event.getStepId())) {
			return;
		}

		Long workOrderId = parseWorkOrderId(event.getWorkflowInstanceId());
		if (workOrderId == null) {
			return;
		}

		workOrderRepository.findById(workOrderId).ifPresent(wo -> {
			if (event.getAction() == WorkflowAction.APPROVE) {
				wo.setStatus(WorkOrderStatus.COMPLETED);
				wo.setReviewedAt(LocalDateTime.now());
				wo.setReviewerId(event.getActorUserId());
				log.info("[WorkOrder] verified wo={} approved by {}", workOrderId, event.getActorUserId());
			}
			else if (event.getAction() == WorkflowAction.REJECT) {
				wo.setStatus(WorkOrderStatus.REJECTED);
				wo.setReviewedAt(LocalDateTime.now());
				wo.setReviewerId(event.getActorUserId());
				log.info("[WorkOrder] verified wo={} rejected by {}", workOrderId, event.getActorUserId());
			}
			workOrderRepository.save(wo);
		});
	}

	// ── 工具方法 ──────────────────────────────────────────────────

	/**
	 * 從 workflowInstanceId (Long) 反查工單 ID。 workflow_instances.businessId 儲存工單 ID 的字串形式。
	 * <p>
	 * 這裡的 event 只有 instanceId 沒有 businessId， 但我們已在 WorkOrder 中儲存了
	 * reviewWorkflowInstanceId， 所以透過 workOrderRepository 反查。
	 */
	private Long parseWorkOrderId(Long workflowInstanceId) {
		if (workflowInstanceId == null) {
			return null;
		}
		return workOrderRepository.findByReviewWorkflowInstanceId(workflowInstanceId)
			.map(WorkOrder::getId)
			.orElse(null);
	}

	private static Long parseLong(String s) {
		if (s == null) {
			return null;
		}
		try {
			return Long.parseLong(s);
		}
		catch (NumberFormatException e) {
			return null;
		}
	}

}
