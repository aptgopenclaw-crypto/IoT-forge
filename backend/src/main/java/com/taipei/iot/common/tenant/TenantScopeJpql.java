package com.taipei.iot.common.tenant;

/**
 * [common v2 F-11] 集中宣告「全域 + 租戶」（global-or-tenant）JPQL 條件片段， 取代各 repository 手寫
 * {@code (e.tenantId IS NULL OR e.tenantId = :tenantId)} 的零碎複製。
 *
 * <h3>背景</h3>
 * <p>
 * 本專案 tenant 隔離有兩種模式：
 * <ol>
 * <li><b>純租戶範圍</b>（{@code tenant_id = :tenantId}）：由
 * {@link com.taipei.iot.tenant.TenantScopedRepository} marker 介面 +
 * {@link com.taipei.iot.tenant.TenantFilterAspect} 透過 Hibernate {@code @Filter}
 * 自動套用，是主要機制。</li>
 * <li><b>全域 + 租戶混合</b>（{@code tenant_id IS NULL OR tenant_id = :tenantId}）： 例如
 * {@code role_permission} / {@code permission} 表中，{@code tenant_id IS NULL}
 * 代表「全租戶共用」、{@code tenant_id = X} 代表「該租戶專屬」。Hibernate {@code @Filter}
 * 無法精確表達「OR」語意，因此這類查詢必須走自訂 {@code @Query}。本類別即為此情境提供統一的 JPQL 片段。</li>
 * </ol>
 *
 * <h3>使用方式</h3> <pre>{@code
 * &#64;Query("SELECT rp FROM RolePermissionEntity rp WHERE rp.roleId = :roleId AND "
 *      + TenantScopeJpql.RP_GLOBAL_OR_TENANT)
 * List<RolePermissionEntity> findByRoleIdAndTenantScope(...);
 * }</pre>
 *
 * <p>
 * 由於常數為 {@code public static final String}（compile-time constant）， 可直接用於 {@code @Query}
 * 的字串連接，不影響 Spring Data 對 JPQL 的解析。
 *
 * <h3>命名規約</h3>
 * <p>
 * 常數以 {@code <alias>_GLOBAL_OR_TENANT} 命名，{@code <alias>} 對應 JPQL 中的 實體別名（例如 {@code rp} 為
 * {@code RolePermissionEntity rp}）。每加一個新 alias 即在此處新增一個常數，避免散落各處字面字串。
 *
 * <p>
 * 命名參數固定為 {@code :tenantId}，呼叫端需以 {@code @Param("tenantId")} 對應。
 *
 * @see com.taipei.iot.tenant.TenantScopedRepository
 * @see TenantScopeSpecifications
 */
public final class TenantScopeJpql {

	/** JPQL 別名 {@code rp}（{@link com.taipei.iot.rbac.entity.RolePermissionEntity}）用。 */
	public static final String RP_GLOBAL_OR_TENANT = "(rp.tenantId IS NULL OR rp.tenantId = :tenantId)";

	/** 命名參數名稱（呼叫端 {@code @Param("tenantId")} 對應之 key）。 */
	public static final String TENANT_ID_PARAM = "tenantId";

	private TenantScopeJpql() {
		// utility class
	}

	/**
	 * 程式化建構任意別名的 global-or-tenant 片段（非 {@code @Query} 用途， 例如
	 * {@code EntityManager.createQuery(...)} 動態組句）。
	 *
	 * <p>
	 * 若是 {@code @Query} 註解，請優先使用已宣告的 {@code XX_GLOBAL_OR_TENANT} 常數，以確保 compile-time
	 * 檢查與一致性。
	 * @param alias JPQL 實體別名，例如 {@code "rp"}、{@code "e"}
	 * @return {@code "(<alias>.tenantId IS NULL OR <alias>.tenantId = :tenantId)"}
	 * @throws IllegalArgumentException 若 alias 為 null / 空白
	 */
	public static String globalOrTenant(String alias) {
		if (alias == null || alias.isBlank()) {
			throw new IllegalArgumentException("alias must not be null or blank");
		}
		return "(" + alias + ".tenantId IS NULL OR " + alias + ".tenantId = :tenantId)";
	}

}
