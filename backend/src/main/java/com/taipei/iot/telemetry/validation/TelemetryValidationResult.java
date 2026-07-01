package com.taipei.iot.telemetry.validation;

import java.util.List;

/**
 * 遙測 JSON Schema 驗證結果。
 *
 * @param valid 是否通過驗證
 * @param errors 結構化錯誤訊息（通過時為空）
 */
public record TelemetryValidationResult(boolean valid, List<String> errors) {

	public static TelemetryValidationResult passed() {
		return new TelemetryValidationResult(true, List.of());
	}

	public static TelemetryValidationResult failed(List<String> errors) {
		return new TelemetryValidationResult(false, List.copyOf(errors));
	}

}
