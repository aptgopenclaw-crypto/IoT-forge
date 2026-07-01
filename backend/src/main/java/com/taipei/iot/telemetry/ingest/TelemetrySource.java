package com.taipei.iot.telemetry.ingest;

/**
 * 遙測來源通道。對應 {@code telemetry_data.source}，由 {@code ingest} 外圈 adapter 標記。
 */
public enum TelemetrySource {

	MQTT, HTTP_API, BATCH_IMPORT, KAFKA

}
