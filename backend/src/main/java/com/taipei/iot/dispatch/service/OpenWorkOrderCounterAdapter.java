package com.taipei.iot.dispatch.service;

import com.taipei.iot.common.dispatch.port.OpenWorkOrderCounter;
import com.taipei.iot.dispatch.enums.WorkOrderStatus;
import com.taipei.iot.dispatch.repository.WorkOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * {@code dispatch}-module adapter for {@link OpenWorkOrderCounter}. Owns the definition
 * of which work-order statuses count as "closed".
 */
@Component
@RequiredArgsConstructor
public class OpenWorkOrderCounterAdapter implements OpenWorkOrderCounter {

	private final WorkOrderRepository workOrderRepository;

	@Override
	public long countOpenWorkOrders() {
		return workOrderRepository.countOpenWorkOrders(List.of(WorkOrderStatus.COMPLETED, WorkOrderStatus.CLOSED));
	}

}
