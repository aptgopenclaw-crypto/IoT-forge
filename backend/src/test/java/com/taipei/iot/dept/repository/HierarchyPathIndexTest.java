package com.taipei.iot.dept.repository;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * [Dept v2 N-1] 驗證 V55 migration SQL 正確建立 hierarchy_path 索引。 純檔案內容驗證（不需 DB），確保 migration
 * 含必要的索引定義。
 */
class HierarchyPathIndexTest {

	private static String migrationSql;

	@BeforeAll
	static void loadMigration() throws IOException {
		Path path = Path.of("src/main/resources/db/migration/V55__dept__add_hierarchy_path_index.sql");
		migrationSql = Files.readString(path);
	}

	@Test
	void migration_shouldCreateIndexOnHierarchyPath() {
		assertThat(migrationSql).containsIgnoringCase("CREATE INDEX");
		assertThat(migrationSql).contains("idx_dept_hierarchy_path");
		assertThat(migrationSql).contains("dept_info");
		assertThat(migrationSql).contains("hierarchy_path");
	}

	@Test
	void migration_shouldUseTextPatternOps() {
		// text_pattern_ops ensures LIKE 'prefix%' can use this index
		// regardless of database collation
		assertThat(migrationSql).contains("text_pattern_ops");
	}

	@Test
	void migration_shouldBePartialIndexOnActiveStatus() {
		// Partial index WHERE status = 1 matches Repository query
		assertThat(migrationSql).containsIgnoringCase("WHERE status = 1");
	}

	@Test
	void migration_shouldTargetDeptInfoTable() {
		assertThat(migrationSql).containsIgnoringCase("ON dept_info");
	}

}
