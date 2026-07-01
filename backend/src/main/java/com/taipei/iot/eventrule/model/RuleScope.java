package com.taipei.iot.eventrule.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 規則套用範圍（對應 {@code event_rule.scope} JSONB）。
 *
 * @param deviceType 必填：規則綁定的設備型別（同一個型別的所有設備）
 * @param deviceIds 選填：null 代表該型別所有設備；非 null 代表只套用指定設備
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RuleScope(String deviceType, List<Long> deviceIds) {
}
