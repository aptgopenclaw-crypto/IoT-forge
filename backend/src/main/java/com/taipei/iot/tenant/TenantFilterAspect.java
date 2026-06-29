package com.taipei.iot.tenant;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.hibernate.Session;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class TenantFilterAspect {

	@PersistenceContext
	private EntityManager entityManager;

	/**
	 * 在每一次 Repository 方法呼叫前啟用 Hibernate tenantFilter。
	 * <p>
	 * 只有實作 {@link TenantScopedRepository} 的 Repository（即操作具有
	 * {@code @Filter(name="tenantFilter")} 之實體的 Repository）才受此 Aspect 管控。 全域實體（如
	 * {@code UserEntity}、{@code TenantEntity}）的 Repository 不受影響。
	 */
	@Before("execution(* com.taipei.iot..*.repository..*Repository.*(..))")
	public void enableTenantFilter(JoinPoint jp) {
		// 只處理 TenantScopedRepository — 全域 Repository 直接放行
		if (!(jp.getThis() instanceof TenantScopedRepository)) {
			return;
		}

		if (TenantContext.isSystemContext()) {
			// [Phase B] 安全交叉檢查
			// - trusted（經由 runInSystemContext 進入）：程式碼層級的合法 cross-tenant 操作，直接放行
			// 例：登入流程查 tenant mappings、@RunInSystemTenantContext 排程任務
			// - 非 trusted（直接 setSystemContext）：僅限 SUPER_ADMIN 或無 Authentication（排程初始設定）
			if (!TenantContext.isTrustedSystemContext()) {
				Authentication auth = SecurityContextHolder.getContext().getAuthentication();
				if (auth != null && auth.isAuthenticated()) {
					boolean isSuperAdmin = auth.getAuthorities()
						.stream()
						.anyMatch(a -> "ROLE_SUPER_ADMIN".equals(a.getAuthority()));
					if (!isSuperAdmin) {
						throw new IllegalStateException("SystemContext detected for non-SUPER_ADMIN authentication. "
								+ "Possible security bypass attempt.");
					}
				}
			}
			return;
		}
		String tenantId = TenantContext.getCurrentTenantId();
		if (tenantId == null) {
			// Fail-closed：未設定 TenantContext 時拒絕查詢，防止資料外洩
			throw new IllegalStateException(
					"TenantContext is not set. All repository operations require a tenant context.");
		}
		Session session = entityManager.unwrap(Session.class);
		session.enableFilter("tenantFilter").setParameter("tenantId", tenantId);
	}

}
