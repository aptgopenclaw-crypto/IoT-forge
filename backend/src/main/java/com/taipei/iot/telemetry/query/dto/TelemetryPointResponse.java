package com.taipei.iot.telemetry.query.dto;

import java.time.Instant;
import java.util.Map;

/**
 * 單一遙測歷史資料點。
 *
 * @param ts 資料時間點（UTC）
 * @param values 該時間點通過驗證的遙測量測值
 */
public record TelemetryPointResponse(Instant ts, Map<String, Object> values) {
}
