package com.taipei.iot.dept.aspect;

import com.taipei.iot.dept.annotation.DataPermission;
import com.taipei.iot.dept.context.DataScopeContext;
import com.taipei.iot.dept.context.DataScopeFilter;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * [Dept v2 N-2] 驗證 @DataPermission AOP 基礎設施標註為「預留框架」， 且目前無任何 Service 方法使用 @DataPermission
 * 註解。
 */
class DataPermissionReservedFrameworkTest {

	@Test
	void aspect_classJavadoc_shouldIndicateReserved() throws IOException {
		String source = Files.readString(Path.of("src/main/java/com/taipei/iot/dept/aspect/DataPermissionAspect.java"));
		assertThat(source).contains("預留框架");
		assertThat(source).contains("DataScopeHelper");
	}

	@Test
	void annotation_classJavadoc_shouldIndicateReserved() throws IOException {
		String source = Files.readString(Path.of("src/main/java/com/taipei/iot/dept/annotation/DataPermission.java"));
		assertThat(source).contains("預留框架");
		assertThat(source).contains("無任何方法使用此註解");
	}

	@Test
	void context_classJavadoc_shouldIndicateReserved() throws IOException {
		String source = Files.readString(Path.of("src/main/java/com/taipei/iot/dept/context/DataScopeContext.java"));
		assertThat(source).contains("預留框架");
		assertThat(source).contains("無任何 Repository/Service 消費端讀取");
	}

	@Test
	void filter_classJavadoc_shouldIndicateReserved() throws IOException {
		String source = Files.readString(Path.of("src/main/java/com/taipei/iot/dept/context/DataScopeFilter.java"));
		assertThat(source).contains("預留框架");
		assertThat(source).contains("無消費端");
	}

	@Test
	void noServiceUsesDataPermissionAnnotation() throws IOException {
		// Scan all service files to confirm @DataPermission is not applied
		Path serviceDir = Path.of("src/main/java/com/taipei/iot");
		long count = Files.walk(serviceDir).filter(p -> p.toString().endsWith("Service.java")).filter(p -> {
			try {
				return Files.readString(p).contains("@DataPermission");
			}
			catch (IOException e) {
				return false;
			}
		}).count();
		assertThat(count).as("No Service class should use @DataPermission annotation").isZero();
	}

}
