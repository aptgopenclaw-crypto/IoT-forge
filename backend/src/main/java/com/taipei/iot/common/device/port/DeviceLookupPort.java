package com.taipei.iot.common.device.port;

import java.util.Optional;

/**
 * 依設備代碼解析設備的 port。
 *
 * <p>
 * 由 {@code device} 模組實作，供 {@code ingest}（將外部上送的 deviceCode 解析為內部設備）與
 * {@code telemetry}（寫入前取得 deviceId / deviceType）單向取用，使其不需直接相依 {@code device} 內部。
 */
public interface DeviceLookupPort {

	/**
	 * 在指定租戶下，以設備代碼解析設備。
	 * @param deviceCode 設備代碼（{@code devices.device_code}）
	 * @param tenantId 租戶識別碼
	 * @return 對應的 {@link DeviceRef}；若不存在則為 {@link Optional#empty()}
	 */
	Optional<DeviceRef> resolve(String deviceCode, String tenantId);

}
