package com.taipei.iot.tenant.dto;

/**
 * 場域部署模式。
 */
public enum DeploymentMode {

	CLOUD, ON_PREMISE;

	/**
	 * 安全地解析字串為 DeploymentMode，不合法值回傳 null。
	 */
	public static DeploymentMode fromString(String value) {
		if (value == null || value.isBlank())
			return null;
		try {
			return valueOf(value.toUpperCase());
		}
		catch (IllegalArgumentException e) {
			return null;
		}
	}

}
