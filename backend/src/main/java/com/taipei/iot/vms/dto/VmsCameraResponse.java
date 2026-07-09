package com.taipei.iot.vms.dto;

import com.taipei.iot.vms.enums.CameraStatus;

/**
 * 攝影機回應 DTO（不含 server 關聯，避免 LazyInitializationException）。
 *
 * @param id 本地攝影機 ID
 * @param serverId 所屬 VMS 伺服器 ID
 * @param vmsCameraId VMS 端的攝影機 ID
 * @param displayName 顯示名稱
 * @param deviceId 關聯 IoT 裝置 ID（選用）
 * @param status 攝影機狀態
 * @param rtspUrl 快取的 RTSP URL
 */
public record VmsCameraResponse(Long id, Long serverId, String vmsCameraId, String displayName, Long deviceId,
		CameraStatus status, String rtspUrl) {

	public static VmsCameraResponse from(com.taipei.iot.vms.entity.VmsCamera entity) {
		return new VmsCameraResponse(entity.getId(), entity.getServer().getId(), entity.getVmsCameraId(),
				entity.getDisplayName(), entity.getDeviceId(), entity.getStatus(), entity.getRtspUrl());
	}
}
