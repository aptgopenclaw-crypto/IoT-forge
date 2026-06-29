package com.taipei.iot.auth.policy;

import com.taipei.iot.common.annotation.AllowDirectNativeQuery;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Dedicated DAO for password-policy rows stored inside the existing
 * {@code system_settings} table.
 *
 * <p>
 * Deliberately does <strong>not</strong> extend
 * {@link com.taipei.iot.tenant.TenantScopedRepository}, and uses native SQL, so the
 * {@code TenantFilterAspect} and Hibernate's {@code @Filter(name="tenantFilter")} are
 * both bypassed. Every method takes an explicit {@code tenantId} (including the reserved
 * platform sentinel {@link PasswordPolicyResolver#PLATFORM_SENTINEL}).
 */
@Repository
@RequiredArgsConstructor
@AllowDirectNativeQuery(reason = "Cross-tenant + platform-sentinel access by design; tenant_id is bound "
		+ "explicitly in every query and verified by PasswordPolicyResolver tests.")
public class PasswordPolicyDao {

	@PersistenceContext
	private EntityManager em;

	/** Load all password.* rows for a given tenant. Returns key→value map. */
	@SuppressWarnings("unchecked")
	@Transactional(readOnly = true)
	public Map<String, String> findAllForTenant(String tenantId) {
		List<Object[]> rows = em
			.createNativeQuery("SELECT setting_key, setting_value FROM system_settings "
					+ "WHERE tenant_id = :tid AND setting_key LIKE 'password.%'")
			.setParameter("tid", tenantId)
			.getResultList();
		Map<String, String> out = new HashMap<>();
		for (Object[] r : rows) {
			out.put((String) r[0], (String) r[1]);
		}
		return out;
	}

	/** Insert-or-update a single setting row for a tenant (or platform sentinel). */
	@Transactional
	public void upsert(String tenantId, String key, String value, String description) {
		em.createNativeQuery(
				"INSERT INTO system_settings (tenant_id, setting_key, setting_value, description, created_at, updated_at) "
						+ "VALUES (:tid, :k, :v, :d, NOW(), NOW()) " + "ON CONFLICT (tenant_id, setting_key) DO UPDATE "
						+ "SET setting_value = EXCLUDED.setting_value, updated_at = NOW()")
			.setParameter("tid", tenantId)
			.setParameter("k", key)
			.setParameter("v", value)
			.setParameter("d", description == null ? "" : description)
			.executeUpdate();
	}

	/** Delete a single tenant override (no-op for platform sentinel deletes). */
	@Transactional
	public int delete(String tenantId, String key) {
		return em.createNativeQuery("DELETE FROM system_settings WHERE tenant_id = :tid AND setting_key = :k")
			.setParameter("tid", tenantId)
			.setParameter("k", key)
			.executeUpdate();
	}

}
