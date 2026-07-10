package com.taipei.iot.announcement.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AnnouncementStatus {

	DRAFT("DRAFT"), PUBLISHED("PUBLISHED");

	private final String value;

}
