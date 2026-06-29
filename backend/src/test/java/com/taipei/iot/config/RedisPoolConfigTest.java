package com.taipei.iot.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 驗證 application.yml Redis timeout 與 Lettuce pool 設定正確綁定。 [Config v2 N-2]
 */
@SpringBootTest(classes = RedisPoolConfigTest.class,
		properties = { "spring.data.redis.host=localhost", "spring.data.redis.port=6379",
				"spring.data.redis.password=test", "spring.data.redis.timeout=2000ms",
				"spring.data.redis.lettuce.pool.max-active=20", "spring.data.redis.lettuce.pool.max-idle=10",
				"spring.data.redis.lettuce.pool.min-idle=5", "spring.data.redis.lettuce.pool.max-wait=1000ms",
				"spring.data.redis.lettuce.shutdown-timeout=1s" })
@EnableConfigurationProperties(RedisProperties.class)
@ActiveProfiles("test")
class RedisPoolConfigTest {

	@Autowired
	private RedisProperties redisProperties;

	@Test
	void timeout_shouldBe2Seconds() {
		assertThat(redisProperties.getTimeout()).isEqualTo(Duration.ofMillis(2000));
	}

	@Test
	void lettucePool_maxActive_shouldBe20() {
		assertThat(redisProperties.getLettuce().getPool().getMaxActive()).isEqualTo(20);
	}

	@Test
	void lettucePool_maxIdle_shouldBe10() {
		assertThat(redisProperties.getLettuce().getPool().getMaxIdle()).isEqualTo(10);
	}

	@Test
	void lettucePool_minIdle_shouldBe5() {
		assertThat(redisProperties.getLettuce().getPool().getMinIdle()).isEqualTo(5);
	}

	@Test
	void lettucePool_maxWait_shouldBe1Second() {
		assertThat(redisProperties.getLettuce().getPool().getMaxWait()).isEqualTo(Duration.ofMillis(1000));
	}

	@Test
	void lettuce_shutdownTimeout_shouldBe1Second() {
		assertThat(redisProperties.getLettuce().getShutdownTimeout()).isEqualTo(Duration.ofSeconds(1));
	}

}
