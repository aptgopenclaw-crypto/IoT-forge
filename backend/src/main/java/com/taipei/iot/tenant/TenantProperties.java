package com.taipei.iot.tenant;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "tenant")
public class TenantProperties {

	/** single = 固定單一租戶 / multi = 多租戶 */
	private String mode = "single";

	/** single 模式下的固定 tenant id */
	private String defaultId = "DEFAULT";

	public String getMode() {
		return mode;
	}

	public void setMode(String mode) {
		this.mode = mode;
	}

	public String getDefaultId() {
		return defaultId;
	}

	public void setDefaultId(String defaultId) {
		this.defaultId = defaultId;
	}

}
