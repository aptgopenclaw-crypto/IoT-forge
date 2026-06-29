package com.taipei.iot.common.tenant;

import org.springframework.data.jpa.domain.Specification;

/**
 * [common v2 F-11] Spring Data {@link Specification} 工廠，提供 tenant 隔離常用條件， 供使用
 * {@code JpaSpecificationExecutor} 的 repository 動態組合查詢時使用。
 *
 * <p>
 * 與 {@link TenantScopeJpql} 互補：
 * <ul>
 * <li>{@code TenantScopeJpql} — 靜態 {@code @Query} 字串拼接</li>
 * <li>{@code TenantScopeSpecifications} — 動態 Criteria API 組合（複雜查詢、條件可選時用）</li>
 * </ul>
 *
 * <p>
 * 實體必須宣告名為 {@code tenantId} 的欄位（與既有 {@code @Filter(name="tenantFilter")} 的對應 entity
 * 慣例一致）。
 */
public final class TenantScopeSpecifications {

	private static final String TENANT_ID_FIELD = "tenantId";

	private TenantScopeSpecifications() {
		// utility class
	}

	/**
	 * 「全域 + 租戶」混合範圍：{@code tenantId IS NULL OR tenantId = :tenantId}。
	 * @param tenantId 當前租戶 id；不可為 null
	 */
	public static <T> Specification<T> globalOrTenant(String tenantId) {
		if (tenantId == null) {
			throw new IllegalArgumentException("tenantId must not be null");
		}
		return (root, query, cb) -> cb.or(cb.isNull(root.get(TENANT_ID_FIELD)),
				cb.equal(root.get(TENANT_ID_FIELD), tenantId));
	}

	/**
	 * 純租戶範圍：{@code tenantId = :tenantId}。
	 * <p>
	 * 注意：若該 entity 已被 {@code TenantFilterAspect} 自動套用 Hibernate {@code @Filter}， 重複套用本
	 * Specification 並無錯誤但屬冗餘。
	 */
	public static <T> Specification<T> tenantOnly(String tenantId) {
		if (tenantId == null) {
			throw new IllegalArgumentException("tenantId must not be null");
		}
		return (root, query, cb) -> cb.equal(root.get(TENANT_ID_FIELD), tenantId);
	}

	/**
	 * 僅全域資料：{@code tenantId IS NULL}（例如管理「全租戶共用」項目時用）。
	 */
	public static <T> Specification<T> globalOnly() {
		return (root, query, cb) -> cb.isNull(root.get(TENANT_ID_FIELD));
	}

}
