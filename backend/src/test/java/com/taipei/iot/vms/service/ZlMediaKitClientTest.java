package com.taipei.iot.vms.service;

import com.taipei.iot.common.exception.BusinessException;
import com.taipei.iot.common.enums.ErrorCode;
import com.taipei.iot.vms.config.VmsProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

/**
 * {@link ZlMediaKitClient} 與 ZLMediaKit REST API 的整合行為。
 */
class ZlMediaKitClientTest {

	private ZlMediaKitClient client;

	private MockRestServiceServer mockServer;

	private final VmsProperties properties = new VmsProperties();

	@BeforeEach
	void setUp() {
		properties.getMediaServer().setApiUrl("http://zlmediakit:8080");
		properties.getMediaServer().setPublicUrl("http://mediaserver:8080");

		RestClient.Builder builder = RestClient.builder().baseUrl(properties.getMediaServer().getApiUrl());
		mockServer = MockRestServiceServer.bindTo(builder).build();
		client = new ZlMediaKitClient(properties, builder);
	}

	@Nested
	@DisplayName("addStreamProxy")
	class AddStreamProxy {

		@Test
		@DisplayName("成功建立串流，回傳播放 URL")
		void success() {
			mockServer.expect(requestTo("http://zlmediakit:8080/index/api/addStreamProxy"))
				.andExpect(method(HttpMethod.POST))
				.andExpect(content().json("""
						{"stream":"vms_cam-001","url":"rtsp://localhost/test"}
						"""))
				.andRespond(withSuccess("""
						{"code":0,"msg":"success","data":{"stream_id":"vms_cam-001"}}
						""", MediaType.APPLICATION_JSON));

			String playUrl = client.addStreamProxy("rtsp://localhost/test", "vms_cam-001");

			assertThat(playUrl)
				.isEqualTo("http://mediaserver:8080/webrtcplayer/?streamId=vms_cam-001&app=vms&schema=http");
			mockServer.verify();
		}

		@Test
		@DisplayName("ZLMediaKit 回傳錯誤 code 時拋 VMS_STREAM_NOT_AVAILABLE")
		void errorCode_throwsException() {
			mockServer.expect(requestTo("http://zlmediakit:8080/index/api/addStreamProxy"))
				.andExpect(method(HttpMethod.POST))
				.andRespond(withSuccess("""
						{"code":-1,"msg":"invalid stream"}
						""", MediaType.APPLICATION_JSON));

			assertThatThrownBy(() -> client.addStreamProxy("rtsp://localhost/test", "vms_cam-001"))
				.isInstanceOf(BusinessException.class)
				.hasFieldOrPropertyWithValue("errorCode", ErrorCode.VMS_STREAM_NOT_AVAILABLE);
		}

	}

	@Nested
	@DisplayName("closeStream")
	class CloseStream {

		@Test
		@DisplayName("成功關閉串流（不拋異常）")
		void success() {
			mockServer.expect(requestTo("http://zlmediakit:8080/index/api/close_stream"))
				.andExpect(method(HttpMethod.POST))
				.andExpect(content().json("""
						{"stream":"vms_cam-001"}
						"""))
				.andRespond(withSuccess());

			client.closeStream("vms_cam-001");
			mockServer.verify();
		}

		@Test
		@DisplayName("關閉不存在的串流不拋異常（吞 error）")
		void closeNonExistent_doesNotThrow() {
			mockServer.expect(requestTo("http://zlmediakit:8080/index/api/close_stream"))
				.andExpect(method(HttpMethod.POST))
				.andRespond(withServerError());

			// 不拋異常，僅 log warning
			client.closeStream("nonexistent");
		}

	}

}
