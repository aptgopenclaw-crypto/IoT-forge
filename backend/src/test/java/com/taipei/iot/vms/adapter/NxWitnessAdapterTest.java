package com.taipei.iot.vms.adapter;

import com.taipei.iot.common.enums.ErrorCode;
import com.taipei.iot.common.exception.BusinessException;
import com.taipei.iot.vms.dto.CameraStreamInfo;
import com.taipei.iot.vms.dto.PtzCommand;
import com.taipei.iot.vms.entity.VmsCamera;
import com.taipei.iot.vms.entity.VmsServer;
import com.taipei.iot.vms.enums.CameraStatus;
import com.taipei.iot.vms.enums.VmsAuthType;
import com.taipei.iot.vms.enums.VmsType;
import com.taipei.iot.vms.repository.VmsServerRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

/**
 * {@link NxWitnessAdapter} 與 Nx Witness REST API 的整合行為。
 */
@ExtendWith(MockitoExtension.class)
class NxWitnessAdapterTest {

	@Mock
	private VmsServerRepository vmsServerRepository;

	@Mock
	private NxSessionManager nxSessionManager;

	private NxWitnessAdapter adapter;

	private MockRestServiceServer mockServer;

	private final VmsServer testServer = VmsServer.builder()
		.id(1L)
		.tenantId("tenant-1")
		.name("Nx Test Server")
		.vmsType(VmsType.NX_WITNESS)
		.baseUrl("http://nx-test:7001")
		.authType(VmsAuthType.BASIC)
		.authUsername("admin")
		.authPassword("pass")
		.isActive(true)
		.build();

	@BeforeEach
	void setUp() {
		com.taipei.iot.common.context.TenantContext.setCurrentTenantId("tenant-1");
		RestClient.Builder builder = RestClient.builder().baseUrl(testServer.getBaseUrl());
		mockServer = MockRestServiceServer.bindTo(builder).build();
		// 注入 mock RestClient 取代內部建立的 client（透過覆寫 buildRestClient）
		RestClient restClient = builder.build();
		adapter = new NxWitnessAdapter(vmsServerRepository, nxSessionManager) {
			@Override
			RestClient buildRestClient(VmsServer server) {
				return restClient;
			}
		};
		// NxSessionManager mock 回傳固定的 session token；部分測試（如 resolveServer 失敗）不會走到，
		// 因此使用 lenient 避免 UnnecessaryStubbingException
		lenient().when(nxSessionManager.getToken(testServer)).thenReturn("mock-session-token");
	}

	@AfterEach
	void tearDown() {
		com.taipei.iot.common.context.TenantContext.clear();
	}

	@Nested
	@DisplayName("getLiveStreamUrl")
	class GetLiveStreamUrl {

		@Test
		@DisplayName("成功取得 RTSP URL")
		void success() {
			when(vmsServerRepository.findByTenantIdAndIsActiveTrue("tenant-1")).thenReturn(List.of(testServer));

			mockServer.expect(requestTo("http://nx-test:7001/ec2/cameras/cam-001/streams"))
				.andExpect(method(HttpMethod.POST))
				.andExpect(content().json("""
						{"streamType":"rtsp"}
						"""))
				.andRespond(withSuccess("""
						{"streamUrl":"rtsp://nx-test:554/cam-001"}
						""", MediaType.APPLICATION_JSON));

			CameraStreamInfo result = adapter.getLiveStreamUrl("cam-001");

			assertThat(result.cameraId()).isEqualTo("cam-001");
			assertThat(result.rtspUrl()).isEqualTo("rtsp://nx-test:554/cam-001");
			assertThat(result.vmsType()).isEqualTo(VmsType.NX_WITNESS);
			mockServer.verify();
		}

		@Test
		@DisplayName("串流 URL 為空時拋 VMS_STREAM_NOT_AVAILABLE")
		void emptyResponse_throwsException() {
			when(vmsServerRepository.findByTenantIdAndIsActiveTrue("tenant-1")).thenReturn(List.of(testServer));

			mockServer.expect(requestTo("http://nx-test:7001/ec2/cameras/cam-001/streams"))
				.andExpect(method(HttpMethod.POST))
				.andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

			assertThatThrownBy(() -> adapter.getLiveStreamUrl("cam-001")).isInstanceOf(BusinessException.class)
				.hasFieldOrPropertyWithValue("errorCode", ErrorCode.VMS_STREAM_NOT_AVAILABLE);
		}

	}

	@Nested
	@DisplayName("controlPtz")
	class ControlPtz {

		@Test
		@DisplayName("PTZ 控制成功")
		void success() {
			when(vmsServerRepository.findByTenantIdAndIsActiveTrue("tenant-1")).thenReturn(List.of(testServer));

			mockServer.expect(requestTo("http://nx-test:7001/ec2/cameras/cam-001/ptz"))
				.andExpect(method(HttpMethod.PUT))
				.andExpect(content().json("""
						{"command":"RIGHT","speed":50}
						"""))
				.andRespond(withSuccess());

			var command = new PtzCommand("RIGHT", 50, null);
			adapter.controlPtz("cam-001", command);

			mockServer.verify();
		}

	}

	@Nested
	@DisplayName("listCameras")
	class ListCameras {

