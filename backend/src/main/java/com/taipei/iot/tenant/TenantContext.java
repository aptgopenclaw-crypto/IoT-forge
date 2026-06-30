package com.taipei.iot.tenant;

/**
 * @deprecated 已遷移至 {@link com.taipei.iot.common.context.TenantContext}， 此類別保留僅供 Transient
 * 期的向後相容，日後將移除。 請改用 {@code com.taipei.iot.common.context.TenantContext}。
 */
@Deprecated
public final class TenantContext {

	private TenantContext() {
	}

	/**
	 * @deprecated 使用
	 * {@link com.taipei.iot.common.context.TenantContext#getCurrentTenantId()}
	 */
	@Deprecated
	public static String getCurrentTenantId() {
		return com.taipei.iot.common.context.TenantContext.getCurrentTenantId();
	}

	/**
	 * @deprecated 使用
	 * {@link com.taipei.iot.common.context.TenantContext#setCurrentTenantId(String)}
	 */
	@Deprecated
	public static void setCurrentTenantId(String tenantId) {
		com.taipei.iot.common.context.TenantContext.setCurrentTenantId(tenantId);
	}

	/**
	 * @deprecated 使用 {@link com.taipei.iot.common.context.TenantContext#clear()}
	 */
	@Deprecated
	public static void clear() {
		com.taipei.iot.common.context.TenantContext.clear();
	}

	/**
	 * @deprecated 使用
	 * {@link com.taipei.iot.common.context.TenantContext#setSystemContext()}
	 */
	@Deprecated
	public static void setSystemContext() {
		com.taipei.iot.common.context.TenantContext.setSystemContext();
	}

	/**
	 * @deprecated 使用
	 * {@link com.taipei.iot.common.context.TenantContext#isSystemContext()}
	 */
	@Deprecated
	public static boolean isSystemContext() {
		return com.taipei.iot.common.context.TenantContext.isSystemContext();
	}

	/**
	 * @deprecated 使用
	 * {@link com.taipei.iot.common.context.TenantContext#isTrustedSystemContext()}
	 */
	@Deprecated
	public static boolean isTrustedSystemContext() {
		return com.taipei.iot.common.context.TenantContext.isTrustedSystemContext();
	}

	/**
	 * @deprecated 使用
	 * {@link com.taipei.iot.common.context.TenantContext#setImpersonator(String)}
	 */
	@Deprecated
	public static void setImpersonator(String userId) {
		com.taipei.iot.common.context.TenantContext.setImpersonator(userId);
	}

	/**
	 * @deprecated 使用
	 * {@link com.taipei.iot.common.context.TenantContext#getImpersonator()}
	 */
	@Deprecated
	public static String getImpersonator() {
		return com.taipei.iot.common.context.TenantContext.getImpersonator();
	}

	/**
	 * @deprecated 使用
	 * {@link com.taipei.iot.common.context.TenantContext#isImpersonating()}
	 */
	@Deprecated
	public static boolean isImpersonating() {
		return com.taipei.iot.common.context.TenantContext.isImpersonating();
	}

	/**
	 * @deprecated 使用
	 * {@link com.taipei.iot.common.context.TenantContext#runInSystemContext(Runnable)}
	 */
	@Deprecated
	public static void runInSystemContext(Runnable action) {
		com.taipei.iot.common.context.TenantContext.runInSystemContext(action);
	}

	/**
	 * @deprecated 使用
	 * {@link com.taipei.iot.common.context.TenantContext#runInSystemContext(java.util.function.Supplier)}
	 */
	@Deprecated
	public static <T> T runInSystemContext(java.util.function.Supplier<T> action) {
		return com.taipei.iot.common.context.TenantContext.runInSystemContext(action);
	}

}
