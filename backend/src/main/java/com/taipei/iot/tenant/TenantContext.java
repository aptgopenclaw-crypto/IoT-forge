package com.taipei.iot.tenant;

import java.util.function.Supplier;

/**
 * ThreadLocal 容器，存放當前執行緒的租戶 ID。
 * <p>
 * 租戶功能貫穿 Spring Filter / MVC Interceptor 層級， 最終帶動到 JPA Hibernate tenantFilter。
 */
public final class TenantContext {

	private TenantContext() {
	}

	private static final ThreadLocal<String> CURRENT_TENANT = new ThreadLocal<>();

	private static final ThreadLocal<String> IMPERSONATOR = new ThreadLocal<>();

	private static final ThreadLocal<Boolean> TRUSTED_SYSTEM_CONTEXT = new ThreadLocal<>();

	private static final String SYSTEM_TENANT_MARKER = "SYSTEM";

	public static String getCurrentTenantId() {
		return CURRENT_TENANT.get();
	}

	public static void setCurrentTenantId(String tenantId) {
		CURRENT_TENANT.set(tenantId);
	}

	public static void clear() {
		CURRENT_TENANT.remove();
		IMPERSONATOR.remove();
		TRUSTED_SYSTEM_CONTEXT.remove();
	}

	/** 排程任務用：跳過 tenant filter */
	public static void setSystemContext() {
		CURRENT_TENANT.set(SYSTEM_TENANT_MARKER);
	}

	public static boolean isSystemContext() {
		return SYSTEM_TENANT_MARKER.equals(CURRENT_TENANT.get());
	}

	/**
	 * 判斷目前的 SYSTEM context 是否由 {@link #runInSystemContext} 建立（程式碼層級的合法 cross-tenant
	 * 操作，例如登入流程查 tenant mappings、排程清理任務等）。
	 *
	 * <p>
	 * 與 {@link #setSystemContext()} 直接呼叫區分：直接呼叫僅用於
	 * {@code JwtAuthenticationFilter}（SUPER_ADMIN 無 tenantId 時）和排程外層設定， 會受到
	 * {@code TenantFilterAspect} 的 SecurityContext 交叉檢查。
	 */
	public static boolean isTrustedSystemContext() {
		return Boolean.TRUE.equals(TRUSTED_SYSTEM_CONTEXT.get());
	}

	/**
	 * [Phase B] 設定代操者 userId（SUPER_ADMIN 在 tenant context 下操作時呼叫）。 由
	 * {@code JwtAuthenticationFilter} 在判定 SUPER_ADMIN + 有 tenantId 時設定， 由
	 * {@code BaseLoggerAspect} / {@code UserAuditService} 讀取寫入 audit log。
	 */
	public static void setImpersonator(String userId) {
		IMPERSONATOR.set(userId);
	}

	public static String getImpersonator() {
		return IMPERSONATOR.get();
	}

	public static boolean isImpersonating() {
		return IMPERSONATOR.get() != null;
	}

	/**
	 * 在 SYSTEM context 中執行給定動作，執行完畢後自動恢復先前的 context。 適用於需要跨租戶操作的場景（如建立
	 * UserTenantMapping）。
	 */
	public static void runInSystemContext(Runnable action) {
		runInSystemContext(() -> {
			action.run();
			return null;
		});
	}

	/**
	 * [Tenant v2 T-13] {@link Supplier} 版本：在 SYSTEM context 中執行查詢並回傳結果， 執行完畢後自動恢復先前的
	 * tenant + impersonator context（無論成功或拋例外）。
	 *
	 * <p>
	 * SYSTEM context 期間 impersonator 會被暫時清除（system 操作不算代操； 例如租戶建立流程中以 SYSTEM context 寫
	 * mapping，那筆 mapping 不該被標為代操）。
	 */
	public static <T> T runInSystemContext(Supplier<T> action) {
		String previousTenant = CURRENT_TENANT.get();
		String previousImpersonator = IMPERSONATOR.get();
		Boolean previousTrusted = TRUSTED_SYSTEM_CONTEXT.get();
		try {
			setSystemContext();
			TRUSTED_SYSTEM_CONTEXT.set(true);
			IMPERSONATOR.remove();
			return action.get();
		}
		finally {
			if (previousTenant != null) {
				CURRENT_TENANT.set(previousTenant);
			}
			else {
				CURRENT_TENANT.remove();
			}
			if (previousImpersonator != null) {
				IMPERSONATOR.set(previousImpersonator);
			}
			else {
				IMPERSONATOR.remove();
			}
			if (previousTrusted != null) {
				TRUSTED_SYSTEM_CONTEXT.set(previousTrusted);
			}
			else {
				TRUSTED_SYSTEM_CONTEXT.remove();
			}
		}
	}

}
