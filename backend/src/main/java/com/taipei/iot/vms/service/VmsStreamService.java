package com.taipei.iot.vms.service;

import com.taipei.iot.common.context.TenantContext;
import com.taipei.iot.common.enums.ErrorCode;
import com.taipei.iot.common.exception.BusinessException;
import com.taipei.iot.vms.dto.StreamRequestDTO;
import com.taipei.iot.vms.entity.VmsCameraMappingEntity;
import com.taipei.iot.vms.entity.VmsStreamLogEntity;
import com.taipei.iot.vms.repository.VmsCameraMappingRepository;
import com.taipei.iot.vms.repository.VmsStreamLogRepository;
import com.taipei.iot.vms.session.HlsSessionManager;
import com.taipei.iot.vms.token.NxTokenManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class VmsStreamService {

	private final VmsCameraMappingRepository cameraRepository;

	private final VmsStreamLogRepository streamLogRepository;

	private final HlsSessionManager sessionManager;

	private final NxTokenManager nxTokenManager;

	public Map<String, Object> createStream(Long cameraId, String userId, StreamRequestDTO request) {
		VmsCameraMappingEntity camera = cameraRepository.findById(cameraId)
			.orElseThrow(() -> new BusinessException(ErrorCode.VMS_CAMERA_NOT_FOUND));

		if ("OFFLINE".equalsIgnoreCase(camera.getStatus()) || "ERROR".equalsIgnoreCase(camera.getStatus())) {
			throw new BusinessException(ErrorCode.VMS_CAMERA_OFFLINE);
		}

		String streamType = "live".equalsIgnoreCase(request.getType()) ? "LIVE" : "PLAYBACK";
		String nxToken = nxTokenManager.getToken(camera.getServerId());

		Instant startTime = request.getStartTime() != null ? Instant.parse(request.getStartTime()) : null;
		Instant endTime = request.getEndTime() != null ? Instant.parse(request.getEndTime()) : null;

		if ("PLAYBACK".equals(streamType) && startTime == null) {
			throw new BusinessException(ErrorCode.VMS_PLAYBACK_INVALID_RANGE, "startTime is required for playback");
		}

		String sessionToken = sessionManager.createSession(userId, cameraId, camera.getServerId(), nxToken, streamType,
				startTime, endTime);

		// Write stream log
		String currentTenantId = TenantContext.getCurrentTenantId();
		VmsStreamLogEntity logEntry = VmsStreamLogEntity.builder()
			.tenantId(currentTenantId != null ? currentTenantId : "0")
			.userId(Long.valueOf(userId))
			.cameraId(cameraId)
			.streamType(streamType)
			.sessionToken(sessionToken)
			.startedAt(LocalDateTime.now())
			.playbackStartTime(startTime != null ? LocalDateTime.ofInstant(startTime, ZoneId.systemDefault()) : null)
			.playbackEndTime(endTime != null ? LocalDateTime.ofInstant(endTime, ZoneId.systemDefault()) : null)
			.build();
		streamLogRepository.save(logEntry);

		return Map.of("sessionToken", sessionToken, "expiresAt",
				java.time.LocalDateTime.now().plusSeconds(300).toString(), "cameraId", cameraId, "streamType",
				streamType);
	}

}
