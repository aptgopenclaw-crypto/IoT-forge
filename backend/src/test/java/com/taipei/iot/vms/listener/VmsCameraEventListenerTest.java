package com.taipei.iot.vms.listener;

import com.taipei.iot.notification.dto.NotificationPayload;
import com.taipei.iot.notification.enums.NotificationRefType;
import com.taipei.iot.notification.enums.NotificationType;
import com.taipei.iot.notification.service.NotificationService;
import com.taipei.iot.vms.enums.VmsEventType;
import com.taipei.iot.vms.event.VmsCameraEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

/**
 * {@link VmsCameraEventListener} 通知發送行為。
 */
@ExtendWith(MockitoExtension.class)
class VmsCameraEventListenerTest {

	@Mock
	private NotificationService notificationService;

	@Captor
	private ArgumentCaptor<NotificationPayload> payloadCaptor;

	private VmsCameraEventListener listener;

	@BeforeEach
	void setUp() {
		listener = new VmsCameraEventListener(notificationService);
	}

	@Test
	@DisplayName("MOTION_DETECT → INFO 通知 + VMS_EVENT refType")
	void motionDetect_sendsInfoNotification() {
		var event = new VmsCameraEvent("tenant-1", 1L, "cam-001", VmsEventType.MOTION_DETECT, "{\"zone\":\"A1\"}",
				Instant.now());

		listener.onVmsCameraEvent(event);

		verify(notificationService).send(payloadCaptor.capture());
		var payload = payloadCaptor.getValue();
		assertThat(payload.getRefType()).isEqualTo(NotificationRefType.VMS_EVENT);
		assertThat(payload.getRefId()).isEqualTo("1");
		assertThat(payload.getType()).isEqualTo(NotificationType.INFO);
		assertThat(payload.getTitle()).contains("移動偵測");
	}

	@Test
	@DisplayName("CAMERA_OFFLINE → ALERT 通知")
	void cameraOffline_sendsAlertNotification() {
		var event = new VmsCameraEvent("tenant-1", 1L, "cam-001", VmsEventType.CAMERA_OFFLINE, null, Instant.now());

		listener.onVmsCameraEvent(event);

		verify(notificationService).send(payloadCaptor.capture());
		assertThat(payloadCaptor.getValue().getType()).isEqualTo(NotificationType.ALERT);
		assertThat(payloadCaptor.getValue().getTitle()).contains("鏡頭離線");
	}

	@Test
	@DisplayName("CAMERA_ONLINE → INFO 通知")
	void cameraOnline_sendsInfoNotification() {
		var event = new VmsCameraEvent("tenant-1", 1L, "cam-001", VmsEventType.CAMERA_ONLINE, null, Instant.now());

		listener.onVmsCameraEvent(event);

		verify(notificationService).send(payloadCaptor.capture());
		assertThat(payloadCaptor.getValue().getType()).isEqualTo(NotificationType.INFO);
		assertThat(payloadCaptor.getValue().getTitle()).contains("鏡頭恢復");
	}

}
