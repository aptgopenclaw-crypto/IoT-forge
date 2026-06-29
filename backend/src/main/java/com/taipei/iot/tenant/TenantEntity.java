package com.taipei.iot.tenant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.Map;

@Entity
@Table(name = "tenant")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
public class TenantEntity {

	@Id
	@Column(name = "tenant_id", length = 50)
	private String tenantId;

	@Column(name = "tenant_code", length = 50, nullable = false, unique = true)
	private String tenantCode;

	@Column(name = "tenant_name", length = 200, nullable = false)
	private String tenantName;

	@Column(name = "deployment_mode", length = 20, nullable = false)
	private String deploymentMode;

	/**
	 * Tenant 層級的設定 jsonb 欄位。
	 *
	 * <p>
	 * [Tenant v2 T-9] 目前**沒有任何 API 寫入** {@code config}（CRUD layer 沒給 setter 入口）， 僅供未來擴展（如
	 * feature flags、quota 設定）。一旦未來開放寫入， {@link #validateConfig()} 會在 persist / update 前由
	 * {@link TenantConfigValidator} 把關大小（≤10 KB）、top-level keys（≤50）、巢狀深度（≤5）， 避免被當成
	 * NoSQL 注入點塞入超大 JSON 撐爆 DB / index。
	 */
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "config", columnDefinition = "jsonb")
	private Map<String, Object> config;

	@Column(name = "enabled", nullable = false)
	private Boolean enabled;

	@CreatedDate
	@Column(name = "create_time", nullable = false, updatable = false)
	private LocalDateTime createTime;

	@LastModifiedDate
	@Column(name = "update_time")
	private LocalDateTime updateTime;

	// [Tenant v2 T-9] persist / update 前驗證 config，違反時拋 IllegalArgumentException
	// → GlobalExceptionHandler 轉成 400 Bad Request。
	@PrePersist
	@PreUpdate
	private void validateConfig() {
		TenantConfigValidator.validate(this.config);
	}

}
