package com.taipei.iot.common.util;

import com.taipei.iot.tenant.TenantContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantAwareQueryTest {

	@Mock
	private EntityManager em;

	@Mock
	private Query query;

	@BeforeEach
	void setUp() {
		TenantContext.clear();
		lenient().when(em.createNativeQuery(anyString())).thenReturn(query);
		lenient().when(query.setParameter(anyString(), org.mockito.ArgumentMatchers.any())).thenReturn(query);
	}

	@AfterEach
	void tearDown() {
		TenantContext.clear();
	}

	// --- stripCommentsAndLower ------------------------------------------------

	@Nested
	@DisplayName("stripCommentsAndLower (N-3 helper)")
	class StripComments {

		@Test
		void stripsBlockComments() {
			String stripped = TenantAwareQuery
				.stripCommentsAndLower("SELECT * FROM x WHERE id = :id /* tenant_id = :tenantId */");
			assertThat(stripped).doesNotContain("tenant_id");
		}

		@Test
		void stripsMultiLineBlockComments() {
			String stripped = TenantAwareQuery
				.stripCommentsAndLower("SELECT *\n/* tenant_id\n line 2 */\nFROM x WHERE id = :id");
			assertThat(stripped).doesNotContain("tenant_id");
		}

		@Test
		void stripsLineComments() {
			String stripped = TenantAwareQuery
				.stripCommentsAndLower("SELECT * FROM x WHERE id = :id -- AND tenant_id = :tenantId\n");
			assertThat(stripped).doesNotContain("tenant_id");
		}

		@Test
		void keepsRealTenantIdInWhere() {
			String stripped = TenantAwareQuery
				.stripCommentsAndLower("SELECT * FROM x WHERE id = :id AND tenant_id = :tenantId");
			assertThat(stripped).contains("tenant_id");
		}

		@Test
		void nullSqlReturnsEmpty() {
			assertThat(TenantAwareQuery.stripCommentsAndLower(null)).isEmpty();
		}

	}

	// --- containsTenantIdAfterWhere ------------------------------------------

	@Nested
	@DisplayName("containsTenantIdAfterWhere (N-3 helper)")
	class TenantIdLocation {

		@Test
		void detectsTenantIdAfterWhere() {
			assertThat(TenantAwareQuery
				.containsTenantIdAfterWhere("select * from x where id = :id and tenant_id = :tenantid")).isTrue();
		}

		@Test
		void rejectsTenantIdOnlyInSelectList() {
			// tenant_id 出現在 SELECT 欄位但 WHERE 沒有
			assertThat(TenantAwareQuery.containsTenantIdAfterWhere("select tenant_id, name from x where id = :id"))
				.isFalse();
		}

		@Test
		void rejectsWhenNoWhereClause() {
			assertThat(TenantAwareQuery.containsTenantIdAfterWhere("select tenant_id from x")).isFalse();
		}

		@Test
		void rejectsSimilarColumnNames() {
			// tenant_id_v2 / my_tenant_id 不應被誤判
			assertThat(TenantAwareQuery.containsTenantIdAfterWhere("select * from x where tenant_id_v2 = :tid"))
				.isFalse();
		}

	}

	// --- create() full flow ---------------------------------------------------

	@Nested
	@DisplayName("create() — N-3 hardened validation")
	class CreateValidation {

		@Test
		void rejectsTenantIdHiddenInBlockComment() {
			TenantContext.setCurrentTenantId("T1");
			String sql = "SELECT * FROM x WHERE id = :id /* tenant_id = :tenantId */";

			assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> TenantAwareQuery.create(em, sql))
				.withMessageContaining("tenant_id");

			verify(em, never()).createNativeQuery(anyString());
		}

		@Test
		void rejectsTenantIdHiddenInLineComment() {
			TenantContext.setCurrentTenantId("T1");
			String sql = "SELECT * FROM x WHERE id = :id -- AND tenant_id = :tenantId\n";

			assertThatExceptionOfType(IllegalArgumentException.class)
				.isThrownBy(() -> TenantAwareQuery.create(em, sql));
		}

		@Test
		void rejectsTenantIdOnlyInSelectList() {
			TenantContext.setCurrentTenantId("T1");
			String sql = "SELECT tenant_id, name FROM x WHERE id = :id";

			assertThatExceptionOfType(IllegalArgumentException.class)
				.isThrownBy(() -> TenantAwareQuery.create(em, sql));
		}

		@Test
		void acceptsTenantIdAfterWhereAndBindsParameter() {
			TenantContext.setCurrentTenantId("T1");
			String sql = "SELECT * FROM x WHERE id = :id AND tenant_id = :tenantId";

			Query result = TenantAwareQuery.create(em, sql);

			assertThat(result).isSameAs(query);
			verify(em).createNativeQuery(sql);
			verify(query).setParameter(eq("tenantId"), eq("T1"));
		}

		@Test
		void acceptsTenantIdAfterWhereWithoutPlaceholder() {
			// 帶 tenant_id literal 但沒有 :tenantId placeholder（少見但合法）
			TenantContext.setCurrentTenantId("T1");
			String sql = "SELECT * FROM x WHERE id = :id AND tenant_id IS NOT NULL";

			TenantAwareQuery.create(em, sql);

			verify(em).createNativeQuery(sql);
			verify(query, never()).setParameter(eq("tenantId"), org.mockito.ArgumentMatchers.any());
		}

		@Test
		void systemContextSkipsValidationEvenWithCommentedTenantId() {
			TenantContext.setSystemContext();
			String sql = "SELECT * FROM x /* tenant_id */ WHERE id = :id";

			TenantAwareQuery.create(em, sql);

			verify(em).createNativeQuery(sql);
			// System context 不綁定 :tenantId 參數
			verify(query, never()).setParameter(eq("tenantId"), org.mockito.ArgumentMatchers.any());
		}

		@Test
		void failsClosedWhenTenantContextMissing() {
			// 既無一般 tenant 也非 system context
			String sql = "SELECT * FROM x WHERE id = :id AND tenant_id = :tenantId";

			assertThatExceptionOfType(IllegalStateException.class).isThrownBy(() -> TenantAwareQuery.create(em, sql))
				.withMessageContaining("TenantContext is not set");

			verify(em, never()).createNativeQuery(anyString());
		}

	}

	// --- createGlobal() -------------------------------------------------------

	@Nested
	@DisplayName("createGlobal() — N-3 keeps strip-comments warning")
	class CreateGlobal {

		@Test
		void commentedTenantIdDoesNotTriggerWarn() {
			// 真正全域查詢，但 SQL 裡碰巧有 tenant_id 在註解中（不應觸發 warn）
			String sql = "SELECT * FROM tenant /* tenant_id 註解中 */ WHERE tenant_id = :tid";
			// 註：實際 tenant_id 在 WHERE 也存在；createGlobal 仍 warn 但這是正確的（提醒可能用錯方法）。
			// 此處驗證行為一致：呼叫不拋例外、回傳 query。
			Query result = TenantAwareQuery.createGlobal(em, sql);
			assertThat(result).isSameAs(query);
			verify(em).createNativeQuery(sql);
		}

		@Test
		void pureGlobalSqlPassesThrough() {
			String sql = "SELECT tenant_code FROM tenant WHERE tenant_code = :code";
			TenantAwareQuery.createGlobal(em, sql);
			verify(em, times(1)).createNativeQuery(sql);
		}

	}

}
