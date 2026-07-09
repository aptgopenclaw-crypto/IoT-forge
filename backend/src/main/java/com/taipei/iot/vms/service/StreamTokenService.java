package com.taipei.iot.vms.service;

import com.taipei.iot.common.enums.ErrorCode;
import com.taipei.iot.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 串流存取 Token 服務。
 *
 * <p>
 * 產生短期 token 附加於播放 URL，ZLMediaKit 播放前透過 hook 向後端驗證 token 有效性。 Token 存於 Redis，key
 * 格式：{@code vms:token:{token}} → {@code cameraId:tenantId}。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StreamTokenService {

	static final String TOKEN_PREFIX = "vms:token:";

	private final StringRedisTemplate redisTemplate;

	/**
	 * 產生串流存取 token。
	 * @param cameraId 攝影機 ID
	 * @param tenantId 租戶 ID
	 * @param ttl token 有效期限
	 * @return token 字串
	 */
	public String generateToken(Long cameraId, String tenantId, Duration ttl) {
		String token = UUID.randomUUID().toString().replace("-", "");
		String value = cameraId + ":" + tenantId;
		redisTemplate.opsForValue().set(TOKEN_PREFIX + token, value, ttl.toSeconds(), TimeUnit.SECONDS);
		return token;
	}

	/**
	 * 驗證 token 並回傳攝影機 ID 與租戶 ID。
	 * @param token token 字串
	 * @return token 驗證結果
	 * @throws BusinessException 若 token 無效或已過期
	 */
	public TokenValidation validateToken(String token) {
		String raw = redisTemplate.opsForValue().getAndDelete(TOKEN_PREFIX + token);
		if (raw == null) {
			throw new BusinessException(ErrorCode.VMS_STREAM_TOKEN_INVALID, "串流 Token 無效或已過期");
		}
		String[] parts = raw.split(":", 2);
		if (parts.length != 2) {
			throw new BusinessException(ErrorCode.VMS_STREAM_TOKEN_INVALID, "串流 Token 格式錯誤");
		}
		return new TokenValidation(Long.parseLong(parts[0]), parts[1]);
	}

	/**
	 * 撤銷 token（立即失效）。
	 */
	public void revokeToken(String token) {
		redisTemplate.delete(TOKEN_PREFIX + token);
	}

	/**
	 * Token 驗證結果。
	 *
	 * @param cameraId 攝影機 ID
	 * @param tenantId 租戶 ID
	 */
	public record TokenValidation(Long cameraId, String tenantId) {
	}

}
