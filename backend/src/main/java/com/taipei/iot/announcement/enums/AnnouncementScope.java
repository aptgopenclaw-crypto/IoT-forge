package com.taipei.iot.announcement.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AnnouncementScope {

	ALL("ALL"), DEPT("DEPT");

	private final String value;

}
