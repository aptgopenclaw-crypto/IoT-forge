package com.taipei.iot.common.auth.port;

import com.taipei.iot.common.policy.PasswordPolicy;

/**
 * Port：根據 tenantId 解析當前有效的密碼原則。
 *
 * <p>
 * {@code auth} 模組實作本介面；{@code user} 及 {@code tenant} 模組依賴本介面 （定義於 {@code common}），藉此打破
 * {@code user/tenant → auth.policy} 的直接依賴。
 */
public interface PasswordPolicyProvider {

	/**
	 * 解析並回傳指定租戶的有效密碼原則。
	 * @param tenantId 租戶 ID；傳入 {@code null} 時使用平台預設值
	 */
	PasswordPolicy resolve(String tenantId);

}
