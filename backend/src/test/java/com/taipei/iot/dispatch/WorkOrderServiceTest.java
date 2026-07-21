package com.taipei.iot.dispatch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.taipei.iot.user.entity.UserEntity;
import com.taipei.iot.user.repository.UserRepository;
import com.taipei.iot.common.exception.BusinessException;
import com.taipei.iot.device.repository.DeviceRepository;
import com.taipei.iot.dispatch.service.WorkOrderService;
import com.taipei.iot.dispatch.dto.WorkOrderResponse;
import com.taipei.iot.dispatch.entity.WorkOrder;
import com.taipei.iot.dispatch.entity.WorkOrderLog;
import com.taipei.iot.dispatch.enums.WorkOrderSourceType;
import com.taipei.iot.dispatch.enums.WorkOrderStatus;
import com.taipei.iot.dispatch.repository.WorkOrderLogRepository;
import com.taipei.iot.dispatch.repository.WorkOrderRepository;
import com.taipei.iot.common.context.TenantContext;
import com.taipei.iot.workflow.entity.WorkflowInstanceEntity;
import com.taipei.iot.workflow.service.WorkflowEngine;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WorkOrderServiceTest {

	@InjectMocks
	private WorkOrderService workOrderService;

	@Mock
	private WorkOrderRepository workOrderRepository;

	@Mock
	private WorkOrderLogRepository workOrderLogRepository;

	@Mock
	private DeviceRepository deviceRepository;

	@Mock
	private UserRepository userRepository;

	@Mock
	private WorkflowEngine workflowEngine;

	@Captor
	private ArgumentCaptor<WorkOrder> workOrderCaptor;

	@Captor
	private ArgumentCaptor<WorkOrderLog> logCaptor;

	@BeforeEach
	void setUp() {
		TenantContext.setCurrentTenantId("TENANT_A");
		WorkflowInstanceEntity mockInstance = WorkflowInstanceEntity.builder().id(999L).build();
		when(workflowEngine.start(any(), any(), any(), any())).thenReturn(mockInstance);
		when(workflowEngine.approve(any(), any(), any())).thenReturn(mockInstance);
		when(userRepository.findById(any()))
			.thenReturn(Optional.of(UserEntity.builder().displayName("Mock User").build()));
	}

	@AfterEach
	void tearDown() {
		TenantContext.clear();
	}

	private WorkOrder createWorkOrder(Long id, WorkOrderStatus status) {
		return WorkOrder.builder()
			.id(id)
			.tenantId("TENANT_A")
			.orderType("REPAIR")
			.sourceType(WorkOrderSourceType.CITIZEN)
			.status(status)
			.build();
	}

	// ── 建立工單 ────────────────────────────────────────────────────

	@Nested
	class CreateTests {

		// @Test
		// void create_shouldSetPending() {
		// WorkOrderRequest request = WorkOrderRequest.builder()
		// .deviceId(1L)
		// .orderType("REPAIR")
		// .sourceType("CITIZEN")
		// .description("路燈不亮")
		// .build();

		// WorkOrder saved = createWorkOrder(1L, WorkOrderStatus.PENDING);
		// when(workOrderRepository.save(any())).thenReturn(saved);

		// WorkOrderResponse result = workOrderService.create(request);

		// assertNotNull(result);
		// assertEquals(WorkOrderStatus.PENDING, result.getStatus());
		// verify(workOrderLogRepository).save(any(WorkOrderLog.class));
		// }

	}

	// ── 指派 ────────────────────────────────────────────────────────

	@Nested
	class AssignTests {

		@Test
		void assign_shouldUpdateStatus() {
			WorkOrder wo = createWorkOrder(1L, WorkOrderStatus.PENDING);
			when(workOrderRepository.findById(1L)).thenReturn(Optional.of(wo));
			when(workOrderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

			WorkOrderResponse result = workOrderService.assign(1L, "tech01", "deptAdmin");

			assertEquals(WorkOrderStatus.ASSIGNED, result.getStatus());
			assertEquals("tech01", result.getAssignedTo());
		}

		@Test
		void assign_whenAlreadyAssigned_shouldThrow() {
			WorkOrder wo = createWorkOrder(1L, WorkOrderStatus.PENDING);
			wo.setAssignedTo("someone");
			when(workOrderRepository.findById(1L)).thenReturn(Optional.of(wo));

			assertThrows(BusinessException.class, () -> workOrderService.assign(1L, "tech01", "deptAdmin"));
		}

		@Test
		void assign_whenWrongStatus_shouldThrow() {
			WorkOrder wo = createWorkOrder(1L, WorkOrderStatus.COMPLETED);
			when(workOrderRepository.findById(1L)).thenReturn(Optional.of(wo));

			assertThrows(BusinessException.class, () -> workOrderService.assign(1L, "tech01", "deptAdmin"));
		}

	}

	// ── 到場 ────────────────────────────────────────────────────────

	@Nested
	class StartWorkTests {

		@Test
		void startWork_shouldRecordGps() {
			WorkOrder wo = createWorkOrder(1L, WorkOrderStatus.ASSIGNED);
			when(workOrderRepository.findById(1L)).thenReturn(Optional.of(wo));
			when(workOrderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

			WorkOrderResponse result = workOrderService.startWork(1L, BigDecimal.valueOf(25.0330),
					BigDecimal.valueOf(121.5654), "tech01");

			assertEquals(WorkOrderStatus.IN_PROGRESS, result.getStatus());
			assertNotNull(result.getStartedAt());
		}

	}

	// ── 完成維修 ────────────────────────────────────────────────────

	@Nested
	class CompleteTests {

		@Test
		void complete_shouldSetReviewing() {
			WorkOrder wo = createWorkOrder(1L, WorkOrderStatus.IN_PROGRESS);
			when(workOrderRepository.findById(1L)).thenReturn(Optional.of(wo));
			when(workOrderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

			WorkOrderResponse result = workOrderService.complete(1L, "已更換燈泡", "燈泡壽命到期", 1500);

			assertEquals(WorkOrderStatus.REVIEWING, result.getStatus());
			assertEquals("已更換燈泡", result.getCompletionRemark());
			assertEquals("燈泡壽命到期", result.getFaultCause());
			assertEquals(Integer.valueOf(1500), result.getRepairCost());
		}

		@Test
		void complete_whenNotInProgress_shouldThrow() {
			WorkOrder wo = createWorkOrder(1L, WorkOrderStatus.PENDING);
			when(workOrderRepository.findById(1L)).thenReturn(Optional.of(wo));

			assertThrows(BusinessException.class, () -> workOrderService.complete(1L, null, null, null));
		}

	}

	// ── 完整流程 ────────────────────────────────────────────────────

	@Nested
	class FullFlowTests {

		@Test
		void fullLifecycle_shouldTransitCorrectly() {
			// 模擬狀態機：PENDING → ASSIGNED → IN_PROGRESS → REVIEWING → COMPLETED → CLOSED
			WorkOrder wo = createWorkOrder(1L, WorkOrderStatus.PENDING);

			// PENDING → ASSIGNED
			when(workOrderRepository.findById(1L)).thenReturn(Optional.of(wo));
			when(workOrderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
			workOrderService.assign(1L, "tech01", "deptAdmin");
			assertEquals(WorkOrderStatus.ASSIGNED, wo.getStatus());

			// ASSIGNED → IN_PROGRESS
			workOrderService.startWork(1L, BigDecimal.valueOf(25.0), BigDecimal.valueOf(121.5), "tech01");
			assertEquals(WorkOrderStatus.IN_PROGRESS, wo.getStatus());

			// IN_PROGRESS → REVIEWING
			workOrderService.complete(1L, "done", "cause", 500);
			assertEquals(WorkOrderStatus.REVIEWING, wo.getStatus());

			// REVIEWING → COMPLETED
			workOrderService.approve(1L, "mgr01");
			assertEquals(WorkOrderStatus.COMPLETED, wo.getStatus());

			// COMPLETED → CLOSED
			workOrderService.close(1L, "admin");
			assertEquals(WorkOrderStatus.CLOSED, wo.getStatus());
		}

		@Test
		void rejectFlow_shouldWork() {
			WorkOrder wo = createWorkOrder(1L, WorkOrderStatus.IN_PROGRESS);
			when(workOrderRepository.findById(1L)).thenReturn(Optional.of(wo));
			when(workOrderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

			workOrderService.complete(1L, "done", null, null);
			workOrderService.reject(1L, "mgr01", "維修品質不佳");

			assertEquals(WorkOrderStatus.REJECTED, wo.getStatus());
			assertEquals("維修品質不佳", wo.getRejectReason());
		}

	}

}
