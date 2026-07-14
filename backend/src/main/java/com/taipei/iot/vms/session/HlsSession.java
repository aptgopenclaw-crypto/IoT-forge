package com.taipei.iot.vms.session;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;

@Getter
@AllArgsConstructor
public class HlsSession {

	private String sessionToken;

	private String userId;

	private Long cameraId;

	private Long serverId;

	private String nxToken;

	private String streamType; // "LIVE" or "PLAYBACK"

	private Instant startTime; // playback: start pos (unix ms epoch)

	private Instant endTime; // playback: end pos

	private Instant createdAt;

}
