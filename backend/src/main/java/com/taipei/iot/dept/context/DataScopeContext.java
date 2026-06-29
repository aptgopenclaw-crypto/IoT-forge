package com.taipei.iot.dept.context;

/**
 * 【預留框架】ThreadLocal 資料範圍上下文 — 由 {@link com.taipei.iot.dept.aspect.DataPermissionAspect}
 * 寫入，供 Repository 層讀取當前請求的資料範圍過濾條件。
 *
 * <p>
 * <b>目前狀態（2026-05）：</b>僅被 Aspect 寫入，無任何 Repository/Service 消費端讀取。 各模組採
 * {@link com.taipei.iot.dept.service.DataScopeHelper} 手動控制。 保留為日後統一改造時啟用。
 * </p>
 */
public final class DataScopeContext {

	private static final ThreadLocal<DataScopeFilter> CONTEXT = new ThreadLocal<>();

	private DataScopeContext() {
	}

	public static void set(DataScopeFilter filter) {
		CONTEXT.set(filter);
	}

	public static DataScopeFilter get() {
		return CONTEXT.get();
	}

	public static void clear() {
		CONTEXT.remove();
	}

}
