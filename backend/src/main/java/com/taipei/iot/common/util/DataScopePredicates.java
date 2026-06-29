package com.taipei.iot.common.util;

import com.taipei.iot.dept.enums.DataScopeEnum;

/**
 * {@link DataScopeEnum} 共用 predicate，集中「ALL / 受限 / 僅 owner」幾種常見判斷， 避免各 service 散落
 * {@code if (dataScope == ALL) ... } 樣板。
 *
 * <p>
 * 注意：本類別不負責真正的 DB 查詢過濾（那部分由 {@code DataScopeHelper}、各 repository 處理），
 * 僅提供「業務邏輯分支判斷」的語意化包裝。
 *
 * <h2>使用範例</h2> <pre>{@code
 *   DataScopeEnum scope = DataScopeEnum.fromString(user.getDataScope());
 *   if (DataScopePredicates.restrictsToOwner(scope)
 *           && !entity.getCreatedBy().equals(user.getUserId())) {
 *       throw new BusinessException(ErrorCode.ACCESS_DENIED);
 *   }
 * }</pre>
 *
 * <p>
 * [common v2 F-15]
 */
public final class DataScopePredicates {

	private DataScopePredicates() {
	}

	/**
	 * 是否為「不限制」範圍（{@code ALL} 或 {@code null}）。
	 * <p>
	 * {@code null} 的處理採保守策略：若呼叫端尚未取得 scope（如未認證 / 未綁定）， 預設不視為「不限制」，避免越權。
	 */
	public static boolean isUnrestricted(DataScopeEnum scope) {
		return scope == DataScopeEnum.ALL;
	}

	/**
	 * 是否需要把資料限制在「使用者自己建立」的範圍內。
	 * <p>
	 * 實務上對應「非 ALL scope」的所有狀況：DEPT_ADMIN、THIS_LEVEL、THIS_LEVEL_AND_BELOW、CUSTOM 等
	 * 在「owner-based 資源」（如附件、個人草稿）皆退化為「只能看 / 改自己的」。
	 * @return scope 非 {@code ALL}（含 {@code null}）時為 {@code true}
	 */
	public static boolean restrictsToOwner(DataScopeEnum scope) {
		return !isUnrestricted(scope);
	}

	/**
	 * 是否為部門範圍受限（DEPT_ADMIN-like）— 排除 ALL 與 null。
	 * <p>
	 * 呼叫端通常會搭配 {@code DataScopeHelper.getVisibleDeptIds()} 取得實際 dept_id 清單。
	 */
	public static boolean restrictsToDeptScope(DataScopeEnum scope) {
		return scope != null && scope != DataScopeEnum.ALL;
	}

}
