package com.taipei.iot.telemetry.query.dto;

import java.time.Instant;
import java.util.Map;

/**
 * 設備最新一筆遙測讀數。
 *
 * @param deviceId 設備主鍵
 * @param ts 最新資料時間點（UTC）
 * @param values 最新遙測量測值
 */
public record TelemetryLatestResponse(Long deviceId, Instant ts, Map<String, Object> values) {
}
