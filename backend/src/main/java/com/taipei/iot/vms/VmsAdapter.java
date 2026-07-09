package com.taipei.iot.vms;

import com.taipei.iot.vms.dto.CameraStreamInfo;
import com.taipei.iot.vms.dto.PtzCommand;
import com.taipei.iot.vms.entity.VmsCamera;
import com.taipei.iot.vms.enums.VmsType;

import java.time.Instant;
import java.util.List;

/**
 * VMS Adapter 埠（Port）介面。
 *
 * <p>
 * 每種 VMS 實作一個 {@code @Service}，由 {@link VmsAdapterManager} 統一管理。 遵循 Strategy Pattern，與專案中
 * {@code ImportStrategy}、{@code TelemetryPayloadDecoder} 相同模式。
 * </p>
 */
public interface VmsAdapter {

	/** 辨識此實作對應的 VMS 類型。 */
	VmsType getType();

	/** 取得即時影像串流 URL（原始 RTSP）。 */
	CameraStreamInfo getLiveStreamUrl(String cameraId);

	/** 取得歷史回放串流 URL。 */
	CameraStreamInfo getPlaybackUrl(String cameraId, Instant startTime, Instant endTime);

	/** PTZ 控制。 */
	void controlPtz(String cameraId, PtzCommand command);

	/** 取得攝影機資訊。 */
	VmsCamera getCameraInfo(String cameraId);

	/** 列出所有攝影機（分頁）。 */
	List<VmsCamera> listCameras(int page, int size);

	/** 驗證 VMS 連線是否正常。 */
	boolean healthCheck();

}
