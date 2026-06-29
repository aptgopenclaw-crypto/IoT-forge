package com.taipei.iot.setting.enums;

import com.taipei.iot.common.enums.ErrorCode;
import com.taipei.iot.common.exception.BusinessException;

import java.net.MalformedURLException;
import java.net.URL;

/**
 * 設定值驗證器 — 每個 {@link SettingKey} 綁定一個實例，確保新增 key 時無法遺漏驗證邏輯。
 */
@FunctionalInterface
public interface SettingValidator {

	/**
	 * 驗證 value 是否合法。不合法時拋出 {@link BusinessException}。
	 */
	void validate(String key, String value);

	// ---- 常用工廠方法 ----

	static SettingValidator intRange(int min, int max) {
		return (key, value) -> {
			try {
				int intValue = Integer.parseInt(value);
				if (intValue < min || intValue > max) {
					throw new BusinessException(ErrorCode.SETTING_INVALID_VALUE,
							key + " must be between " + min + " and " + max);
				}
			}
			catch (NumberFormatException e) {
				throw new BusinessException(ErrorCode.SETTING_INVALID_VALUE, key + " must be a valid integer");
			}
		};
	}

	static SettingValidator url() {
		return (key, value) -> {
			try {
				new URL(value);
			}
			catch (MalformedURLException e) {
				throw new BusinessException(ErrorCode.SETTING_INVALID_VALUE, key + " must be a valid URL");
			}
		};
	}

}
