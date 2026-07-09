package com.taipei.iot.vms.adapter;

import com.taipei.iot.common.context.TenantContext;
import com.taipei.iot.common.enums.ErrorCode;
import com.taipei.iot.common.exception.BusinessException;
import com.taipei.iot.vms.dto.CameraStreamInfo;
import com.taipei.iot.vms.entity.VmsServer;
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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * {@link AxxonAdapter} 與 Axxon Next REST API 的整合行為。
 */
@ExtendWith(MockitoExtension.class)
class AxxonAdapterTest {

	@Mock
	private VmsServerRepository vmsServerRepository;

	private AxxonAdapter adapter;

	private MockRestServiceServer mockServer;

	private final VmsServer testServer = VmsServer.builder()
		.id(1L)
		.tenantId("tenant-1")
		.name("Axxon Server")
		.vmsType(VmsType.AXXON)
		.baseUrl("http://axxon:8080")
		.authType(VmsAuthType.BASIC)
		.authUsername("admin")
		.authPassword("pass")
		.isActive(true)
		.build();

	@BeforeEach
	void setUp() {
		TenantContext.setCurrentTenantId("tenant-1");

		RestClient.Builder builder = RestClient.builder().baseUrl(testServer.getBaseUrl());
		mockServer = MockRestServiceServer.bindTo(builder).build();
		RestClient restClient = builder.build();

		adapter = new AxxonAdapter(vmsServerRepository) {
			@Override
			RestClient buildRestClient(VmsServer server) {
				return restClient;
			}
		};
	}

	@AfterEach
	void tearDown() {
		TenantContext.clear();
	}

	@Nested
	@DisplayName("getLiveStreamUrl")
	class GetLiveStreamUrl {

		@Test
		@DisplayName("成功取得 RTSP URL")
		void success() {
			when(vmsServerRepository.findByTenantIdAndIsActiveTrue("tenant-1")).thenReturn(List.of(testServer));

			mockServer.expect(requestTo("http://axxon:8080/api/rest/v1/cameras/cam-001/stream"))
				.andExpect(method(HttpMethod.GET))
				.andRespond(withSuccess("""
						{"streamUrl":"rtsp://axxon:554/cam-001"}
						""", MediaType.APPLICATION_JSON));

			CameraStreamInfo result = adapter.getLiveStreamUrl("cam-001");

			assertThat(result.cameraId()).isEqualTo("cam-001");
			assertThat(result.rtspUrl()).isEqualTo("rtsp://axxon:554/cam-001");
			assertThat(result.vmsType()).isEqualTo(VmsType.AXXON);
			mockServer.verify();
		}

		@Test
		@DisplayName("串流 URL 為空時拋 VMS_STREAM_NOT_AVAILABLE")
		void emptyResponse_throwsException() {
			when(vmsServerRepository.findByTenantIdAndIsActiveTrue("tenant-1")).thenReturn(List.of(testServer));

			mockServer.expect(requestTo("http://axxon:8080/api/rest/v1/cameras/cam-001/stream"))
				.andExpect(method(HttpMethod.GET))
				.andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

			assertThatThrownBy(() -> adapter.getLiveStreamUrl("cam-001")).isInstanceOf(BusinessException.class)
				.hasFieldOrPropertyWithValue("errorCode", ErrorCode.VMS_STREAM_NOT_AVAILABLE);
		}

	}

	@Nested
	@DisplayName("healthCheck")
	class HealthCheck {

		@Test
		@DisplayName("VMS 正常時回傳 true")
		void serverUp_returnsTrue() {
			when(vmsServerRepository.findByTenantIdAndIsActiveTrue("tenant-1")).thenReturn(List.of(testServer));

			mockServer.expect(requestTo("http://axxon:8080/api/rest/v1/system/status"))
				.andExpect(method(HttpMethod.GET))
				.andRespond(withSuccess());

			assertThat(adapter.healthCheck()).isTrue();
			mockServer.verify();
		}

		@Test
		@DisplayName("VMS 異常時回傳 false")
		void serverDown_returnsFalse() {
			when(vmsServerRepository.findByTenantIdAndIsActiveTrue("tenant-1")).thenReturn(List.of(testServer));

			mockServer.expect(requestTo("http://axxon:8080/api/rest/v1/system/status"))
				.andExpect(method(HttpMethod.GET))
				.andRespond(withServerError());

			assertThat(adapter.healthCheck()).isFalse();
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
