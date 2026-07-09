package com.taipei.iot.vms.adapter;

import com.taipei.iot.common.enums.ErrorCode;
import com.taipei.iot.common.exception.BusinessException;
import com.taipei.iot.vms.entity.VmsServer;
import com.taipei.iot.vms.enums.VmsAuthType;
import com.taipei.iot.vms.enums.VmsType;
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
 * {@link NxSessionManager} 行為測試。
 */
class NxSessionManagerTest {

	private NxSessionManager sessionManager;

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
		RestClient.Builder builder = RestClient.builder();
		mockServer = MockRestServiceServer.bindTo(builder).build();
		sessionManager = new NxSessionManager(builder);
	}

	@Nested
	@DisplayName("getToken")
	class GetToken {

		@Test
		@DisplayName("首次呼叫發送 login request 並回傳 token")
		void firstCall_logsInAndReturnsToken() {
			mockServer.expect(requestTo("http://nx-test:7001/rest/v1/login/sessions"))
				.andExpect(method(HttpMethod.POST))
				.andExpect(content().json("""
						{"username":"admin","password":"pass"}
						"""))
				.andRespond(withSuccess("""
						{"id":"uuid","username":"admin","token":"session-token-abc","ageS":0,"expiresInS":3600}
						""", MediaType.APPLICATION_JSON));

			String token = sessionManager.getToken(testServer);

			assertThat(token).isEqualTo("session-token-abc");
			mockServer.verify();
		}

		@Test
		@DisplayName("cache hit（未到期）不發送 login request")
		void cacheHit_doesNotLogin() {
			// 第一次呼叫：login → cache
			mockServer.expect(requestTo("http://nx-test:7001/rest/v1/login/sessions"))
				.andExpect(method(HttpMethod.POST))
				.andRespond(withSuccess("""
						{"id":"uuid","username":"admin","token":"cached-token","ageS":0,"expiresInS":3600}
						""", MediaType.APPLICATION_JSON));

			String firstToken = sessionManager.getToken(testServer);
			assertThat(firstToken).isEqualTo("cached-token");
			mockServer.verify();

			// 第二次呼叫：應從 cache 取得，不發送任何請求
			String secondToken = sessionManager.getToken(testServer);
			assertThat(secondToken).isEqualTo("cached-token");
		}

	}

	@Nested
	@DisplayName("invalidate")
	class Invalidate {

		@Test
		@DisplayName("清除 cache 後重新 login")
		void afterInvalidate_logsInAgain() {
			// 第一次呼叫：login（加入兩次 expectation，因 invalidate 後會再次 login）
			mockServer.expect(requestTo("http://nx-test:7001/rest/v1/login/sessions"))
				.andExpect(method(HttpMethod.POST))
				.andRespond(withSuccess("""
						{"id":"uuid","username":"admin","token":"token-v1","ageS":0,"expiresInS":3600}
						""", MediaType.APPLICATION_JSON));
			mockServer.expect(requestTo("http://nx-test:7001/rest/v1/login/sessions"))
				.andExpect(method(HttpMethod.POST))
				.andRespond(withSuccess("""
						{"id":"uuid","username":"admin","token":"token-v2","ageS":0,"expiresInS":3600}
						""", MediaType.APPLICATION_JSON));

			// 第一次呼叫：使用 token-v1
			String firstToken = sessionManager.getToken(testServer);
			assertThat(firstToken).isEqualTo("token-v1");

			// invalidate 清除 cache
			sessionManager.invalidate(1L);

			// 第二次呼叫：應重新 login，使用 token-v2
			String secondToken = sessionManager.getToken(testServer);
			assertThat(secondToken).isEqualTo("token-v2");

			mockServer.verify();
		}

	}

	@Nested
	@DisplayName("login failure")
	class LoginFailure {

		@Test
		@DisplayName("登入失敗（空回應）拋 VMS_CONNECTION_FAILED")
		void nullResponse_throwsVmsConnectionFailed() {
			mockServer.expect(requestTo("http://nx-test:7001/rest/v1/login/sessions"))
				.andExpect(method(HttpMethod.POST))
				.andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

			assertThatThrownBy(() -> sessionManager.getToken(testServer)).isInstanceOf(BusinessException.class)
				.hasFieldOrPropertyWithValue("errorCode", ErrorCode.VMS_CONNECTION_FAILED);
		}

		@Test
		@DisplayName("登入 HTTP 錯誤拋 RestClientException")
		void httpError_throwsException() {
			mockServer.expect(requestTo("http://nx-test:7001/rest/v1/login/sessions"))
				.andExpect(method(HttpMethod.POST))
				.andRespond(withUnauthorizedRequest());

			assertThatThrownBy(() -> sessionManager.getToken(testServer)).isInstanceOf(Exception.class);
		}

	}

}
