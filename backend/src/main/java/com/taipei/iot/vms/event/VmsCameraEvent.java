package com.taipei.iot.vms.event;

import com.taipei.iot.vms.enums.VmsEventType;

import java.time.Instant;

/**
 * VMS 攝影機事件（Spring ApplicationEvent 模式）。
 *
 * <p>
 * 由 {@code VmsEventService} 在接收並儲存 VMS webhook 後發布， 供 {@code VmsCameraEventListener} 以
 * {@code @Async @EventListener} 訂閱推播通知。
 * </p>
 *
 * <p>
 * 採用與 {@code TelemetryReceivedEvent}、{@code RuleTriggeredEvent} 相同的 record 模式， 不繼承
 * {@code java.util.EventObject}，Spring 會自動包裝。
 * </p>
 *
 * @param tenantId 租戶識別碼
 * @param cameraId 本地攝影機 ID
 * @param vmsCameraId VMS 端的攝影機 ID
 * @param eventType 事件類型
 * @param payload 事件資料（JSON string）
 * @param occurredAt 事件發生時間
 */
public record VmsCameraEvent(String tenantId, Long cameraId, String vmsCameraId, VmsEventType eventType, String payload,
		Instant occurredAt) {
}
