package com.taipei.iot.tenant;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 純單元測試 {@link TenantConsistencyValidator#checkConsistency(Set, Function)} 與
 * {@link TenantConsistencyValidator#resolveTenantAwareEntities} 的純邏輯部分。
 *
 * <p>
 * 整合面驗證（即「啟動時若有違規應 fail 啟動」）由 Spring Boot 測試的實際 boot 過程 隱式覆蓋——任何違規 entity 都會讓
 * {@code @SpringBootTest} 啟動失敗。
 * </p>
 */
class TenantConsistencyValidatorTest {

	// ── 測試替身：模擬「合規」與「違規」的 Repository bean ─────────────────

	interface CompliantRepo extends JpaRepository<TestTenantAwareEntity, Long>, TenantScopedRepository {

	}

	interface NonCompliantRepo extends JpaRepository<TestTenantAwareEntity, Long> {

	}

	static class CompliantRepoImpl implements CompliantRepo {

		// 介面方法不被測試呼叫，留空
		@Override
		public java.util.List<TestTenantAwareEntity> findAll() {
			return List.of();
		}

		@Override
		public java.util.List<TestTenantAwareEntity> findAll(org.springframework.data.domain.Sort sort) {
			return List.of();
		}

		@Override
		public java.util.List<TestTenantAwareEntity> findAllById(Iterable<Long> longs) {
			return List.of();
		}

		@Override
		public <S extends TestTenantAwareEntity> java.util.List<S> saveAll(Iterable<S> entities) {
			return List.of();
		}

		@Override
		public void flush() {
		}

		@Override
		public <S extends TestTenantAwareEntity> S saveAndFlush(S entity) {
			return entity;
		}

		@Override
		public <S extends TestTenantAwareEntity> java.util.List<S> saveAllAndFlush(Iterable<S> entities) {
			return List.of();
		}

		@Override
		public void deleteAllInBatch(Iterable<TestTenantAwareEntity> entities) {
		}

		@Override
		public void deleteAllByIdInBatch(Iterable<Long> longs) {
		}

		@Override
		public void deleteAllInBatch() {
		}

		@Override
		public TestTenantAwareEntity getOne(Long aLong) {
			return null;
		}

		@Override
		public TestTenantAwareEntity getById(Long aLong) {
			return null;
		}

		@Override
		public TestTenantAwareEntity getReferenceById(Long aLong) {
			return null;
		}

		@Override
		public <S extends TestTenantAwareEntity> java.util.List<S> findAll(
				org.springframework.data.domain.Example<S> example) {
			return List.of();
		}

		@Override
		public <S extends TestTenantAwareEntity> java.util.List<S> findAll(
				org.springframework.data.domain.Example<S> example, org.springframework.data.domain.Sort sort) {
			return List.of();
		}

		@Override
		public org.springframework.data.domain.Page<TestTenantAwareEntity> findAll(
				org.springframework.data.domain.Pageable pageable) {
			return org.springframework.data.domain.Page.empty();
		}

		@Override
		public <S extends TestTenantAwareEntity> S save(S entity) {
			return entity;
		}

		@Override
		public Optional<TestTenantAwareEntity> findById(Long aLong) {
			return Optional.empty();
		}

		@Override
		public boolean existsById(Long aLong) {
			return false;
		}

		@Override
		public long count() {
			return 0;
		}

		@Override
		public void deleteById(Long aLong) {
		}

		@Override
		public void delete(TestTenantAwareEntity entity) {
		}

		@Override
		public void deleteAllById(Iterable<? extends Long> longs) {
		}

		@Override
		public void deleteAll(Iterable<? extends TestTenantAwareEntity> entities) {
		}

		@Override
		public void deleteAll() {
		}

		@Override
		public <S extends TestTenantAwareEntity> Optional<S> findOne(
				org.springframework.data.domain.Example<S> example) {
			return Optional.empty();
		}

		@Override
		public <S extends TestTenantAwareEntity> org.springframework.data.domain.Page<S> findAll(
				org.springframework.data.domain.Example<S> example, org.springframework.data.domain.Pageable pageable) {
			return org.springframework.data.domain.Page.empty();
		}

		@Override
		public <S extends TestTenantAwareEntity> long count(org.springframework.data.domain.Example<S> example) {
			return 0;
		}

		@Override
		public <S extends TestTenantAwareEntity> boolean exists(org.springframework.data.domain.Example<S> example) {
			return false;
		}

		@Override
		public <S extends TestTenantAwareEntity, R> R findBy(org.springframework.data.domain.Example<S> example,
				Function<org.springframework.data.repository.query.FluentQuery.FetchableFluentQuery<S>, R> queryFunction) {
			return null;
		}

	}

	static class NonCompliantRepoImpl implements NonCompliantRepo {

		// 純粹的非合規 repo — 不繼承 CompliantRepoImpl（否則會誤帶 TenantScopedRepository）
		@Override
		public java.util.List<TestTenantAwareEntity> findAll() {
			return List.of();
		}

		@Override
		public java.util.List<TestTenantAwareEntity> findAll(org.springframework.data.domain.Sort sort) {
			return List.of();
		}

		@Override
		public java.util.List<TestTenantAwareEntity> findAllById(Iterable<Long> longs) {
			return List.of();
		}

		@Override
		public <S extends TestTenantAwareEntity> java.util.List<S> saveAll(Iterable<S> entities) {
			return List.of();
		}

		@Override
		public void flush() {
		}

		@Override
		public <S extends TestTenantAwareEntity> S saveAndFlush(S entity) {
			return entity;
		}

		@Override
		public <S extends TestTenantAwareEntity> java.util.List<S> saveAllAndFlush(Iterable<S> entities) {
			return List.of();
		}

		@Override
		public void deleteAllInBatch(Iterable<TestTenantAwareEntity> entities) {
		}

		@Override
		public void deleteAllByIdInBatch(Iterable<Long> longs) {
		}

		@Override
		public void deleteAllInBatch() {
		}

		@Override
		public TestTenantAwareEntity getOne(Long aLong) {
			return null;
		}

		@Override
		public TestTenantAwareEntity getById(Long aLong) {
			return null;
		}

		@Override
		public TestTenantAwareEntity getReferenceById(Long aLong) {
			return null;
		}

		@Override
		public <S extends TestTenantAwareEntity> java.util.List<S> findAll(
				org.springframework.data.domain.Example<S> example) {
			return List.of();
		}

		@Override
		public <S extends TestTenantAwareEntity> java.util.List<S> findAll(
				org.springframework.data.domain.Example<S> example, org.springframework.data.domain.Sort sort) {
			return List.of();
		}

		@Override
		public org.springframework.data.domain.Page<TestTenantAwareEntity> findAll(
				org.springframework.data.domain.Pageable pageable) {
			return org.springframework.data.domain.Page.empty();
		}

		@Override
		public <S extends TestTenantAwareEntity> S save(S entity) {
			return entity;
		}

		@Override
		public Optional<TestTenantAwareEntity> findById(Long aLong) {
			return Optional.empty();
		}

		@Override
		public boolean existsById(Long aLong) {
			return false;
		}

		@Override
		public long count() {
			return 0;
		}

		@Override
		public void deleteById(Long aLong) {
		}

		@Override
		public void delete(TestTenantAwareEntity entity) {
		}

		@Override
		public void deleteAllById(Iterable<? extends Long> longs) {
		}

		@Override
		public void deleteAll(Iterable<? extends TestTenantAwareEntity> entities) {
		}

		@Override
		public void deleteAll() {
		}

		@Override
		public <S extends TestTenantAwareEntity> Optional<S> findOne(
				org.springframework.data.domain.Example<S> example) {
			return Optional.empty();
		}

		@Override
		public <S extends TestTenantAwareEntity> org.springframework.data.domain.Page<S> findAll(
				org.springframework.data.domain.Example<S> example, org.springframework.data.domain.Pageable pageable) {
			return org.springframework.data.domain.Page.empty();
		}

		@Override
		public <S extends TestTenantAwareEntity> long count(org.springframework.data.domain.Example<S> example) {
			return 0;
		}

		@Override
		public <S extends TestTenantAwareEntity> boolean exists(org.springframework.data.domain.Example<S> example) {
			return false;
		}

		@Override
		public <S extends TestTenantAwareEntity, R> R findBy(org.springframework.data.domain.Example<S> example,
				Function<org.springframework.data.repository.query.FluentQuery.FetchableFluentQuery<S>, R> queryFunction) {
			return null;
		}

	}

	// ── 測試案例 ───────────────────────────────────────────────────────────

	@Test
	void checkConsistency_allCompliant_returnsEmpty() {
		Object compliant = new CompliantRepoImpl();
		Function<Class<?>, Optional<Object>> lookup = entity -> Optional.of(compliant);

		List<String> violations = TenantConsistencyValidator.checkConsistency(Set.of(TestTenantAwareEntity.class),
				lookup);

		assertThat(violations).isEmpty();
	}

	@Test
	void checkConsistency_repoMissingMarker_reportsViolation() {
		Object nonCompliant = new NonCompliantRepoImpl();
		Function<Class<?>, Optional<Object>> lookup = entity -> Optional.of(nonCompliant);

		List<String> violations = TenantConsistencyValidator.checkConsistency(Set.of(TestTenantAwareEntity.class),
				lookup);

		assertThat(violations).hasSize(1);
		assertThat(violations.get(0)).contains("NonCompliantRepoImpl").contains("TestTenantAwareEntity");
	}

	@Test
	void checkConsistency_noRepositoryForEntity_isSkipped() {
		// 例如純 DAO（PasswordPolicyDao）對應的 entity 沒有 Spring Data Repository
		Function<Class<?>, Optional<Object>> lookup = entity -> Optional.empty();

		List<String> violations = TenantConsistencyValidator.checkConsistency(Set.of(TestTenantAwareEntity.class),
				lookup);

		assertThat(violations).isEmpty();
	}

	@Test
	void checkConsistency_mixed_reportsOnlyOffenders() {
		class OtherEntity implements TenantAware {

			@Override
			public String getTenantId() {
				return null;
			}

			@Override
			public void setTenantId(String tenantId) {
			}

		}
		Map<Class<?>, Object> repoMap = new HashMap<>();
		repoMap.put(TestTenantAwareEntity.class, new CompliantRepoImpl());
		repoMap.put(OtherEntity.class, new NonCompliantRepoImpl());

		Function<Class<?>, Optional<Object>> lookup = entity -> Optional.ofNullable(repoMap.get(entity));

		List<String> violations = TenantConsistencyValidator
			.checkConsistency(Set.of(TestTenantAwareEntity.class, OtherEntity.class), lookup);

		assertThat(violations).hasSize(1);
		assertThat(violations.get(0)).contains("OtherEntity");
	}

	@Test
	void checkConsistency_emptyEntitySet_returnsEmpty() {
		List<String> violations = TenantConsistencyValidator.checkConsistency(Set.of(), entity -> Optional.empty());

		assertThat(violations).isEmpty();
	}

}
