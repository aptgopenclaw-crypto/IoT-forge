package com.taipei.iot.common.enums;

import lombok.extern.slf4j.Slf4j;

/**
 * 資料權限範圍列舉。
 *
 * <p>
 * 原位於 {@code dept.enums}，因是跨模組共用的核心概念，下沉至 {@code common.enums}。
 */
@Slf4j
public enum DataScopeEnum {

	ALL, THIS_LEVEL, THIS_LEVEL_AND_BELOW;

	public static DataScopeEnum fromString(String value) {
		if (value == null) {
			return ALL;
		}
		try {
			return DataScopeEnum.valueOf(value);
		}
		catch (IllegalArgumentException e) {
			log.warn("Unknown DataScope value '{}', falling back to ALL", value);
			return ALL;
		}
	}

}
