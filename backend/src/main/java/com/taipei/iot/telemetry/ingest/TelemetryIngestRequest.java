package com.taipei.iot.telemetry.ingest;

import java.time.Instant;
import java.util.Map;

/**
 * 進入 telemetry 核心的 canonical 接入請求。各來源（MQTT / HTTP / 批次匯入…）的 {@code ingest} adapter
 * 先收斂成本模型，再呼叫 {@link TelemetryIngestionService}，核心因此與協定無關。
 *
 * @param deviceCode 設備代碼（待解析為內部 deviceId）
 * @param tenantId 租戶識別碼
 * @param ts 資料時間點（null 時由核心以接收時間補上）
 * @param values 遙測 key-value（待 JSON Schema 驗證）
 * @param source 來源通道（null 時預設 {@link TelemetrySource#MQTT}）
 * @param sourceClientId 來源客戶端識別（可為 null）
 * @param rawPayload 原始 payload（偵錯用，可為 null）
 */
public record TelemetryIngestRequest(String deviceCode, String tenantId, Instant ts, Map<String, Object> values,
		TelemetrySource source, String sourceClientId, Map<String, Object> rawPayload) {
}
