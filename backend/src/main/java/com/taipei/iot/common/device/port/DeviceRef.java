package com.taipei.iot.common.device.port;

/**
 * 設備解析結果的輕量參考。
 *
 * <p>
 * 由 {@link DeviceLookupPort} 回傳，僅攜帶上層（{@code ingest} / {@code telemetry}）所需的最小設備識別資訊， 不外洩
 * {@code device} 模組的完整 entity。
 *
 * @param deviceId 設備主鍵（{@code devices.id}）
 * @param deviceCode 設備代碼（{@code devices.device_code}）
 * @param deviceType 設備型別（對應 schema / device template）
 * @param tenantId 租戶識別碼
 */
public record DeviceRef(Long deviceId, String deviceCode, String deviceType, String tenantId) {
}
