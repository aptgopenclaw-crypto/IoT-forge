package com.taipei.iot.setting.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemSettingDto {

	private String settingKey;

	private String settingValue;

	private String description;

}
