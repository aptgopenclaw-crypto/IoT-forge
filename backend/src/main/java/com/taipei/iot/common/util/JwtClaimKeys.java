package com.taipei.iot.common.util;

/**
 * JWT Claim 欄位名稱常數。
 * <p>
 * 集中管理 JWT filter 與 {@link SecurityContextUtils} 之間共用的 claim key 字串， 避免散落在各處的魔術字串造成重構困難。
 */
public final class JwtClaimKeys {

	private JwtClaimKeys() {
	}

	/**
	 * JWT claim key：使用者唯一識別碼（對應 Authentication principal / JWT subject）。 值使用縮寫
	 * {@code "uid"} 以降低 token payload 大小，對應 JWT filter 的寫入方式。
	 */
	public static final String USER_ID = "uid";

	public static final String TENANT_ID = "tenantId";

	public static final String DEPT_ID = "deptId";

	public static final String DATA_SCOPE = "dataScope";

	/**
	 * [Platform/Tenant Separation ADR-007] Token scope claim: one of {@code "PLATFORM"},
	 * {@code "TENANT"}, {@code "IMPERSONATION"}. Used by ScopeEnforcementFilter (Phase
	 * 1.1.2) to validate that the request path prefix matches the token's authority
	 * scope. Absent on legacy tokens issued before Phase 1 — treated as {@code TENANT}
	 * for backward compat.
	 */
	public static final String SCOPE = "scope";

	/**
	 * [Platform/Tenant Separation ADR-007] Impersonation context object, present only
	 * when {@link #SCOPE} = {@code "IMPERSONATION"}. Populated by the Impersonation API
	 * (Phase 1.1.6). Shape: {@code originalUserId}, {@code sessionId}, {@code expiresAt}
	 * (epoch seconds).
	 */
	public static final String IMPERSONATION = "impersonation";

}
