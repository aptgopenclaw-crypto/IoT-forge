package com.taipei.iot.common.tenant;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.domain.Specification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * [common v2 F-11] {@link TenantScopeSpecifications} 單元測試。 使用 Mockito 驗證 Specification 對
 * CriteriaBuilder 的呼叫順序與條件組合。
 */
class TenantScopeSpecificationsTest {

	@SuppressWarnings("unchecked")
	private final Root<Object> root = mock(Root.class);

	private final CriteriaQuery<?> query = mock(CriteriaQuery.class);

	private final CriteriaBuilder cb = mock(CriteriaBuilder.class);

	@SuppressWarnings("unchecked")
	private final Path<Object> tenantIdPath = mock(Path.class);

	private final Predicate isNullPredicate = mock(Predicate.class);

	private final Predicate equalPredicate = mock(Predicate.class);

	private final Predicate orPredicate = mock(Predicate.class);

	@BeforeEach
	void setUp() {
		when(root.get("tenantId")).thenReturn(tenantIdPath);
		when(cb.isNull(tenantIdPath)).thenReturn(isNullPredicate);
		when(cb.equal(eq(tenantIdPath), any(Object.class))).thenReturn(equalPredicate);
		when(cb.or(any(Predicate.class), any(Predicate.class))).thenReturn(orPredicate);
	}

	@Nested
	@DisplayName("globalOrTenant(tenantId)")
	class GlobalOrTenant {

		@Test
		@DisplayName("應組合 IS NULL OR = tenantId 兩個 predicate")
		void shouldBuildOrPredicate() {
			Specification<Object> spec = TenantScopeSpecifications.globalOrTenant("T1");

			Predicate result = spec.toPredicate(root, query, cb);

			assertThat(result).isSameAs(orPredicate);
			verify(cb).isNull(tenantIdPath);
			verify(cb).equal(tenantIdPath, "T1");
			verify(cb).or(isNullPredicate, equalPredicate);
		}

		@Test
		@DisplayName("tenantId 為 null 應拋 IllegalArgumentException")
		void shouldRejectNull() {
			assertThatThrownBy(() -> TenantScopeSpecifications.globalOrTenant(null))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("tenantId");
		}

	}

	@Nested
	@DisplayName("tenantOnly(tenantId)")
	class TenantOnly {

		@Test
		@DisplayName("應只組合 = tenantId 一個 predicate")
		void shouldBuildEqualPredicate() {
			Specification<Object> spec = TenantScopeSpecifications.tenantOnly("T2");

			Predicate result = spec.toPredicate(root, query, cb);

			assertThat(result).isSameAs(equalPredicate);
			verify(cb).equal(tenantIdPath, "T2");
			verify(cb, times(0)).isNull(any());
			verify(cb, times(0)).or(any(), any());
		}

		@Test
		@DisplayName("tenantId 為 null 應拋 IllegalArgumentException")
		void shouldRejectNull() {
			assertThatThrownBy(() -> TenantScopeSpecifications.tenantOnly(null))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("tenantId");
		}

	}

	@Nested
	@DisplayName("globalOnly()")
	class GlobalOnly {

		@Test
		@DisplayName("應只組合 IS NULL 一個 predicate")
		void shouldBuildIsNullPredicate() {
			Specification<Object> spec = TenantScopeSpecifications.globalOnly();

			Predicate result = spec.toPredicate(root, query, cb);

			assertThat(result).isSameAs(isNullPredicate);
			verify(cb).isNull(tenantIdPath);
			verify(cb, times(0)).equal(any(), any(Object.class));
			verify(cb, times(0)).or(any(), any());
		}

	}

}
