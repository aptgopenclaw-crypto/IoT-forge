package com.taipei.iot.setting.entity;

import jakarta.persistence.Version;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

class SystemSettingEntityVersionTest {

	@Test
	void versionField_shouldHaveVersionAnnotation() throws NoSuchFieldException {
		Field versionField = SystemSettingEntity.class.getDeclaredField("version");
		assertThat(versionField.isAnnotationPresent(Version.class)).isTrue();
	}

	@Test
	void builder_shouldSetVersionField() {
		SystemSettingEntity entity = SystemSettingEntity.builder()
			.settingKey("idle_timeout_minutes")
			.settingValue("15")
			.version(0)
			.build();

		assertThat(entity.getVersion()).isEqualTo(0);
	}

	@Test
	void setVersion_shouldUpdateField() {
		SystemSettingEntity entity = new SystemSettingEntity();
		entity.setVersion(3);
		assertThat(entity.getVersion()).isEqualTo(3);
	}

}
