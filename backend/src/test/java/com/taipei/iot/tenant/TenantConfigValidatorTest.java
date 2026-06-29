package com.taipei.iot.tenant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * [Tenant v2 T-9] {@link TenantConfigValidator} 驗證行為。
 */
class TenantConfigValidatorTest {

	@Nested
	@DisplayName("合法情境")
	class Valid {

		@Test
		@DisplayName("null config — 視為合法（不寫 jsonb）")
		void nullConfig_isValid() {
			assertThatCode(() -> TenantConfigValidator.validate(null)).doesNotThrowAnyException();
		}

		@Test
		@DisplayName("空 map — 視為合法")
		void emptyConfig_isValid() {
			assertThatCode(() -> TenantConfigValidator.validate(Map.of())).doesNotThrowAnyException();
		}

		@Test
		@DisplayName("一般 feature flag 結構 — 合法")
		void normalConfig_isValid() {
			Map<String, Object> cfg = new LinkedHashMap<>();
			cfg.put("featureA", true);
			cfg.put("featureB", false);
			cfg.put("quota", Map.of("maxUsers", 100, "maxStorageGb", 50));
			cfg.put("tags", List.of("vip", "beta"));

			assertThatCode(() -> TenantConfigValidator.validate(cfg)).doesNotThrowAnyException();
		}

		@Test
		@DisplayName("剛好達到上限的深度（5 層） — 合法")
		void atMaxDepth_isValid() {
			// depth=5: top map → L2 map → L3 map → L4 map → L5 value
			Map<String, Object> cfg = nestedMaps(TenantConfigValidator.MAX_DEPTH);
			assertThatCode(() -> TenantConfigValidator.validate(cfg)).doesNotThrowAnyException();
		}

		@Test
		@DisplayName("剛好達到上限的 key 數（50） — 合法")
		void atMaxKeys_isValid() {
			Map<String, Object> cfg = new HashMap<>();
			for (int i = 0; i < TenantConfigValidator.MAX_TOP_LEVEL_KEYS; i++) {
				cfg.put("k" + i, i);
			}
			assertThatCode(() -> TenantConfigValidator.validate(cfg)).doesNotThrowAnyException();
		}

	}

	@Nested
	@DisplayName("違規情境")
	class Invalid {

		@Test
		@DisplayName("超出 top-level keys 上限 — 拒絕")
		void exceedTopLevelKeys_rejected() {
			Map<String, Object> cfg = new HashMap<>();
			for (int i = 0; i <= TenantConfigValidator.MAX_TOP_LEVEL_KEYS; i++) {
				cfg.put("k" + i, i);
			}
			assertThatThrownBy(() -> TenantConfigValidator.validate(cfg)).isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("top-level keys")
				.hasMessageContaining(String.valueOf(TenantConfigValidator.MAX_TOP_LEVEL_KEYS));
		}

		@Test
		@DisplayName("超出巢狀深度 — 拒絕")
		void exceedDepth_rejected() {
			Map<String, Object> cfg = nestedMaps(TenantConfigValidator.MAX_DEPTH + 1);
			assertThatThrownBy(() -> TenantConfigValidator.validate(cfg)).isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("巢狀深度");
		}

		@Test
		@DisplayName("透過 List 包裝繞過深度限制 — 一樣拒絕")
		void exceedDepth_viaCollection_rejected() {
			// top → L2 list → L3 map → L4 list → L5 map → L6 value（超出 5 層）
			Object payload = "v";
			for (int i = 0; i < TenantConfigValidator.MAX_DEPTH; i++) {
				payload = (i % 2 == 0) ? Map.of("k", payload) : List.of(payload);
			}
			Map<String, Object> cfg = Map.of("root", payload);
			assertThatThrownBy(() -> TenantConfigValidator.validate(cfg)).isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("巢狀深度");
		}

		@Test
		@DisplayName("序列化後超出 10 KB — 拒絕")
		void exceedSerializedSize_rejected() {
			// 單一 value 塞 11 KB 字串 → 序列化後必定 > 10 KB
			String bigValue = "x".repeat(TenantConfigValidator.MAX_SERIALIZED_BYTES + 1024);
			Map<String, Object> cfg = Map.of("payload", bigValue);

			assertThatThrownBy(() -> TenantConfigValidator.validate(cfg)).isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("序列化後大小");
		}

		@Test
		@DisplayName("錯誤訊息提供實際與上限數值（便於除錯）")
		void errorMessage_includesActualAndLimit() {
			String bigValue = "y".repeat(TenantConfigValidator.MAX_SERIALIZED_BYTES + 1024);
			Map<String, Object> cfg = Map.of("payload", bigValue);

			assertThatThrownBy(() -> TenantConfigValidator.validate(cfg))
				.hasMessageContaining(String.valueOf(TenantConfigValidator.MAX_SERIALIZED_BYTES));
		}

	}

	@Nested
	@DisplayName("常數契約（防止無預警放寬）")
	class Constants {

		@Test
		void maxSerializedBytes_is10KB() {
			assertThat(TenantConfigValidator.MAX_SERIALIZED_BYTES).isEqualTo(10 * 1024);
		}

		@Test
		void maxTopLevelKeys_is50() {
			assertThat(TenantConfigValidator.MAX_TOP_LEVEL_KEYS).isEqualTo(50);
		}

		@Test
		void maxDepth_is5() {
			assertThat(TenantConfigValidator.MAX_DEPTH).isEqualTo(5);
		}

	}

	@Nested
	@DisplayName("Entity 整合（@PrePersist / @PreUpdate）")
	class EntityIntegration {

		@Test
		@DisplayName("TenantEntity 在 persist 前會呼叫 validator — 透過反射觸發 lifecycle method")
		void entityLifecycleMethod_invokesValidator() throws Exception {
			TenantEntity e = new TenantEntity();
			String bigValue = "z".repeat(TenantConfigValidator.MAX_SERIALIZED_BYTES + 1024);
			Map<String, Object> bad = new HashMap<>();
			bad.put("payload", bigValue);
			e.setConfig(bad);

			// 直接觸發 @PrePersist private method（不啟動 JPA 容器）
			var method = TenantEntity.class.getDeclaredMethod("validateConfig");
			method.setAccessible(true);

			assertThatThrownBy(() -> {
				try {
					method.invoke(e);
				}
				catch (java.lang.reflect.InvocationTargetException ite) {
					throw ite.getCause();
				}
			}).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("序列化後大小");
		}

	}

	// ─── helpers ─────────────────────────────────────────────────────────

	/**
	 * 建構一個讓 {@link TenantConfigValidator} 計算出的深度恰好等於 {@code desiredDepth} 的結構。
	 *
	 * <p>
	 * {@code depthOf} 從 top map 起算 {@code current=1}，每往下遞迴一層 +1，碰到 leaf 時回傳當前
	 * {@code current}。因此 N 層 nested map + leaf 會回傳 N+1。要得到 depth = {@code desiredDepth}，
	 * 需要 {@code desiredDepth - 1} 層 nested maps。
	 */
	private static Map<String, Object> nestedMaps(int desiredDepth) {
		Object inner = "leaf";
		// 內層先建 (desiredDepth - 2) 層 map，最後在外層再包一層 → 共 (desiredDepth - 1) 層 map
		for (int i = 0; i < desiredDepth - 2; i++) {
			inner = Map.of("k", inner);
		}
		Map<String, Object> top = new HashMap<>();
		top.put("root", inner);
		return top;
	}

}
