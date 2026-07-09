package com.taipei.iot.vms.dto;

import java.time.Instant;
import java.util.Map;

/**
 * VMS Webhook 標準化事件格式。
 *
 * @param eventType 原始 VMS event type
 * @param cameraId VMS 端的攝影機 ID
 * @param occurredAt 事件發生時間
 * @param payload 原始事件資料
 */
public record VmsEvent(String eventType, String cameraId, Instant occurredAt, Map<String, Object> payload) {
}
