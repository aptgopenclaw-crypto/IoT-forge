package com.taipei.iot.platform.impersonation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
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

/**
 * Impersonation session record (ADR-002 / Phase 1).
 *
 * <p>
 * Created by {@code POST /v1/platform/impersonations} with status=ACTIVE, revoked by
 * {@code DELETE /v1/platform/impersonations/{id}} (REVOKED), or implicitly EXPIRED once
 * {@link #expiresAt} elapses.
 *
 * <p>
 * Schema: {@code V64__platform__impersonation_session.sql}.
 */
@Entity
@Table(name = "impersonation_session")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImpersonationSessionEntity {

	public static final String STATUS_ACTIVE = "ACTIVE";

	public static final String STATUS_REVOKED = "REVOKED";

	public static final String STATUS_EXPIRED = "EXPIRED";

	@Id
	@Column(name = "id", length = 50)
	private String id;

	@Column(name = "operator_user_id", length = 50, nullable = false)
	private String operatorUserId;

	@Column(name = "target_tenant_id", length = 50, nullable = false)
	private String targetTenantId;

	@Column(name = "reason", length = 500, nullable = false)
	private String reason;

	@Column(name = "status", length = 20, nullable = false)
	@Builder.Default
	private String status = STATUS_ACTIVE;

	@Column(name = "started_at", nullable = false)
	private LocalDateTime startedAt;

	@Column(name = "expires_at", nullable = false)
	private LocalDateTime expiresAt;

	@Column(name = "revoked_at")
	private LocalDateTime revokedAt;

	@Column(name = "revoked_by_user_id", length = 50)
	private String revokedByUserId;

	@CreatedDate
	@Column(name = "create_time", nullable = false, updatable = false)
	private LocalDateTime createTime;

	@LastModifiedDate
	@Column(name = "update_time")
	private LocalDateTime updateTime;

}
