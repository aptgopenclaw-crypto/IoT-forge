package com.taipei.iot.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 登入後首次選定租戶的請求（temporary token → access/refresh token）。
 *
 * <p>
 * 與 {@link SwitchTenantRequest} 目前欄位相同，但代表不同 use case： 本 DTO 用於 <b>登入流程</b>第一次選定租戶（前端
 * SelectTenantView）， 而 {@code SwitchTenantRequest} 用於 <b>已登入 session 中</b>切換租戶（前端
 * TenantSwitcher）。 故意保留兩個型別以維持 API 語意分離、方便日後各自演進，請勿合併。
 * </p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SelectTenantRequest {

	@NotBlank(message = "tenantId is required")
	@Size(max = 50, message = "tenantId must not exceed 50 characters")
	private String tenantId;

}
