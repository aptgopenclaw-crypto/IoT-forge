package com.taipei.iot.telemetry.query.dto;

/**
 * 單一數值量測欄位在指定時間窗的統計摘要。
 *
 * <p>
 * 僅統計可轉為數值的樣本；當 {@code count} 為 0 時，{@code min}/{@code max}/{@code avg} 皆為 {@code null}。
 *
 * @param field 量測欄位名稱
 * @param count 納入統計的樣本數
 * @param min 最小值（無樣本時為 null）
 * @param max 最大值（無樣本時為 null）
 * @param avg 平均值（無樣本時為 null）
 */
public record TelemetryFieldStats(String field, long count, Double min, Double max, Double avg) {
}
