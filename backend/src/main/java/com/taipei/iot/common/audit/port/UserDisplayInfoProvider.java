package com.taipei.iot.common.audit.port;

import java.util.Optional;

/**
 * Port：根據 userId 查詢使用者顯示資訊。
 *
 * <p>
 * {@code auth} 模組實作本介面；{@code audit} 模組依賴本介面（定義於 {@code common}）， 藉此打破
 * {@code audit → auth} 的直接依賴。
 */
public interface UserDisplayInfoProvider {

	/**
	 * 根據 userId 取得顯示用資訊；若找不到使用者則回傳 {@code Optional.empty()}。
	 */
	Optional<UserDisplayInfo> findByUserId(String userId);

}
