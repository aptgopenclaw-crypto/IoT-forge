package com.taipei.iot.common.dispatch.port;

/**
 * Port for counting open (unresolved) work orders. Implemented by the {@code dispatch}
 * module so that lower-layer consumers (e.g. {@code device} statistics) do not depend
 * upward on {@code dispatch}.
 */
public interface OpenWorkOrderCounter {

	/**
	 * Count work orders that are still open (not completed or closed). The definition of
	 * "open" is owned by the {@code dispatch} module.
	 * @return number of open work orders in the current tenant scope
	 */
	long countOpenWorkOrders();

}
