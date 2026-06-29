package com.taipei.iot.user.entity;

import com.taipei.iot.tenant.TenantAware;
import com.taipei.iot.tenant.TenantEntityListener;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
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
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_info_log")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@EntityListeners({ TenantEntityListener.class, AuditingEntityListener.class })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserInfoLogEntity implements TenantAware {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	@Column(name = "tenant_id", length = 50, nullable = false)
	private String tenantId;

	@Column(name = "action_type", length = 20, nullable = false)
	private String actionType;

	@Column(name = "action_user_id", length = 50, nullable = false)
	private String actionUserId;

	@Column(name = "target_user_id", length = 50, nullable = false)
	private String targetUserId;

	@Column(name = "email", length = 200)
	private String email;

	@Column(name = "display_name", length = 200)
	private String displayName;

	@Column(name = "role_code", length = 50)
	private String roleCode;

	@Column(name = "dept_id", length = 50)
	private String deptId;

	@Column(name = "detail", length = 1000)
	private String detail;

	/**
	 * [Phase B] SUPER_ADMIN 在 tenant context 下執行時填入其 userId；一般操作為 null。
	 */
	@Column(name = "impersonated_by", length = 50)
	private String impersonatedBy;

	@CreatedDate
	@Column(name = "create_time", nullable = false, updatable = false)
	private LocalDateTime createTime;

}
