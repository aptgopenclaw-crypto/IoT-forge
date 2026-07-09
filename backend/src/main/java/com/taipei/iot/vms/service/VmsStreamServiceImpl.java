package com.taipei.iot.vms.service;

import com.taipei.iot.common.context.TenantContext;
import com.taipei.iot.common.enums.ErrorCode;
import com.taipei.iot.common.exception.BusinessException;
import com.taipei.iot.vms.VmsAdapterManager;
import com.taipei.iot.vms.config.VmsProperties;
import com.taipei.iot.vms.dto.CameraLiveResponse;
import com.taipei.iot.vms.dto.CameraPlaybackResponse;
import com.taipei.iot.vms.dto.PtzCommand;
import com.taipei.iot.vms.entity.VmsCamera;
import com.taipei.iot.vms.entity.VmsServer;
import com.taipei.iot.vms.repository.VmsCameraRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;

/**
 * VMS 串流服務實作。
 *
 * <p>
 * 流程：
 * <ol>
 * <li>查詢本地 {@code vms_cameras} 取得攝影機 + 關聯 VMS 伺服器</li>
 * <li>依伺服器類型取得對應 {@code VmsAdapter}</li>
 * <li>檢查 Redis cache — cache hit 直接回傳</li>
 * <li>cache miss：adapter 取得 RTSP URL → ZLMediaKit 轉換 → cache → 回傳</li>
 * </ol>
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VmsStreamServiceImpl implements VmsStreamService {

	private static final String CACHE_KEY_PREFIX = "vms:stream:";

	private static final String STREAM_ID_PREFIX = "vms_";

	private final VmsCameraRepository vmsCameraRepository;

	private final VmsAdapterManager vmsAdapterManager;

	private final ZlMediaKitClient zlMediaKitClient;

	private final StringRedisTemplate redisTemplate;

	private final VmsProperties vmsProperties;

	private final StreamTokenService streamTokenService;

	@Override
	@Transactional(readOnly = true)
	public CameraLiveResponse getLiveStream(Long cameraId) {
		String tenantId = TenantContext.getCurrentTenantId();
		VmsCamera camera = findCamera(cameraId, tenantId);
		VmsServer server = camera.getServer();

		// 1. 檢查 Redis cache
		String cacheKey = CACHE_KEY_PREFIX + cameraId;
		String cachedUrl = redisTemplate.opsForValue().get(cacheKey);
		if (cachedUrl != null) {
			log.debug("串流 cache hit: cameraId={}", cameraId);
			return new CameraLiveResponse(cameraId, camera.getDisplayName(), cachedUrl,
					Instant.now().plus(vmsProperties.getStreamCacheTtlSeconds(), ChronoUnit.SECONDS),
					camera.getStatus());
		}

		// 2. cache miss：走完整流程
		String vmsCameraId = camera.getVmsCameraId();
		var streamInfo = vmsAdapterManager.getAdapter(server.getVmsType()).getLiveStreamUrl(vmsCameraId);

		// 3. ZLMediaKit 轉換串流
		String streamId = STREAM_ID_PREFIX + cameraId;
		String playUrl = zlMediaKitClient.addStreamProxy(streamInfo.rtspUrl(), streamId);

		// 4. 產生串流 token 附加於播放 URL
		int ttl = vmsProperties.getStreamCacheTtlSeconds();
		String token = streamTokenService.generateToken(cameraId, tenantId, Duration.ofSeconds(ttl));
		String securedPlayUrl = playUrl + "?token=" + token;

		// 5. 寫入 Redis cache（含 token 的完整 URL）
		redisTemplate.opsForValue().set(cacheKey, securedPlayUrl, ttl, TimeUnit.SECONDS);

		log.info("即時串流已建立: cameraId={}, playUrl={}", cameraId, securedPlayUrl);
		return new CameraLiveResponse(cameraId, camera.getDisplayName(), securedPlayUrl,
				Instant.now().plus(ttl, ChronoUnit.SECONDS), camera.getStatus());
	}

	@Override
	@Transactional(readOnly = true)
	public CameraPlaybackResponse getPlayback(Long cameraId, Instant startTime, Instant endTime) {
		if (startTime == null || endTime == null || !startTime.isBefore(endTime)) {
			throw new BusinessException(ErrorCode.VMS_PLAYBACK_INVALID_RANGE, "回放時間範圍無效");
		}

		String tenantId = TenantContext.getCurrentTenantId();
		VmsCamera camera = findCamera(cameraId, tenantId);
		VmsServer server = camera.getServer();

		// 歷史回放不 cache（時間範圍每次不同），直接走 adapter + ZLMediaKit
		String streamId = STREAM_ID_PREFIX + cameraId + "_playback";
		var streamInfo = vmsAdapterManager.getAdapter(server.getVmsType())
			.getPlaybackUrl(camera.getVmsCameraId(), startTime, endTime);

		String playUrl = zlMediaKitClient.addStreamProxy(streamInfo.rtspUrl(), streamId);
		String token = streamTokenService.generateToken(cameraId, tenantId,
				Duration.ofSeconds(vmsProperties.getStreamCacheTtlSeconds()));
		String securedPlayUrl = playUrl + "?token=" + token;

		return new CameraPlaybackResponse(cameraId, camera.getDisplayName(), securedPlayUrl, startTime, endTime,
				camera.getStatus());
	}

	@Override
	public void controlPtz(Long cameraId, PtzCommand command) {
		String tenantId = TenantContext.getCurrentTenantId();
		VmsCamera camera = findCamera(cameraId, tenantId);
		VmsServer server = camera.getServer();

		vmsAdapterManager.getAdapter(server.getVmsType()).controlPtz(camera.getVmsCameraId(), command);
	}

	@Override
	public void releaseStream(Long cameraId) {
		String cacheKey = CACHE_KEY_PREFIX + cameraId;

		// 清除 Redis cache
		redisTemplate.delete(cacheKey);

		// 通知 ZLMediaKit 關閉串流
		String streamId = STREAM_ID_PREFIX + cameraId;
		zlMediaKitClient.closeStream(streamId);

		log.info("串流已釋放: cameraId={}", cameraId);
	}

	private VmsCamera findCamera(Long cameraId, String tenantId) {
		return vmsCameraRepository.findByIdAndTenantId(cameraId, tenantId)
			.orElseThrow(() -> new BusinessException(ErrorCode.VMS_CAMERA_NOT_FOUND, "攝影機不存在: id=" + cameraId));
	}

}
