package com.taipei.iot.vms.service;

import com.taipei.iot.vms.dto.CameraLiveResponse;
import com.taipei.iot.vms.dto.CameraPlaybackResponse;
import com.taipei.iot.vms.dto.PtzCommand;

import java.time.Instant;

/**
 * VMS 串流服務介面。
 *
 * <p>
 * 協調 VMS Adapter → ZLMediaKit → Redis cache 的核心流程。
 * </p>
 */
public interface VmsStreamService {

	/** 取得即時影像串流。 */
	CameraLiveResponse getLiveStream(Long cameraId);

	/** 取得歷史回放串流。 */
	CameraPlaybackResponse getPlayback(Long cameraId, Instant startTime, Instant endTime);

	/** PTZ 控制。 */
	void controlPtz(Long cameraId, PtzCommand command);

	/** 主動釋放串流。 */
	void releaseStream(Long cameraId);

}
