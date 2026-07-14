package com.taipei.iot.vms.entity;

import com.taipei.iot.common.tenant.TenantAware;
import com.taipei.iot.common.tenant.TenantEntityListener;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Filter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "vms_server")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@EntityListeners({ TenantEntityListener.class, AuditingEntityListener.class })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VmsServerEntity implements TenantAware {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "tenant_id", nullable = false)
	private String tenantId;

	@Column(nullable = false, length = 100)
	private String name;

	@Column(name = "vms_type", nullable = false, length = 20)
	private String vmsType;

	@Column(name = "base_url", nullable = false, length = 255)
	private String baseUrl;

	@Column(name = "auth_type", length = 20)
	private String authType;

	@Column(name = "auth_username", length = 100)
	private String authUsername;

	@Column(name = "auth_password", length = 255)
	private String authPassword;

	@Column(name = "api_token", length = 500)
	private String apiToken;

	@Column(name = "is_active", nullable = false)
	private Boolean isActive;

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
