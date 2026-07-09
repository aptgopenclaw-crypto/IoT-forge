package com.taipei.iot.vms.service;

import com.taipei.iot.common.enums.ErrorCode;
import com.taipei.iot.common.exception.BusinessException;
import com.taipei.iot.vms.VmsAdapter;
import com.taipei.iot.vms.VmsAdapterManager;
import com.taipei.iot.vms.config.VmsProperties;
import com.taipei.iot.vms.dto.CameraLiveResponse;
import com.taipei.iot.vms.dto.CameraPlaybackResponse;
import com.taipei.iot.vms.dto.CameraStreamInfo;
import com.taipei.iot.vms.dto.PtzCommand;
import com.taipei.iot.vms.entity.VmsCamera;
import com.taipei.iot.vms.entity.VmsServer;
import com.taipei.iot.vms.enums.CameraStatus;
import com.taipei.iot.vms.enums.VmsAuthType;
import com.taipei.iot.vms.enums.VmsType;
import com.taipei.iot.vms.repository.VmsCameraRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * {@link VmsStreamServiceImpl} 串流服務行為。
 */
@ExtendWith(MockitoExtension.class)
class VmsStreamServiceImplTest {

	@Mock
	private VmsCameraRepository vmsCameraRepository;

	@Mock
	private VmsAdapterManager vmsAdapterManager;

	@Mock
	private ZlMediaKitClient zlMediaKitClient;

	@Mock
	private StringRedisTemplate redisTemplate;

	@Mock
	private ValueOperations<String, String> valueOps;

	@Mock
	private StreamTokenService streamTokenService;

	private VmsStreamServiceImpl service;

	private final VmsProperties properties = new VmsProperties();

	private final VmsServer testServer = VmsServer.builder()
		.id(1L)
		.tenantId("tenant-1")
		.name("Test Server")
		.vmsType(VmsType.NX_WITNESS)
		.baseUrl("http://nx:7001")
		.authType(VmsAuthType.BASIC)
		.authUsername("admin")
		.authPassword("pass")
		.isActive(true)
		.build();

	private final VmsCamera testCamera = VmsCamera.builder()
		.id(1L)
		.tenantId("tenant-1")
		.server(testServer)
		.vmsCameraId("cam-001")
		.displayName("入口")
		.status(CameraStatus.ONLINE)
		.build();

	@BeforeEach
	void setUp() {
		com.taipei.iot.common.context.TenantContext.setCurrentTenantId("tenant-1");
		service = new VmsStreamServiceImpl(vmsCameraRepository, vmsAdapterManager, zlMediaKitClient, redisTemplate,
				properties, streamTokenService);
		lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
		lenient().when(streamTokenService.generateToken(anyLong(), any(), any())).thenReturn("test-token-123");
	}

	@AfterEach
	void tearDown() {
		com.taipei.iot.common.context.TenantContext.clear();
	}

	@Nested
	@DisplayName("getLiveStream")
	class GetLiveStream {

		@Test
		@DisplayName("cache miss → adapter + ZLMediaKit → cache → 回傳 playUrl")
		void cacheMiss_fullFlow() {
			when(vmsCameraRepository.findByIdAndTenantId(1L, "tenant-1")).thenReturn(Optional.of(testCamera));
			var streamInfo = new CameraStreamInfo("cam-001", "rtsp://nx/test", null, VmsType.NX_WITNESS, null);
			when(vmsAdapterManager.getAdapter(VmsType.NX_WITNESS)).thenReturn(adapter(streamInfo));
			when(valueOps.get("vms:stream:1")).thenReturn(null); // cache miss
			when(zlMediaKitClient.addStreamProxy("rtsp://nx/test", "vms_1"))
				.thenReturn("http://mediaserver:8080/webrtcplayer/?streamId=vms_1&app=vms&schema=http");

			CameraLiveResponse response = service.getLiveStream(1L);

			assertThat(response.playUrl()).contains("webrtcplayer/?streamId=vms_1").contains("?token=");
			assertThat(response.cameraId()).isEqualTo(1L);
			assertThat(response.status()).isEqualTo(CameraStatus.ONLINE);
			verify(valueOps).set("vms:stream:1", response.playUrl(), properties.getStreamCacheTtlSeconds(),
					TimeUnit.SECONDS);
		}

		@Test
		@DisplayName("cache hit → 直接回傳 cached URL（含 token）")
		void cacheHit_returnsCached() {
			when(vmsCameraRepository.findByIdAndTenantId(1L, "tenant-1")).thenReturn(Optional.of(testCamera));
			when(valueOps.get("vms:stream:1")).thenReturn("http://cached/webrtc?token=abc");

			CameraLiveResponse response = service.getLiveStream(1L);

			assertThat(response.playUrl()).isEqualTo("http://cached/webrtc?token=abc");
			verify(vmsAdapterManager, never()).getAdapter(any());
			verify(zlMediaKitClient, never()).addStreamProxy(any(), any());
		}

