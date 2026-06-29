package com.taipei.iot.tenant;

/**
 * 標記介面：實作此介面的 Repository 操作的是具有 {@code @Filter(name="tenantFilter")} 的實體。
 * <p>
 * {@link TenantFilterAspect} 只對實作此介面的 Repository 強制要求 {@link TenantContext}：
 * <ul>
 * <li>若 {@link TenantContext#isSystemContext()} 為 true → 跳過 filter（允許跨租戶操作）</li>
 * <li>若 {@link TenantContext#getCurrentTenantId()} 非 null → 啟用 tenantFilter</li>
 * <li>否則 → 拋出 {@link IllegalStateException}（fail-closed，防止資料外洩）</li>
 * </ul>
 * <p>
 * 全域實體（如 {@code UserEntity}、{@code TenantEntity}）的 Repository 不應實作此介面。
 */
public interface TenantScopedRepository {

}
