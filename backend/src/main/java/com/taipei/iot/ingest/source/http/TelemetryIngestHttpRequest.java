package com.taipei.iot.ingest.source.http;

import java.time.Instant;
import java.util.Map;

/**
 * HTTP ingest 單筆請求。{@code deviceCode} 與 {@code externalCode} 二擇一：優先用 {@code deviceCode}，
 * 否則以 {@code externalCode} 經 {@code device_external_ref} 解析。
 *
 * @param deviceCode 內部設備代碼（選填）
 * @param externalCode 外部設備代碼（選填，需先建立映射）
 * @param ts 資料時間點（選填，null 由核心補接收時間）
 * @param values 遙測 key-value
 */
public record TelemetryIngestHttpRequest(String deviceCode, String externalCode, Instant ts,
		Map<String, Object> values) {
}
