package com.taipei.iot.vms.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taipei.iot.common.enums.ErrorCode;
import com.taipei.iot.common.exception.BusinessException;
import com.taipei.iot.vms.entity.VmsServer;
import com.taipei.iot.vms.enums.VmsAuthType;
import com.taipei.iot.vms.enums.VmsType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class NxSessionManagerTest {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private NxSessionManager sessionManager;

	private HttpClient httpClient;

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
	@SuppressWarnings("unchecked")
	void setUp() throws Exception {
		httpClient = mock(HttpClient.class);
		var response = mock(HttpResponse.class);
		when(response.statusCode()).thenReturn(200);
		when(response.body()).thenReturn(
				"{\"id\":\"uuid\",\"username\":\"admin\",\"token\":\"session-token-abc\",\"ageS\":0,\"expiresInS\":3600}");
		when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);

		sessionManager = new NxSessionManager(httpClient);
	}

	@Nested
	@DisplayName("getToken")
	class GetToken {

		@Test
		@DisplayName("首次呼叫發送 login request 並回傳 token")
		void firstCall_logsInAndReturnsToken() throws Exception {
			String token = sessionManager.getToken(testServer);
			assertThat(token).isEqualTo("session-token-abc");
			verify(httpClient).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
		}

		@Test
		@DisplayName("cache hit（未到期）不發送 login request")
		void cacheHit_doesNotLogin() throws Exception {
			String firstToken = sessionManager.getToken(testServer);
			assertThat(firstToken).isEqualTo("session-token-abc");

			reset(httpClient);

			String secondToken = sessionManager.getToken(testServer);
			assertThat(secondToken).isEqualTo("session-token-abc");
			verify(httpClient, never()).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
		}

	}

	@Nested
	@DisplayName("invalidate")
	class Invalidate {

		@Test
		@DisplayName("清除 cache 後重新 login")
		void afterInvalidate_logsInAgain() throws Exception {
			String firstToken = sessionManager.getToken(testServer);
			assertThat(firstToken).isEqualTo("session-token-abc");

			sessionManager.invalidate(1L);

			String secondToken = sessionManager.getToken(testServer);
			assertThat(secondToken).isEqualTo("session-token-abc");
			verify(httpClient, times(2)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
		}

	}

	@Nested
	@DisplayName("login failure")
	class LoginFailure {

		@Test
		@DisplayName("登入失敗（token is null）拋 VMS_CONNECTION_FAILED")
		void nullResponse_throwsVmsConnectionFailed() throws Exception {
			reset(httpClient);
			var response = mock(HttpResponse.class);
			when(response.statusCode()).thenReturn(200);
			when(response.body()).thenReturn("{}");
			when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);

			assertThatThrownBy(() -> sessionManager.getToken(testServer)).isInstanceOf(BusinessException.class)
				.hasFieldOrPropertyWithValue("errorCode", ErrorCode.VMS_CONNECTION_FAILED);
		}

		@Test
		@DisplayName("登入 HTTP 錯誤（404）拋 BusinessException")
		void httpError_throwsException() throws Exception {
			reset(httpClient);
			var response = mock(HttpResponse.class);
			when(response.statusCode()).thenReturn(404);
			when(response.body()).thenReturn("{\"error\":\"9\",\"errorId\":\"notFound\",\"errorString\":\"\"}");
			when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);

			assertThatThrownBy(() -> sessionManager.getToken(testServer)).isInstanceOf(BusinessException.class)
				.hasFieldOrPropertyWithValue("errorCode", ErrorCode.VMS_CONNECTION_FAILED);
		}

	}

}
