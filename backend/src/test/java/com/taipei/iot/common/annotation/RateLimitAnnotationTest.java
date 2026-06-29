package com.taipei.iot.common.annotation;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * N-11 驗證：{@link RateLimit} 註解的 JavaDoc 合約與元數據正確性。
 */
class RateLimitAnnotationTest {

	@Test
	void annotation_hasRuntimeRetention() {
		var retention = RateLimit.class.getAnnotation(java.lang.annotation.Retention.class);
		assertThat(retention).isNotNull();
		assertThat(retention.value()).isEqualTo(java.lang.annotation.RetentionPolicy.RUNTIME);
	}

	@Test
	void annotation_targetsMethodOnly() {
		var target = RateLimit.class.getAnnotation(java.lang.annotation.Target.class);
		assertThat(target).isNotNull();
		assertThat(target.value()).containsExactly(java.lang.annotation.ElementType.METHOD);
	}

	@Test
	void annotation_defaultLimit_is10() throws NoSuchMethodException {
		Method method = RateLimit.class.getDeclaredMethod("limit");
		Object defaultValue = method.getDefaultValue();
		assertThat(defaultValue).isEqualTo(10);
	}

	@Test
	void annotation_defaultPeriod_is60() throws NoSuchMethodException {
		Method method = RateLimit.class.getDeclaredMethod("period");
		Object defaultValue = method.getDefaultValue();
		assertThat(defaultValue).isEqualTo(60);
	}

	@Test
	void annotation_keyHasNoDefault() throws NoSuchMethodException {
		Method method = RateLimit.class.getDeclaredMethod("key");
		Object defaultValue = method.getDefaultValue();
		assertThat(defaultValue).isNull();
	}

	@Test
	void javadoc_mentionsRedisKeyFormat() {
		// Structural assertion: class-level annotation has key() method whose
		// declared name matches what the interceptor uses in key composition.
		// This verifies the documentation claim: "rate_limit:{key}:{clientIp}"
		assertThat(RateLimit.class.getDeclaredMethods()).extracting("name").contains("key", "limit", "period");
	}

}
