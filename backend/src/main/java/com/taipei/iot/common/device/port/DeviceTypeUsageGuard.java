package com.taipei.iot.common.device.port;

/**
 * Port for querying how many devices use a given device type. Implemented by the
 * {@code device} module so that the lower definition-layer {@code schema} module does not
 * depend upward on {@code device}.
 */
public interface DeviceTypeUsageGuard {

	/**
	 * Count the devices currently using the given device type.
	 * @param deviceType the device type (template) identifier
	 * @return number of devices of that type
	 */
	long countDevicesOfType(String deviceType);

}
