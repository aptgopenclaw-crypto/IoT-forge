package com.taipei.iot.common.event;

import java.time.Instant;
import java.util.Map;

/**
 * 遙測資料接收事件。
 *
 * <p>
 * 當 {@code telemetry} 核心管線（{@code TelemetryIngestionService}）完成驗證並寫入
 * {@code telemetry_data} 後發出，供 {@code event-rule} 模組以 {@code @Async @EventListener}
 * 訂閱進行規則比對。
 *
 * <p>
 * 採 Spring {@code ApplicationEventPublisher} 解耦：本事件定義於 {@code common}，{@code telemetry}
 * 發出、 {@code event-rule} 訂閱，雙方皆不直接相依，避免編譯期循環。
 *
 * <p>
 * 注意：訂閱端通常以 {@code @Async} 執行（ThreadLocal 不繼承），需以本事件攜帶的 {@link #tenantId()} 顯式還原
 * {@code TenantContext}。
 *
 * @param tenantId 租戶識別碼（訂閱端據此還原租戶情境）
 * @param deviceId 設備主鍵（{@code devices.id}）
 * @param deviceType 設備型別（對應 schema / device template）
 * @param ts 資料時間點（來自 payload 的 ts，或伺服器接收時間）
 * @param values 通過驗證後的遙測 key-value（唯讀；訂閱端不應修改）
 */
public record TelemetryReceivedEvent(String tenantId, Long deviceId, String deviceType, Instant ts,
		Map<String, Object> values) {
}
