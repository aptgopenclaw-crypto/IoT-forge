package com.taipei.iot.eventrule.dto;

/**
 * 啟用 / 停用規則的請求 DTO。
 */
public record ToggleEnabledRequest(boolean enabled) {
}
