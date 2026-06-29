package com.taipei.iot.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Component;

/**
 * [Config v2 N-8] Redis 啟動連線檢查 — 確保應用啟動時 Redis 可達。 連不上時 fail-fast，避免 runtime 才發現 Redis
 * 不可用。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "spring.data.redis.host")
public class RedisConnectionValidator {

	private final RedisConnectionFactory connectionFactory;

	@PostConstruct
	void validateConnection() {
		try (var connection = connectionFactory.getConnection()) {
			String pong = connection.ping();
			if (!"PONG".equals(pong)) {
				throw new IllegalStateException("Redis ping returned unexpected response: " + pong);
			}
			log.info("[Redis] Startup ping successful — connection validated");
		}
		catch (IllegalStateException e) {
			throw e;
		}
		catch (Exception e) {
			throw new IllegalStateException("Redis connection validation failed on startup. "
					+ "Ensure Redis is reachable at the configured host/port.", e);
		}
	}

}
