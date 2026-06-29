package com.taipei.iot.announcement.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;

/**
 * 公告分類。預設 GENERAL（一般）；其餘為對使用者具有不同語意提示的類別， 由前端依分類給予不同顏色標籤呈現。
 */
@Getter
@RequiredArgsConstructor
public enum AnnouncementCategory {

	GENERAL("GENERAL"), SYSTEM("SYSTEM"), POLICY("POLICY"), EVENT("EVENT"), MAINTENANCE("MAINTENANCE");

	private final String value;

	public static boolean isValid(String value) {
		if (value == null)
			return false;
		return Arrays.stream(values()).anyMatch(c -> c.value.equals(value));
	}

}
