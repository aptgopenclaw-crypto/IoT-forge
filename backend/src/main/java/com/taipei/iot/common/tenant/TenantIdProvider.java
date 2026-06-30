package com.taipei.iot.common.tenant;

import java.util.List;

/**
 * Port：提供系統中已啟用的租戶識別碼列表。
 *
 * <p>
 * {@code tenant} 模組實作本介面；{@code audit}（排程清除任務）等模組依賴本介面（定義於 {@code common}），藉此打破
 * {@code audit → tenant} 的直接依賴。
 */
public interface TenantIdProvider {

	/**
	 * 回傳所有已啟用租戶的 tenantId 列表。
	 */
	List<String> findEnabledTenantIds();

}
