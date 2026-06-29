package com.taipei.iot.config;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * [Config v2 N-8] 驗證 RedisConnectionValidator 在連線成功/失敗時的行為。
 */
class RedisConnectionValidatorTest {

	@Test
	void validateConnection_whenPingSucceeds_shouldNotThrow() {
		RedisConnectionFactory factory = mock(RedisConnectionFactory.class);
		RedisConnection connection = mock(RedisConnection.class);
		when(factory.getConnection()).thenReturn(connection);
		when(connection.ping()).thenReturn("PONG");

		RedisConnectionValidator validator = new RedisConnectionValidator(factory);
		validator.validateConnection();

		verify(connection).ping();
		verify(connection).close();
	}

	@Test
	void validateConnection_whenPingFails_shouldThrowIllegalState() {
		RedisConnectionFactory factory = mock(RedisConnectionFactory.class);
		when(factory.getConnection()).thenThrow(new RuntimeException("Connection refused"));

		RedisConnectionValidator validator = new RedisConnectionValidator(factory);

		assertThatThrownBy(validator::validateConnection).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("Redis connection validation failed");
	}

	@Test
	void validateConnection_whenPingReturnsUnexpected_shouldThrow() {
		RedisConnectionFactory factory = mock(RedisConnectionFactory.class);
		RedisConnection connection = mock(RedisConnection.class);
		when(factory.getConnection()).thenReturn(connection);
		when(connection.ping()).thenReturn("WRONG");

		RedisConnectionValidator validator = new RedisConnectionValidator(factory);

		assertThatThrownBy(validator::validateConnection).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("unexpected response");

		verify(connection).close();
	}

}
