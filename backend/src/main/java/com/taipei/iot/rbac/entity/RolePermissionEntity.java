package com.taipei.iot.rbac.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 角色與權限的對應關係。
 *
 * <h3>[Tenant v2 T-2] 租戶隔離設計決策：採「全域實體 + Service 層 tenant-scope 查詢」策略</h3>
 * <p>
 * 本實體刻意 <b>不</b> 標註 {@code @Filter(name="tenantFilter")}、不 implement
 * {@code TenantAware}，其 Repository 也不 implement {@code TenantScopedRepository}。 理由：
 * </p>
 * <ol>
 * <li><b>{@code tenant_id IS NULL} 為合法的「全域權限」設計</b>：所有 tenant 共用的 系統權限（如平台層面的內建
 * role-permission 對應）以 {@code tenant_id = NULL} 表示。若加上單純的
 * {@code @Filter(condition="tenant_id = :tenantId")}，將 <i>排除所有全域權限</i>，整個 RBAC 將失效。</li>
 * <li><b>Service 層已使用 tenant-scope 查詢</b>：{@code findByRoleIdAndTenantScope} /
 * {@code findByRoleIdInAndTenantScope} 顯式以
 * {@code (tenant_id IS NULL OR tenant_id = :tenantId)} 過濾，正確處理「全域 + 租戶 專屬」的聯集語意，這是
 * {@code @Filter} 無法表達的。</li>
 * </ol>
 * <p>
 * <b>對應的縱深防禦</b>：
 * </p>
 * <ul>
 * <li>呼叫 {@code findByRoleId(roleId)} / {@code deleteByRoleId(roleId)} 等無 tenant 範圍
 * 的方法時，<b>caller 必須先驗證 {@code roleId} 屬於當前 tenant</b> （透過 {@code roleRepository} 並經
 * tenant filter）。</li>
 * <li>新增無 tenant 範圍的 query method 前，必須在 PR review 中驗證所有 caller 均已 完成「以 tenant-scoped
 * roleId 為前置條件」。</li>
 * </ul>
 *
 * <p>
 * 變更此設計決策前，需重新定義「全域權限」的儲存方式（例如改用獨立 table 或為每個 tenant 複製一份），並評估 migration 成本與儲存量影響。
 * </p>
 */
@Entity
@Table(name = "role_permissions")
@IdClass(RolePermissionId.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RolePermissionEntity {

	@Id
	@Column(name = "role_id", length = 50, nullable = false)
	private String roleId;

	@Id
	@Column(name = "permission_id", length = 50, nullable = false)
	private String permissionId;

	@Column(name = "tenant_id", length = 50)
	private String tenantId;

}
