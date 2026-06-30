package com.taipei.iot.common.schema.port;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Optional;

/**
 * 取得設備型別 schema 的 port。
 *
 * <p>
 * 由 {@code schema}（定義層）模組實作，供上層消費者（{@code telemetry} 驗證遙測格式、{@code event-rule}
 * 對規則欄位做白名單驗證）單向取用，使消費者不需直接相依 {@code schema} 內部。
 *
 * <p>
 * {@code device_templates.schema} 拆分為 {@code attributes} 與 {@code telemetry} 兩段；本 port 提供
 * 其中的 {@code telemetry} 段作為 JSON Schema 驗證來源。
 */
public interface SchemaProviderPort {

	/**
	 * 取得指定設備型別的 telemetry JSON Schema（{@code device_templates.schema.telemetry}）。
	 * @param deviceType 設備型別（template）識別碼
	 * @return telemetry 段 JSON Schema；若該型別不存在或未定義 telemetry 段則為 {@link Optional#empty()}
	 */
	Optional<JsonNode> getTelemetrySchema(String deviceType);

}
