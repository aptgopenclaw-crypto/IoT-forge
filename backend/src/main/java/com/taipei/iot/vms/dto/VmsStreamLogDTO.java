package com.taipei.iot.vms.dto;

import lombok.*;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VmsStreamLogDTO {

	private Long id;

	private String userId;

	private String userName;

	private Long cameraId;

	private String cameraName;

	private String streamType;

	private LocalDateTime startedAt;

	private LocalDateTime endedAt;

	private Integer durationSeconds;

	private LocalDateTime playbackStartTime;

	private LocalDateTime playbackEndTime;

}
