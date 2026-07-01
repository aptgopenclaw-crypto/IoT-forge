package com.taipei.iot.telemetry.ingest;

import java.util.List;

/**
 * 單筆遙測接入結果。批次接入逐筆回傳，故部分失敗不影響其他筆。
 *
 * @param success 是否成功寫入
 * @param deviceCode 來源設備代碼
 * @param deviceId 解析後的內部設備主鍵（失敗時可能為 null）
 * @param errorCode 失敗時的 {@code ErrorCode} 代碼（成功為 null）
 * @param message 結果訊息
 * @param validationErrors schema 驗證失敗時的結構化錯誤（其餘情況為空）
 */
public record TelemetryIngestResult(boolean success, String deviceCode, Long deviceId, String errorCode, String message,
		List<String> validationErrors) {

	public static TelemetryIngestResult success(String deviceCode, Long deviceId) {
		return new TelemetryIngestResult(true, deviceCode, deviceId, null, "OK", List.of());
	}

	public static TelemetryIngestResult failure(String deviceCode, String errorCode, String message) {
		return new TelemetryIngestResult(false, deviceCode, null, errorCode, message, List.of());
	}

	public static TelemetryIngestResult validationFailure(String deviceCode, String errorCode, String message,
			List<String> validationErrors) {
		return new TelemetryIngestResult(false, deviceCode, null, errorCode, message, List.copyOf(validationErrors));
	}

}
