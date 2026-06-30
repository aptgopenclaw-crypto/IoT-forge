package com.taipei.iot.device.service;

import com.taipei.iot.common.device.port.DeviceTypeUsageGuard;
import com.taipei.iot.device.repository.DeviceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * {@code device}-module adapter for {@link DeviceTypeUsageGuard}. Lets the {@code schema}
 * module check device-type usage without depending on {@code device}.
 */
@Component
@RequiredArgsConstructor
public class DeviceTypeUsageAdapter implements DeviceTypeUsageGuard {

	private final DeviceRepository deviceRepository;

	@Override
	public long countDevicesOfType(String deviceType) {
		return deviceRepository.countByDeviceType(deviceType);
	}

}
