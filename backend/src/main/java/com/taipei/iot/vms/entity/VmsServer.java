package com.taipei.iot.vms.entity;

import com.taipei.iot.common.tenant.TenantAware;
import com.taipei.iot.common.tenant.TenantEntityListener;
import com.taipei.iot.vms.enums.VmsAuthType;
import com.taipei.iot.vms.enums.VmsType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Filter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * VMS 伺服器設定實體，對應 {@code vms_servers} 表。
 *
 * <p>
 * 租戶隔離：掛 {@code tenantFilter}，{@link TenantEntityListener} 防跨租戶篡改。
 * </p>
 */
@Entity
@Table(name = "vms_servers")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@EntityListeners({ TenantEntityListener.class, AuditingEntityListener.class })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VmsServer implements TenantAware {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "tenant_id", nullable = false, length = 50)
	private String tenantId;

	@Column(name = "name", nullable = false, length = 100)
	private String name;

	@Enumerated(EnumType.STRING)
	@Column(name = "vms_type", nullable = false, length = 30)
	private VmsType vmsType;

	@Column(name = "base_url", nullable = false, length = 500)
	private String baseUrl;

	@Enumerated(EnumType.STRING)
	@Column(name = "auth_type", nullable = false, length = 20)
	@Builder.Default
	private VmsAuthType authType = VmsAuthType.BASIC;

	@Column(name = "auth_username", length = 100)
	private String authUsername;

	@Column(name = "auth_password", length = 255)
	private String authPassword;

	@Column(name = "api_token", length = 500)
	private String apiToken;

	@Builder.Default
	@Column(name = "is_active", nullable = false)
	private Boolean isActive = true;

	@CreatedDate
	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@LastModifiedDate
	@Column(name = "updated_at")
	private LocalDateTime updatedAt;

	@Override
	public String getTenantId() {
		return tenantId;
	}

	@Override
	public void setTenantId(String tenantId) {
		this.tenantId = tenantId;
	}

}
