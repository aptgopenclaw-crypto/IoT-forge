package com.taipei.iot.vms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 攝影機映射建立請求。
 *
 * @param serverId 所屬 VMS 伺服器 ID
 * @param vmsCameraId VMS 端的攝影機 ID
 * @param displayName 顯示名稱（選用）
 * @param deviceId 關聯 IoT 裝置 ID（選用）
 */
public record VmsCameraRequest(@NotNull Long serverId, @NotBlank String vmsCameraId, String displayName,
		Long deviceId) {
}
