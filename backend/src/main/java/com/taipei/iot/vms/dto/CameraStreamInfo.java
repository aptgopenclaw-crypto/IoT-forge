package com.taipei.iot.vms.dto;

import com.taipei.iot.vms.enums.VmsType;

import java.util.Map;

/**
 * VMS 回傳的串流資訊。
 *
 * @param cameraId VMS 端的攝影機 ID
 * @param rtspUrl 原始 RTSP 串流網址
 * @param displayName 攝影機顯示名稱
 * @param vmsType 來源 VMS 類型
 * @param metadata VMS 特定的額外資訊
 */
public record CameraStreamInfo(String cameraId, String rtspUrl, String displayName, VmsType vmsType,
		Map<String, Object> metadata) {
}
