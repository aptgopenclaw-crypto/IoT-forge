package com.taipei.iot.vms.dto;

import com.taipei.iot.vms.enums.CameraStatus;

import java.time.Instant;

/**
 * 歷史回放播放回應。
 *
 * @param cameraId 本地攝影機 ID
 * @param displayName 顯示名稱
 * @param playUrl 前端播放 URL
 * @param startTime 回放起始時間
 * @param endTime 回放結束時間
 * @param status 攝影機狀態
 */
public record CameraPlaybackResponse(Long cameraId, String displayName, String playUrl, Instant startTime,
		Instant endTime, CameraStatus status) {
}
