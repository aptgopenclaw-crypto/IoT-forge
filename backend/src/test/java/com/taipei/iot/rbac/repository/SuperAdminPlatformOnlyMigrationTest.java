package com.taipei.iot.rbac.repository;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * [Phase 3 / 3.1.2] 驗證 V65 migration SQL 內容： 1. 先清空 ROLE_SUPER_ADMIN 既有
 * role_permissions（避免歷史殘留） 2. 只綁定 4 個 PLATFORM_* 權限（tenant_id = NULL）
 *
 * 純檔案內容驗證（不需 DB），確保 V65 不會意外授予 super_admin 非 PLATFORM_* 權限。
 */
class SuperAdminPlatformOnlyMigrationTest {

	private static String migrationSql;

	@BeforeAll
	static void loadMigration() throws IOException {
		Path path = Path.of("src/main/resources/db/migration/V65__rbac__super_admin_platform_only.sql");
		migrationSql = Files.readString(path);
	}

	@Test
	void migration_shouldDeleteExistingSuperAdminBindings() {
		// 必須先 DELETE，否則歷史環境若曾手動授 USER_LIST 等 tenant 權限，
		// Phase 3 強制隔離後 super_admin 仍能呼叫租戶 API
		assertThat(migrationSql).containsIgnoringCase("DELETE FROM role_permissions");
		assertThat(migrationSql).contains("'ROLE_SUPER_ADMIN'");
	}

	@Test
	void migration_shouldBindAllFourPlatformPermissions() {
		assertThat(migrationSql).contains("'PLATFORM_TENANT_MANAGE'");
		assertThat(migrationSql).contains("'PLATFORM_PASSWORD_POLICY_MANAGE'");
		assertThat(migrationSql).contains("'PLATFORM_USER_TENANT_MAPPING'");
		assertThat(migrationSql).contains("'PLATFORM_IMPERSONATE'");
	}

	@Test
	void migration_shouldUseNullTenantIdForGlobalPermissions() {
		// tenant_id = NULL 代表全租戶共用權限；super_admin 不綁定特定租戶
		assertThat(migrationSql).containsIgnoringCase("SELECT 'ROLE_SUPER_ADMIN'");
		assertThat(migrationSql).containsIgnoringCase("NULL");
	}

	@Test
	void migration_shouldNotBindAnyTenantScopedPermissions() {
		// 防止有人複製貼上把 USER_LIST / DEPT_LIST 等租戶權限加進來
		assertThat(migrationSql).doesNotContain("'USER_LIST'");
		assertThat(migrationSql).doesNotContain("'USER_UPDATE'");
		assertThat(migrationSql).doesNotContain("'DEPT_LIST'");
		assertThat(migrationSql).doesNotContain("'MENU_LIST'");
		assertThat(migrationSql).doesNotContain("'AUDIT_LIST'");
		assertThat(migrationSql).doesNotContain("'ROLE_LIST'");
	}

	@Test
	void migration_shouldUseOnConflictDoNothingForIdempotency() {
		assertThat(migrationSql).containsIgnoringCase("ON CONFLICT DO NOTHING");
	}

}
