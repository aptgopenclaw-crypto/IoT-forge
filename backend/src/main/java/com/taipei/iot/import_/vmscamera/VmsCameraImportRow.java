package com.taipei.iot.import_.vmscamera;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * VMS 攝影機 CSV 匯入列 DTO。
 *
 * <p>
 * 對應 CSV 欄位：server_name, vms_camera_id, display_name, rtsp_url, device_code, dept_name。
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VmsCameraImportRow {

	private String serverName;

	private String vmsCameraId;

	private String displayName;

	private String rtspUrl;

	private String deviceCode;

	private String deptName;

}
