package com.taipei.iot.vms.dto;

import com.taipei.iot.vms.enums.CameraStatus;

import java.time.Instant;

/**
 * 即時影像播放回應。
 *
 * @param cameraId 本地攝影機 ID
 * @param displayName 顯示名稱
 * @param playUrl 前端播放 URL（經 ZLMediaKit 轉換後）
 * @param expiresAt 串流 URL 過期時間
 * @param status 攝影機狀態
 */
public record CameraLiveResponse(Long cameraId, String displayName, String playUrl, Instant expiresAt,
		CameraStatus status) {
}