		@Test
		@DisplayName("成功列出 Camera 類型裝置")
		void success() {
			when(vmsServerRepository.findByTenantIdAndIsActiveTrue("tenant-1")).thenReturn(List.of(testServer));

			mockServer.expect(requestTo("http://nx-test:7001/rest/v1/devices?deviceType=Camera&_orderBy=name"))
				.andExpect(method(HttpMethod.GET))
				.andRespond(withSuccess(
						"""
								[
									{"id":"cam-001","name":"Camera 1","status":"Online","deviceType":"Camera","vendor":"ACTi","model":"ACM-1231","isLicenseUsed":false},
									{"id":"cam-002","name":"Camera 2","status":"Offline","deviceType":"Camera","vendor":"Hikvision","model":"DS-2CD","group":{"id":"g1","name":"Floor 1"},"isLicenseUsed":true}
								]
								""",
						MediaType.APPLICATION_JSON));

			List<VmsCamera> result = adapter.listCameras(1, 100);

			assertThat(result).hasSize(2);
			assertThat(result.get(0).getVmsCameraId()).isEqualTo("cam-001");
			assertThat(result.get(0).getDisplayName()).isEqualTo("Camera 1");
			assertThat(result.get(0).getStatus()).isEqualTo(CameraStatus.ONLINE);
			assertThat(result.get(0).getMetadata()).containsEntry("vendor", "ACTi");
			assertThat(result.get(1).getStatus()).isEqualTo(CameraStatus.OFFLINE);
			assertThat(result.get(1).getMetadata()).containsEntry("groupName", "Floor 1");
			mockServer.verify();
		}

		@Test
		@DisplayName("空陣列回傳 empty list")
		void emptyArray_returnsEmptyList() {
			when(vmsServerRepository.findByTenantIdAndIsActiveTrue("tenant-1")).thenReturn(List.of(testServer));

			mockServer.expect(requestTo("http://nx-test:7001/rest/v1/devices?deviceType=Camera&_orderBy=name"))
				.andExpect(method(HttpMethod.GET))
				.andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

			List<VmsCamera> result = adapter.listCameras(1, 100);

			assertThat(result).isEmpty();
			mockServer.verify();
		}

	}

	@Nested
	@DisplayName("getCameraInfo")
	class GetCameraInfo {

		@Test
		@DisplayName("成功取得裝置資訊")
		void success() {
			when(vmsServerRepository.findByTenantIdAndIsActiveTrue("tenant-1")).thenReturn(List.of(testServer));

			mockServer.expect(requestTo("http://nx-test:7001/rest/v1/devices/cam-001"))
				.andExpect(method(HttpMethod.GET))
				.andRespond(withSuccess("""
						{"id":"cam-001","name":"Camera 1","status":"Online","deviceType":"Camera"}
						""", MediaType.APPLICATION_JSON));

			VmsCamera result = adapter.getCameraInfo("cam-001");

			assertThat(result.getVmsCameraId()).isEqualTo("cam-001");
			assertThat(result.getDisplayName()).isEqualTo("Camera 1");
			assertThat(result.getStatus()).isEqualTo(CameraStatus.ONLINE);
			mockServer.verify();
		}

		@Test
		@DisplayName("裝置不存在（404）拋 VMS_CAMERA_NOT_FOUND")
		void notFound_throwsException() {
			when(vmsServerRepository.findByTenantIdAndIsActiveTrue("tenant-1")).thenReturn(List.of(testServer));

			mockServer.expect(requestTo("http://nx-test:7001/rest/v1/devices/cam-999"))
				.andExpect(method(HttpMethod.GET))
				.andRespond(withResourceNotFound());

			assertThatThrownBy(() -> adapter.getCameraInfo("cam-999")).isInstanceOf(BusinessException.class)
				.hasFieldOrPropertyWithValue("errorCode", ErrorCode.VMS_CAMERA_NOT_FOUND);
		}

	}

	@Nested
	@DisplayName("healthCheck")
	class HealthCheck {

		@Test
		@DisplayName("VMS 正常時回傳 true（GET /rest/v1/system/info）")
		void serverUp_returnsTrue() {
			when(vmsServerRepository.findByTenantIdAndIsActiveTrue("tenant-1")).thenReturn(List.of(testServer));

			mockServer.expect(requestTo("http://nx-test:7001/rest/v1/system/info"))
				.andExpect(method(HttpMethod.GET))
				.andRespond(withSuccess("""
						{"name":"Test System","version":"5.1.0","customization":"basic"}
						""", MediaType.APPLICATION_JSON));

			boolean result = adapter.healthCheck();
			assertThat(result).isTrue();
		}

		@Test
		@DisplayName("VMS 異常時回傳 false")
		void serverDown_returnsFalse() {
			when(vmsServerRepository.findByTenantIdAndIsActiveTrue("tenant-1")).thenReturn(List.of(testServer));

			mockServer.expect(requestTo("http://nx-test:7001/rest/v1/system/info"))
				.andExpect(method(HttpMethod.GET))
				.andRespond(withServerError());

			boolean result = adapter.healthCheck();
			assertThat(result).isFalse();
		}

	}

	@Nested
	@DisplayName("resolveServer")
	class ResolveServer {

		@Test
		@DisplayName("無啟用 server 時拋 VMS_SERVER_NOT_FOUND")
		void noActiveServer_throwsException() {
			when(vmsServerRepository.findByTenantIdAndIsActiveTrue("tenant-1")).thenReturn(List.of());

			assertThatThrownBy(() -> adapter.getLiveStreamUrl("cam-001")).isInstanceOf(BusinessException.class)
				.hasFieldOrPropertyWithValue("errorCode", ErrorCode.VMS_SERVER_NOT_FOUND);
		}

	}

}
