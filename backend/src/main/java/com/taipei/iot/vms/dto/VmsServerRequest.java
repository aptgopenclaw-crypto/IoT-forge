package com.taipei.iot.vms.dto;

import com.taipei.iot.vms.enums.VmsAuthType;
import com.taipei.iot.vms.enums.VmsType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * VMS 伺服器建立/更新請求。
 *
 * @param name VMS 伺服器名稱
 * @param vmsType VMS 類型
 * @param baseUrl VMS REST API 入口
 * @param authType 認證類型（預設 BASIC）
 * @param authUsername 認證使用者名稱
 * @param authPassword 認證密碼
 * @param apiToken API Token（authType=TOKEN 時使用）
 */
public record VmsServerRequest(@NotBlank String name, @NotNull VmsType vmsType, @NotBlank String baseUrl,
		VmsAuthType authType, String authUsername, String authPassword, String apiToken) {
}
