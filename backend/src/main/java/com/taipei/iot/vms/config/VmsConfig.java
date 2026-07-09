package com.taipei.iot.vms.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * VMS 模組設定。
 */
@Configuration
@EnableConfigurationProperties(VmsProperties.class)
public class VmsConfig {

}
