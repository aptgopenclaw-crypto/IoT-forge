package com.taipei.iot.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "change_password_log")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChangePasswordLogEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	@Column(name = "user_id", length = 50, nullable = false)
	private String userId;

	@Column(name = "change_type", length = 50, nullable = false)
	private String changeType;

	@Column(name = "ip_address", length = 50)
	private String ipAddress;

	@Column(name = "create_time", nullable = false, updatable = false)
	private LocalDateTime createTime;

	@jakarta.persistence.PrePersist
	public void prePersist() {
		if (this.createTime == null) {
			this.createTime = LocalDateTime.now();
		}
	}

}
