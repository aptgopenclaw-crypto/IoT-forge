package com.taipei.iot.vms.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "vms")
@Getter
@Setter
public class VmsConfig {

	/** Session TTL in seconds (default 300 = 5 min) */
	private int sessionTtlSeconds = 300;

	/** Token refresh margin ratio (default 0.9 = refresh at 90% of expiry) */
	private double tokenRefreshRatio = 0.9;

}
