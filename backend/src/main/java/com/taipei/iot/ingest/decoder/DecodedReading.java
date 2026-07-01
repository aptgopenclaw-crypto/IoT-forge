package com.taipei.iot.ingest.decoder;

import java.time.Instant;
import java.util.Map;

/**
 * 解碼後的單筆遙測讀數（尚未轉成 telemetry 核心的 canonical 請求）。
 *
 * @param deviceCode 設備代碼（payload 內含時填入，否則為 null，由來源 adapter 以其他方式補上，例如 MQTT topic）
 * @param ts 資料時間點（payload 未帶時為 null，由核心補接收時間）
 * @param values 遙測 key-value
 */
public record DecodedReading(String deviceCode, Instant ts, Map<String, Object> values) {
}
