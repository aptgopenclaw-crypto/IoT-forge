package com.taipei.iot.vms.service;

import com.taipei.iot.vms.dto.VmsStreamLogDTO;
import com.taipei.iot.vms.entity.VmsStreamLogEntity;
import com.taipei.iot.vms.repository.VmsStreamLogRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class VmsStreamLogService {

	private final VmsStreamLogRepository repository;

	public Page<VmsStreamLogDTO> queryLogs(Long userId, Long cameraId, String streamType, LocalDateTime startDate,
			LocalDateTime endDate, Pageable pageable) {
		Specification<VmsStreamLogEntity> spec = (root, query, cb) -> {
			List<Predicate> predicates = new ArrayList<>();
			if (userId != null)
				predicates.add(cb.equal(root.get("userId"), userId));
			if (cameraId != null)
				predicates.add(cb.equal(root.get("cameraId"), cameraId));
			if (streamType != null)
				predicates.add(cb.equal(root.get("streamType"), streamType));
			if (startDate != null)
				predicates.add(cb.greaterThanOrEqualTo(root.get("startedAt"), startDate));
			if (endDate != null)
				predicates.add(cb.lessThanOrEqualTo(root.get("startedAt"), endDate));
			return cb.and(predicates.toArray(new Predicate[0]));
		};

		return repository.findAll(spec, pageable).map(this::toDTO);
	}

	private VmsStreamLogDTO toDTO(VmsStreamLogEntity entity) {
		return VmsStreamLogDTO.builder()
			.id(entity.getId())
			.userId(entity.getUserId())
			.cameraId(entity.getCameraId())
			.streamType(entity.getStreamType())
			.startedAt(entity.getStartedAt())
			.endedAt(entity.getEndedAt())
			.durationSeconds(entity.getDurationSeconds())
			.playbackStartTime(entity.getPlaybackStartTime())
			.playbackEndTime(entity.getPlaybackEndTime())
			.build();
	}

}
