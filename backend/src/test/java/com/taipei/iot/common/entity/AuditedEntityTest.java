package com.taipei.iot.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.LastModifiedBy;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * [common v2 F-12] {@link AuditedEntity} 結構規格測試。
 *
 * <p>
 * 透過反射驗證欄位 / 註解 / 繼承關係，避免日後有人意外修改而破壞契約 （如：拿掉 {@code @CreatedBy}、把 {@code created_by} 改成
 * updatable、改 length 等）。
 */
class AuditedEntityTest {

	@Test
	@DisplayName("應繼承 BaseEntity 並標記 @MappedSuperclass")
	void shouldExtendBaseEntityAndBeMappedSuperclass() {
		assertThat(AuditedEntity.class.getSuperclass()).isEqualTo(BaseEntity.class);
		assertThat(AuditedEntity.class.getAnnotation(MappedSuperclass.class)).isNotNull();
		assertThat(Modifier.isAbstract(AuditedEntity.class.getModifiers())).isTrue();
	}

	@Test
	@DisplayName("createdBy 應有 @CreatedBy + @Column(name=created_by, updatable=false, length=50)")
	void createdByFieldContract() throws NoSuchFieldException {
		Field f = AuditedEntity.class.getDeclaredField("createdBy");

		assertThat(f.getType()).isEqualTo(String.class);
		assertThat(f.getAnnotation(CreatedBy.class)).isNotNull();

		Column col = f.getAnnotation(Column.class);
		assertThat(col).isNotNull();
		assertThat(col.name()).isEqualTo("created_by");
		assertThat(col.length()).isEqualTo(50);
		assertThat(col.updatable()).isFalse();
	}

	@Test
	@DisplayName("updatedBy 應有 @LastModifiedBy + @Column(name=updated_by, length=50)")
	void updatedByFieldContract() throws NoSuchFieldException {
		Field f = AuditedEntity.class.getDeclaredField("updatedBy");

		assertThat(f.getType()).isEqualTo(String.class);
		assertThat(f.getAnnotation(LastModifiedBy.class)).isNotNull();

		Column col = f.getAnnotation(Column.class);
		assertThat(col).isNotNull();
		assertThat(col.name()).isEqualTo("updated_by");
		assertThat(col.length()).isEqualTo(50);
		// 與 createdBy 不同，updatedBy 必須允許 update
		assertThat(col.updatable()).isTrue();
	}

	@Test
	@DisplayName("createdByName 應有 @Column(name=created_by_name, length=100) 且不自動稽核")
	void createdByNameFieldContract() throws NoSuchFieldException {
		Field f = AuditedEntity.class.getDeclaredField("createdByName");

		assertThat(f.getType()).isEqualTo(String.class);
		// 不應有 @CreatedBy（姓名 snapshot 由 service 層顯式寫入）
		assertThat(f.getAnnotation(CreatedBy.class)).isNull();

		Column col = f.getAnnotation(Column.class);
		assertThat(col).isNotNull();
		assertThat(col.name()).isEqualTo("created_by_name");
		assertThat(col.length()).isEqualTo(100);
	}

	@Test
	@DisplayName("getter / setter 應正常運作（Lombok 產生）")
	void gettersAndSettersWork() {
		AuditedEntity entity = new AuditedEntity() {
		};

		entity.setCreatedBy("user-1");
		entity.setUpdatedBy("user-2");
		entity.setCreatedByName("Alice");

		assertThat(entity.getCreatedBy()).isEqualTo("user-1");
		assertThat(entity.getUpdatedBy()).isEqualTo("user-2");
		assertThat(entity.getCreatedByName()).isEqualTo("Alice");
	}

	@Test
	@DisplayName("應繼承 BaseEntity 的 createdAt / updatedAt 欄位")
	void shouldInheritTimestampsFromBase() {
		AuditedEntity entity = new AuditedEntity() {
		};

		// 透過 BaseEntity 的 getter（Lombok @Getter）
		assertThat(entity.getCreatedAt()).isNull();
		assertThat(entity.getUpdatedAt()).isNull();
	}

}