		@Test
		@DisplayName("camera 不存在 → 拋 VMS_CAMERA_NOT_FOUND")
		void cameraNotFound_throwsException() {
			when(vmsCameraRepository.findByIdAndTenantId(999L, "tenant-1")).thenReturn(Optional.empty());

			assertThatThrownBy(() -> service.getLiveStream(999L)).isInstanceOf(BusinessException.class)
				.hasFieldOrPropertyWithValue("errorCode", ErrorCode.VMS_CAMERA_NOT_FOUND);
		}

		@Test
		@DisplayName("ZLMediaKit 失敗 → 拋 VMS_STREAM_NOT_AVAILABLE")
		void zlMediaKitFails_throwsException() {
			when(vmsCameraRepository.findByIdAndTenantId(1L, "tenant-1")).thenReturn(Optional.of(testCamera));
			var streamInfo2 = new CameraStreamInfo("cam-001", "rtsp://nx/test", null, VmsType.NX_WITNESS, null);
			when(vmsAdapterManager.getAdapter(VmsType.NX_WITNESS)).thenReturn(adapter(streamInfo2));
			when(valueOps.get("vms:stream:1")).thenReturn(null);
			when(zlMediaKitClient.addStreamProxy("rtsp://nx/test", "vms_1"))
				.thenThrow(new BusinessException(ErrorCode.VMS_STREAM_NOT_AVAILABLE, "媒體伺服器錯誤"));

			assertThatThrownBy(() -> service.getLiveStream(1L)).isInstanceOf(BusinessException.class)
				.hasFieldOrPropertyWithValue("errorCode", ErrorCode.VMS_STREAM_NOT_AVAILABLE);
		}

	}

	@Nested
	@DisplayName("getPlayback")
	class GetPlayback {

		@Test
		@DisplayName("成功回放")
		void success() {
			when(vmsCameraRepository.findByIdAndTenantId(1L, "tenant-1")).thenReturn(Optional.of(testCamera));
			var streamInfo3 = new CameraStreamInfo("cam-001", "rtsp://nx/playback", null, VmsType.NX_WITNESS, null);
			when(vmsAdapterManager.getAdapter(VmsType.NX_WITNESS)).thenReturn(adapter(streamInfo3));
			when(zlMediaKitClient.addStreamProxy("rtsp://nx/playback", "vms_1_playback"))
				.thenReturn("http://mediaserver:8080/webrtcplayer/?streamId=vms_1_playback&app=vms&schema=http");

			var start = Instant.parse("2026-07-01T00:00:00Z");
			var end = Instant.parse("2026-07-01T01:00:00Z");
			CameraPlaybackResponse response = service.getPlayback(1L, start, end);

			assertThat(response.playUrl()).contains("vms_1_playback").contains("?token=");
			assertThat(response.startTime()).isEqualTo(start);
			assertThat(response.endTime()).isEqualTo(end);
		}

		@Test
		@DisplayName("時間範圍無效 → 拋 VMS_PLAYBACK_INVALID_RANGE")
		void invalidRange_throwsException() {
			var start = Instant.parse("2026-07-01T01:00:00Z");
			var end = Instant.parse("2026-07-01T00:00:00Z");

			assertThatThrownBy(() -> service.getPlayback(1L, start, end)).isInstanceOf(BusinessException.class)
				.hasFieldOrPropertyWithValue("errorCode", ErrorCode.VMS_PLAYBACK_INVALID_RANGE);
		}

	}

	@Nested
	@DisplayName("controlPtz")
	class ControlPtz {

		@Test
		@DisplayName("成功控制 PTZ")
		void success() {
			when(vmsCameraRepository.findByIdAndTenantId(1L, "tenant-1")).thenReturn(Optional.of(testCamera));
			var adapter = mock(VmsAdapter.class);
			when(vmsAdapterManager.getAdapter(VmsType.NX_WITNESS)).thenReturn(adapter);

			var command = new PtzCommand("LEFT", 30, null);
			service.controlPtz(1L, command);

			verify(adapter).controlPtz("cam-001", command);
		}

	}

	@Nested
	@DisplayName("releaseStream")
	class ReleaseStream {

		@Test
		@DisplayName("清除 cache 並關閉串流")
		void release() {
			service.releaseStream(1L);

			verify(redisTemplate).delete("vms:stream:1");
			verify(zlMediaKitClient).closeStream("vms_1");
		}

	}

	/**
	 * 輔助：產生 VmsAdapter lambda（僅用於 getLiveStreamUrl）。
	 */
	private VmsAdapter adapter(CameraStreamInfo info) {
		return new VmsAdapter() {
			@Override
			public VmsType getType() {
				return VmsType.NX_WITNESS;
			}

			@Override
			public CameraStreamInfo getLiveStreamUrl(String cameraId) {
				return info;
			}

			@Override
			public CameraStreamInfo getPlaybackUrl(String cameraId, Instant s, Instant e) {
				return info;
			}

			@Override
			public void controlPtz(String cameraId, PtzCommand cmd) {
			}

			@Override
			public com.taipei.iot.vms.entity.VmsCamera getCameraInfo(String cameraId) {
				return null;
			}

			@Override
			public java.util.List<com.taipei.iot.vms.entity.VmsCamera> listCameras(int page, int size) {
				return java.util.List.of();
			}

			@Override
			public boolean healthCheck() {
				return true;
			}
		};
	}

}
