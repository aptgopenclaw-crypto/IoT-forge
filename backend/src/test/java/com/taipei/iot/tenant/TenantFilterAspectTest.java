package com.taipei.iot.tenant;

import jakarta.persistence.EntityManager;
import org.aspectj.lang.JoinPoint;
import org.hibernate.Session;
import org.hibernate.Filter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * [Phase B] 驗證 TenantFilterAspect 對 systemContext 的 SecurityContext 交叉檢查。
 */
class TenantFilterAspectTest {

	private TenantFilterAspect aspect;

	private JoinPoint jp;

	@BeforeEach
	void setup() {
		aspect = new TenantFilterAspect();
		EntityManager em = mock(EntityManager.class);
		Session session = mock(Session.class);
		Filter filter = mock(Filter.class);
		when(em.unwrap(Session.class)).thenReturn(session);
		when(session.enableFilter("tenantFilter")).thenReturn(filter);
		when(filter.setParameter("tenantId", "T1")).thenReturn(filter);
		ReflectionTestUtils.setField(aspect, "entityManager", em);

		jp = mock(JoinPoint.class);
		TenantScopedRepository fakeRepo = new TenantScopedRepository() {
		};
		when(jp.getThis()).thenReturn(fakeRepo);
	}

	@AfterEach
	void cleanup() {
		TenantContext.clear();
		SecurityContextHolder.clearContext();
	}

	@Test
	void systemContext_withSuperAdminAuth_shouldPass() {
		Authentication auth = new UsernamePasswordAuthenticationToken("super-001", null,
				List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN")));
		SecurityContextHolder.getContext().setAuthentication(auth);
		TenantContext.setSystemContext();

		assertDoesNotThrow(() -> aspect.enableTenantFilter(jp));
	}

	@Test
	void systemContext_withNonSuperAdminAuth_shouldThrow() {
		// 直接 setSystemContext()（非 trusted）+ 非 SUPER_ADMIN auth → 應拋錯
		Authentication auth = new UsernamePasswordAuthenticationToken("user-001", null,
				List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
		SecurityContextHolder.getContext().setAuthentication(auth);
		TenantContext.setSystemContext();

		assertThrows(IllegalStateException.class, () -> aspect.enableTenantFilter(jp));
	}

	@Test
	void trustedSystemContext_withNonSuperAdminAuth_shouldPass() {
		// 經由 runInSystemContext 進入 → 即使非 SUPER_ADMIN 也應放行
		// (登入流程：先認證使用者 → 再 runInSystemContext 查 tenant mappings)
		Authentication auth = new UsernamePasswordAuthenticationToken("user-001", null,
				List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
		SecurityContextHolder.getContext().setAuthentication(auth);

		TenantContext.runInSystemContext(() -> {
			assertDoesNotThrow(() -> aspect.enableTenantFilter(jp));
			return null;
		});
	}

	@Test
	void systemContext_withNoAuth_shouldPass_forSchedulerPath() {
		// SecurityContextHolder is cleared in @AfterEach → no auth
		TenantContext.setSystemContext();

		assertDoesNotThrow(() -> aspect.enableTenantFilter(jp));
	}

	@Test
	void tenantContext_withValidTenant_shouldEnableFilter() {
		TenantContext.setCurrentTenantId("T1");
		assertDoesNotThrow(() -> aspect.enableTenantFilter(jp));
	}

	@Test
	void noTenantContext_shouldThrowFailClosed() {
		assertThrows(IllegalStateException.class, () -> aspect.enableTenantFilter(jp));
	}

}
