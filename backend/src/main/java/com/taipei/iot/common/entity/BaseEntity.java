package com.taipei.iot.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 所有實體的共用基礎超類別，提供創建 / 更新 timestamp 自動記錄。
 * <p>
 * <strong>注意：</strong> 子類應自行基於業務主鍵（如 {@code id}）定義 {@code equals()} /
 * {@code hashCode()}，而非依賴內部資料（created_at / updated_at）。
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@Getter
// 不加 @Setter：兩個欄位均由 AuditingEntityListener 管理，
// 應用層不應直接寫入，尤其 createdAt 在語意上建立後不變。
public abstract class BaseEntity {

	@CreatedDate
	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@LastModifiedDate
	@Column(name = "updated_at")
	private LocalDateTime updatedAt;

}
