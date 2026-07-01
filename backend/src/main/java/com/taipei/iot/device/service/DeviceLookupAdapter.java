package com.taipei.iot.device.service;

import com.taipei.iot.common.context.TenantContext;
import com.taipei.iot.common.device.port.DeviceLookupPort;
import com.taipei.iot.common.device.port.DeviceRef;
import com.taipei.iot.device.repository.DeviceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * {@code device}-module adapter for {@link DeviceLookupPort}. Lets {@code ingest} /
 * {@code telemetry} resolve a {@code deviceCode} to a {@link DeviceRef} without depending
 * on {@code device} internals.
 *
 * <p>
 * 以傳入的 {@code tenantId} 顯式限定查詢，並在 SYSTEM context 中執行（毋須仰賴呼叫端 ambient
 * {@code TenantContext}），租戶隔離由 JPQL 的 {@code tenant_id} 條件保證。
 */
@Component
@RequiredArgsConstructor
public class DeviceLookupAdapter implements DeviceLookupPort {

	private final DeviceRepository deviceRepository;

	@Override
	@Transactional(readOnly = true)
	public Optional<DeviceRef> resolve(String deviceCode, String tenantId) {
		if (deviceCode == null || tenantId == null) {
			return Optional.empty();
		}
		return TenantContext.runInSystemContext(() -> deviceRepository.findByTenantIdAndDeviceCode(tenantId, deviceCode)
			.map(d -> new DeviceRef(d.getId(), d.getDeviceCode(), d.getDeviceType(), d.getTenantId())));
	}

}
