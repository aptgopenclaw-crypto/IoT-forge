package com.taipei.iot.vms.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class StreamRequestDTO {

	private String type; // "live" or "playback"

	private String startTime; // ISO format for playback

	private String endTime; // ISO format for playback

}
