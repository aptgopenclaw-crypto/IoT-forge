package com.taipei.iot.telemetry.storage;

import java.time.Instant;
import java.util.Map;

/**
 * 寫入儲存層的 canonical 遙測讀數（已通過驗證）。
 *
 * <p>
 * 作為 {@link TelemetryStore} 的輸入契約，刻意與 JPA entity {@link TelemetryData} 分離，使未來可平滑替換
 * 儲存後端（例如 TimescaleDB hypertable）而不影響上層。
 *
 * @param tenantId 租戶識別碼
 * @param deviceId 設備主鍵（{@code devices.id}）
 * @param deviceType 設備型別
 * @param ts 資料時間點
 * @param values 通過驗證後的遙測 key-value
 * @param source 來源通道（{@code TelemetrySource} 名稱）
 * @param sourceClientId 來源客戶端識別（可為 null）
 * @param rawPayload 原始 payload（偵錯用，可為 null）
 */
public record TelemetryReading(String tenantId, Long deviceId, String deviceType, Instant ts,
		Map<String, Object> values, String source, String sourceClientId, Map<String, Object> rawPayload) {
}
