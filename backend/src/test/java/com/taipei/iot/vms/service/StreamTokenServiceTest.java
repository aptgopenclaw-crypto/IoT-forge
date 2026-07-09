package com.taipei.iot.vms.service;

import com.taipei.iot.common.enums.ErrorCode;
import com.taipei.iot.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link StreamTokenService} token 生命週期行為。
 */
@ExtendWith(MockitoExtension.class)
class StreamTokenServiceTest {

	@Mock
	private StringRedisTemplate redisTemplate;

	@Mock
	private ValueOperations<String, String> valueOps;

	private StreamTokenService service;

	@BeforeEach
	void setUp() {
		service = new StreamTokenService(redisTemplate);
		when(redisTemplate.opsForValue()).thenReturn(valueOps);
	}

	@Nested
	@DisplayName("generateToken")
	class GenerateToken {

		@Test
		@DisplayName("產生 token 並寫入 Redis (TTL)")
		void generatesAndStoresToken() {
			String token = service.generateToken(1L, "tenant-1", Duration.ofSeconds(300));

			assertThat(token).isNotNull().hasSize(32); // UUID without dashes
			verify(valueOps).set(org.mockito.ArgumentMatchers.startsWith("vms:token:"),
					org.mockito.ArgumentMatchers.eq("1:tenant-1"), org.mockito.ArgumentMatchers.eq(300L),
					org.mockito.ArgumentMatchers.any());
		}

	}

	@Nested
	@DisplayName("validateToken")
	class ValidateToken {

		@Test
		@DisplayName("有效 token → 回傳 cameraId + tenantId")
		void validToken_returnsValidation() {
			when(valueOps.getAndDelete("vms:token:valid-token")).thenReturn("1:tenant-1");

			var result = service.validateToken("valid-token");

			assertThat(result.cameraId()).isEqualTo(1L);
			assertThat(result.tenantId()).isEqualTo("tenant-1");
		}

		@Test
		@DisplayName("無效 token → 拋 VMS_STREAM_TOKEN_INVALID")
		void invalidToken_throwsException() {
			when(valueOps.getAndDelete("vms:token:bad")).thenReturn(null);

			assertThatThrownBy(() -> service.validateToken("bad")).isInstanceOf(BusinessException.class)
				.hasFieldOrPropertyWithValue("errorCode", ErrorCode.VMS_STREAM_TOKEN_INVALID);
		}

	}

	@Nested
	@DisplayName("revokeToken")
	class RevokeToken {

		@Test
		@DisplayName("撤銷 token (Redis DELETE)")
		void revokesToken() {
			service.revokeToken("some-token");

			verify(redisTemplate).delete("vms:token:some-token");
		}

	}

}
