package com.taipei.iot.dept.enums;

import lombok.extern.slf4j.Slf4j;

/**
 * @deprecated 已遷移至 {@link com.taipei.iot.common.enums.DataScopeEnum}， 此類別保留僅供 Transient
 * 期的向後相容，日後將移除。 請改用 {@code com.taipei.iot.common.enums.DataScopeEnum}。
 */
@Deprecated
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
