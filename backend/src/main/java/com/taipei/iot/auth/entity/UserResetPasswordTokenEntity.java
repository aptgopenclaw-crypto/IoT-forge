package com.taipei.iot.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_reset_password_token")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResetPasswordTokenEntity {

	@Id
	@Column(name = "token_id", length = 100)
	private String tokenId;

	@Column(name = "user_id", length = 50, nullable = false)
	private String userId;

	/**
	 * SHA-256 hex digest of the plaintext reset token sent to the user via email. The
	 * plaintext token is never persisted; only this digest is stored so that a database
	 * compromise does not expose usable reset tokens.
	 */
	@Column(name = "token_hash", length = 64, nullable = false, unique = true)
	private String tokenHash;

	@Column(name = "expired_at", nullable = false)
	private LocalDateTime expiredAt;

	@Column(name = "used", nullable = false)
	@Builder.Default
	private Boolean used = false;

	@Column(name = "create_time", nullable = false, updatable = false)
	private LocalDateTime createTime;

	@jakarta.persistence.PrePersist
	public void prePersist() {
		if (this.createTime == null) {
			this.createTime = LocalDateTime.now();
		}
	}

}
