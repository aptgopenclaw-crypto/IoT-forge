package com.taipei.iot.vms.dto;

import com.taipei.iot.vms.entity.VmsServer;
import com.taipei.iot.vms.enums.VmsAuthType;
import com.taipei.iot.vms.enums.VmsType;

import java.time.LocalDateTime;

/**
 * VMS 伺服器回應 DTO。
 *
 * @param id 伺服器 ID
 * @param name 伺服器名稱
 * @param vmsType VMS 類型
 * @param baseUrl REST API 入口
 * @param authType 認證類型
 * @param isActive 是否啟用
 * @param createdAt 建立時間
 */
public record VmsServerResponse(Long id, String name, VmsType vmsType, String baseUrl, VmsAuthType authType,
		Boolean isActive, LocalDateTime createdAt) {

	public static VmsServerResponse from(VmsServer entity) {
		return new VmsServerResponse(entity.getId(), entity.getName(), entity.getVmsType(), entity.getBaseUrl(),
				entity.getAuthType(), entity.getIsActive(), entity.getCreatedAt());
	}
}
