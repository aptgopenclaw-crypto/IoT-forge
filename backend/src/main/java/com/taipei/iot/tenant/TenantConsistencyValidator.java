package com.taipei.iot.tenant;

import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.metamodel.EntityType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.repository.support.Repositories;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * [Tenant v2 T-8] 啟動期一致性驗證 — 結構性根因防護。
 *
 * <p>
 * 掃描 JPA Metamodel 中所有 {@link TenantAware} entity，並透過 {@link Repositories} 查出對應的 Spring
 * Data Repository bean； 若有 Repository 操作 TenantAware entity 卻 <b>沒有</b> implements
 * {@link TenantScopedRepository}，則於 {@link ApplicationReadyEvent} 觸發時 <b>fail-fast</b> 拋
 * {@link IllegalStateException} 中止啟動。
 * </p>
 *
 * <h3>為何需要這個 Validator</h3>
 * <p>
 * T-1 漏洞起因為 {@code AnnouncementAttachmentRepository} 忘記
 * {@code implements TenantScopedRepository} → Hibernate {@code @Filter("tenantFilter")}
 * 從未啟用 → 跨租戶資料外洩。{@code TenantScopedRepository} 是純標記介面，沒有編譯期/啟動期 檢查能避免「忘記實作」。本 Validator
 * 把該檢查制度化，CI 階段就能攔截。
 * </p>
 *
 * <h3>不在守備範圍內</h3>
 * <ul>
 * <li>有 {@code tenant_id} 欄位但刻意不掛 {@code @Filter}、不 implement {@code TenantAware} 的
 * entity（{@code UserSessionEntity} / {@code TenantAuthConfigEntity} /
 * {@code RolePermissionEntity}）— 由設計決策回歸測試 {@code TenantIsolationDesignDecisionTest}
 * 鎖死。</li>
 * <li>沒有 Spring Data Repository（純 JPQL/Native query DAO，如 {@code PasswordPolicyDao}）的
 * entity — 不在本 Validator 範圍，但 DAO 內部會自行帶 tenantId 參數。</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TenantConsistencyValidator {

	private final EntityManagerFactory entityManagerFactory;

	private final ListableBeanFactory beanFactory;

	@EventListener(ApplicationReadyEvent.class)
	public void validateOnStartup() {
		Set<Class<?>> tenantAwareEntities = resolveTenantAwareEntities(entityManagerFactory);
		Repositories repositories = new Repositories(beanFactory);

		List<String> violations = checkConsistency(tenantAwareEntities, repositories::getRepositoryFor);

		if (!violations.isEmpty()) {
			String msg = "[Tenant v2 T-8] Tenant isolation violations detected — these repositories "
					+ "operate on TenantAware entities but do not implement TenantScopedRepository:\n  - "
					+ String.join("\n  - ", violations)
					+ "\nFix: add `, TenantScopedRepository` to the listed repository interfaces.";
			log.error(msg);
			throw new IllegalStateException(msg);
		}
		log.info("[TenantConsistencyValidator] OK — {} TenantAware entities verified; all repositories "
				+ "implement TenantScopedRepository.", tenantAwareEntities.size());
	}

	/**
	 * 解析 JPA Metamodel，取出所有 {@link TenantAware} entity class。 Visible for testing.
	 */
	static Set<Class<?>> resolveTenantAwareEntities(EntityManagerFactory emf) {
		Set<Class<?>> result = new HashSet<>();
		for (EntityType<?> et : emf.getMetamodel().getEntities()) {
			Class<?> javaType = et.getJavaType();
			if (javaType != null && TenantAware.class.isAssignableFrom(javaType)) {
				result.add(javaType);
			}
		}
		return result;
	}

	/**
	 * 純函式檢查 — 給定一組 TenantAware entity class 與「entity → Repository bean」查詢函式，
	 * 回傳「Repository 不是 TenantScopedRepository」的違規清單。 Visible for testing — 抽出來方便不啟動
	 * Spring context 直接驗證邏輯。
	 */
	static List<String> checkConsistency(Set<Class<?>> tenantAwareEntities,
			Function<Class<?>, Optional<Object>> repoLookup) {
		List<String> violations = new ArrayList<>();
		for (Class<?> entityClass : tenantAwareEntities) {
			Optional<Object> repoOpt = repoLookup.apply(entityClass);
			if (repoOpt.isEmpty()) {
				// 無 Spring Data Repository（如純 DAO）→ 不在本檢查範圍
				continue;
			}
			Object repo = repoOpt.get();
			if (!(repo instanceof TenantScopedRepository)) {
				violations.add(repo.getClass().getName() + " (entity=" + entityClass.getSimpleName() + ")");
			}
		}
		return violations;
	}

}
