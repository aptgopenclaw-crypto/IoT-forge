package com.taipei.iot.dept.enums;

import lombok.extern.slf4j.Slf4j;

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
