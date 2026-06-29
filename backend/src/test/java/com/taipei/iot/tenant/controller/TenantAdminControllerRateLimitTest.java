package com.taipei.iot.tenant.controller;

import com.taipei.iot.common.annotation.RateLimit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * [Tenant v2 T-11] 鎖死 TenantAdminController 的速率限制配置。
 *
 * <p>
 * 本測試以反射檢查 annotation，不啟動 Spring context，可在毫秒級執行； 目的是防止未來新增端點時忘記掛 {@code @RateLimit}，讓
 * SUPER_ADMIN token 外洩時仍有最後一道流量上限。
 */
class TenantAdminControllerRateLimitTest {

	@Test
	@DisplayName("class-level @PreAuthorize 仍鎖在 PLATFORM_TENANT_MANAGE（防衝突保護未被誤刪）")
	void classLevelPreAuthorize_isSuperAdmin() {
		PreAuthorize ann = TenantAdminController.class.getAnnotation(PreAuthorize.class);
		assertThat(ann).isNotNull();
		assertThat(ann.value()).contains("PLATFORM_TENANT_MANAGE");
	}

	@Test
	@DisplayName("listTenants 掛上 @RateLimit（讀操作較寬鬆）")
	void listTenants_hasRateLimit() throws Exception {
		RateLimit rl = rateLimitOn("listTenants");
		assertThat(rl.key()).isEqualTo("admin-tenant-list");
		assertSensibleBounds(rl, 1, 120, 1, 3600);
	}

	@Test
	@DisplayName("createTenant 掛上 @RateLimit（寫操作嚴格 — 防止大量建立撐爆 DB）")
	void createTenant_hasRateLimit() throws Exception {
		RateLimit rl = rateLimitOn("createTenant");
		assertThat(rl.key()).isEqualTo("admin-tenant-create");
		// 寫操作上限應 ≤ 30/分（否則失去意義）
		assertSensibleBounds(rl, 1, 30, 30, 600);
	}

	@Test
	@DisplayName("updateTenant 掛上 @RateLimit")
	void updateTenant_hasRateLimit() throws Exception {
		RateLimit rl = rateLimitOn("updateTenant");
		assertThat(rl.key()).isEqualTo("admin-tenant-update");
		assertSensibleBounds(rl, 1, 60, 30, 600);
	}

	@Test
	@DisplayName("toggleEnabled 掛上 @RateLimit（避免反覆切換造成 cache 抖動 / Pub/Sub 風暴）")
	void toggleEnabled_hasRateLimit() throws Exception {
		RateLimit rl = rateLimitOn("toggleEnabled");
		assertThat(rl.key()).isEqualTo("admin-tenant-toggle");
		assertSensibleBounds(rl, 1, 60, 30, 600);
	}

	@Test
	@DisplayName("所有公開 HTTP 方法都必須掛 @RateLimit（防止未來新增端點漏網）")
	void everyEndpoint_hasRateLimit() {
		// 已知端點集合 — 新增方法時請同步更新並補 @RateLimit
		Set<String> expected = new HashSet<>(Set.of("listTenants", "createTenant", "updateTenant", "toggleEnabled"));

		for (Method m : TenantAdminController.class.getDeclaredMethods()) {
			if (!java.lang.reflect.Modifier.isPublic(m.getModifiers()))
				continue;
			if (m.isSynthetic() || m.isBridge())
				continue;
			if (!expected.contains(m.getName()))
				continue;

			RateLimit rl = m.getAnnotation(RateLimit.class);
			assertThat(rl).as("方法 %s 必須掛 @RateLimit（SUPER_ADMIN token 外洩時的最後防線）", m.getName()).isNotNull();
		}
	}

	@Test
	@DisplayName("@RateLimit key 必須唯一（避免不同端點共用同一 Redis 計數器）")
	void rateLimitKeys_areUnique() {
		Set<String> keys = new HashSet<>();
		for (Method m : TenantAdminController.class.getDeclaredMethods()) {
			RateLimit rl = m.getAnnotation(RateLimit.class);
			if (rl == null)
				continue;
			assertThat(keys.add(rl.key())).as("重複的 @RateLimit key: %s（方法 %s）", rl.key(), m.getName()).isTrue();
		}
	}

	// ─── helpers ─────────────────────────────────────────────────────────

	private static RateLimit rateLimitOn(String methodName) throws NoSuchMethodException {
		for (Method m : TenantAdminController.class.getDeclaredMethods()) {
			if (m.getName().equals(methodName)) {
				RateLimit rl = m.getAnnotation(RateLimit.class);
				if (rl != null)
					return rl;
			}
		}
		throw new NoSuchMethodException("No @RateLimit on " + methodName);
	}

	private static void assertSensibleBounds(RateLimit rl, int minLimit, int maxLimit, int minPeriod, int maxPeriod) {
		assertThat(rl.limit()).as("limit 應在 [%d, %d] 區間", minLimit, maxLimit).isBetween(minLimit, maxLimit);
		assertThat(rl.period()).as("period(秒) 應在 [%d, %d] 區間", minPeriod, maxPeriod).isBetween(minPeriod, maxPeriod);
	}

}
