package com.taipei.iot.auth.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.taipei.iot.auth.provider.AuthType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserEntity {

	@Id
	@Column(name = "user_id", length = 50)
	private String userId;

	@Column(name = "email", length = 200, nullable = false, unique = true)
	private String email;

	@JsonIgnore
	@Column(name = "password_hash", length = 255, nullable = false)
	private String passwordHash;

	@Column(name = "display_name", length = 200, nullable = false)
	private String displayName;

	@Column(name = "phone", length = 50)
	private String phone;

	@Column(name = "enabled", nullable = false)
	@Builder.Default
	private Boolean enabled = true;

	@Column(name = "locked", nullable = false)
	@Builder.Default
	private Boolean locked = false;

	@Column(name = "locked_at")
	private LocalDateTime lockedAt;

	@JsonIgnore
	@Column(name = "login_fail_count", nullable = false)
	@Builder.Default
	private Integer loginFailCount = 0;

	@JsonIgnore
	@Column(name = "is_super_admin", nullable = false)
	@Builder.Default
	private Boolean isSuperAdmin = false;

	@Column(name = "last_login_at")
	private LocalDateTime lastLoginAt;

	@Column(name = "deleted", nullable = false)
	@Builder.Default
	private Boolean deleted = false;

	@Column(name = "deleted_at")
	private LocalDateTime deletedAt;

	@CreatedDate
	@Column(name = "create_time", nullable = false, updatable = false)
	private LocalDateTime createTime;

	@LastModifiedDate
	@Column(name = "update_time")
	private LocalDateTime updateTime;

	@Column(name = "notify_email_flag", nullable = false)
	@Builder.Default
	private Boolean notifyEmailFlag = true;

	@Column(name = "notify_sms_flag", nullable = false)
	@Builder.Default
	private Boolean notifySmsFlag = true;

	/**
	 * [Phase 3] Timestamp of the latest successful password change. Maintained by
	 * AuthServiceImpl.resetPassword / UserSelfService.changePassword /
	 * UserAdminService.createUser / forceChangePassword. Used by PasswordExpiryChecker.
	 */
	@Column(name = "password_changed_at", nullable = false)
	private LocalDateTime passwordChangedAt;

	/**
	 * [Phase 3] When true, login succeeds but immediately requires the user to change
	 * their password. Set by admin-driven flows (initial user creation, admin-reset) per
	 * the password.force_change_on_first_login / force_change_on_admin_reset policy keys.
	 */
	@Column(name = "force_change_password", nullable = false)
	@Builder.Default
	private Boolean forceChangePassword = false;

	/** Authentication source for this account: LOCAL / LDAP / OIDC / SAML */
	@Enumerated(EnumType.STRING)
	@Column(name = "auth_type", nullable = false)
	@Builder.Default
	private AuthType authType = AuthType.LOCAL;

	/** External IdP unique identifier (LDAP DN / OIDC sub / SAML nameId) */
	@Column(name = "external_id")
	private String externalId;

}
