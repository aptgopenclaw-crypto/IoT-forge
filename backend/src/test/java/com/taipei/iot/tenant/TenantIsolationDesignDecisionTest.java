package com.taipei.iot.tenant;

import com.taipei.iot.auth.entity.UserSessionEntity;
import com.taipei.iot.auth.provider.config.entity.TenantAuthConfigEntity;
import com.taipei.iot.auth.provider.config.repository.TenantAuthConfigRepository;
import com.taipei.iot.auth.repository.UserSessionRepository;
import com.taipei.iot.rbac.entity.RolePermissionEntity;
import com.taipei.iot.rbac.repository.RolePermissionRepository;
import org.hibernate.annotations.Filter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * [Tenant v2 T-2] 釘住「全域實體 + Service 層手動 tenant 過濾」設計決策的回歸測試。
 *
 * <p>
 * {@link UserSessionEntity}、{@link TenantAuthConfigEntity}、{@link RolePermissionEntity}
 * 三個實體在資料表層擁有 {@code tenant_id} 欄位，但 <b>刻意</b> 不走 {@code TenantAware} / {@code @Filter} /
 * {@code TenantScopedRepository} 三件套，理由詳見各 entity class 的 JavaDoc。
 * </p>
 *
 * <p>
 * 本測試的目的是：若未來開發者出於善意（例如看到 T-2 review）為這些 entity 補上 {@code @Filter} 或讓 Repository
 * implement {@code TenantScopedRepository}，這個測試會失敗， 並提示開發者先閱讀對應 entity 的 JavaDoc、確認所有
 * caller 路徑後再決定是否變更。
 * </p>
 *
 * <p>
 * 對應 v2 文件：{@code 01-docs/code-review/tenant/tenant-module-code-review-v2.md} #T-2。
 * </p>
 */
class TenantIsolationDesignDecisionTest {

	@Test
	@DisplayName("UserSessionEntity 應維持「全域實體」設計（PK = JWT jti，跨租戶 session 為合法情境）")
	void userSessionEntity_isIntentionallyGlobal() {
		assertFalse(TenantAware.class.isAssignableFrom(UserSessionEntity.class),
				"UserSessionEntity 不應 implement TenantAware；若需變更，請先閱讀其 class JavaDoc 並確認 refresh-token rotation 流程。");
		assertNull(UserSessionEntity.class.getAnnotation(Filter.class),
				"UserSessionEntity 不應標註 @Filter；session 查詢時機常先於 TenantContext 設定，會被 fail-closed 誤殺。");
		assertFalse(TenantScopedRepository.class.isAssignableFrom(UserSessionRepository.class),
				"UserSessionRepository 不應 implement TenantScopedRepository；service 層以 sessionId+userId 雙重定位作為縱深防禦。");
	}

	@Test
	@DisplayName("TenantAuthConfigEntity 應維持「全域實體」設計（登入流程在 TenantContext 設定前查詢）")
	void tenantAuthConfigEntity_isIntentionallyGlobal() {
		assertFalse(TenantAware.class.isAssignableFrom(TenantAuthConfigEntity.class),
				"TenantAuthConfigEntity 不應 implement TenantAware；登入流程在 JwtAuthFilter 之前呼叫 findByTenantId，加 @Filter 會中斷登入。");
		assertNull(TenantAuthConfigEntity.class.getAnnotation(Filter.class),
				"TenantAuthConfigEntity 不應標註 @Filter；tenant_id 為 UNIQUE 主索引，findByTenantId 已等同 tenant filter。");
		assertFalse(TenantScopedRepository.class.isAssignableFrom(TenantAuthConfigRepository.class),
				"TenantAuthConfigRepository 不應 implement TenantScopedRepository；變更前需重構 AuthenticationDispatcher 在登入前設定 TenantContext。");
	}

	@Test
	@DisplayName("RolePermissionEntity 應維持「全域實體」設計（tenant_id IS NULL 為合法的全域權限）")
	void rolePermissionEntity_isIntentionallyGlobal() {
		assertFalse(TenantAware.class.isAssignableFrom(RolePermissionEntity.class),
				"RolePermissionEntity 不應 implement TenantAware；tenant_id IS NULL 代表全域權限，@Filter 會排除這些 row。");
		assertNull(RolePermissionEntity.class.getAnnotation(Filter.class),
				"RolePermissionEntity 不應標註單純的 @Filter；service 層使用 (tenant_id IS NULL OR tenant_id = :tenantId) 表達正確語意。");
		assertFalse(TenantScopedRepository.class.isAssignableFrom(RolePermissionRepository.class),
				"RolePermissionRepository 不應 implement TenantScopedRepository；變更前需重新定義「全域權限」的儲存方式。");
	}

}
