package com.taipei.iot.auth.entity;

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

@Entity
@Table(name = "roles")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoleEntity {

	@Id
	@Column(name = "role_id", length = 50)
	private String roleId;

	@Column(name = "code", length = 50, nullable = false, unique = true)
	private String code;

	@Column(name = "name", length = 100, nullable = false)
	private String name;

	@Column(name = "description", length = 500)
	private String description;

	@Column(name = "built_in", nullable = false)
	@Builder.Default
	private Boolean builtIn = true;

	@Column(name = "enabled", nullable = false)
	@Builder.Default
	private Boolean enabled = true;

	@Column(name = "data_scope", length = 30)
	@Builder.Default
	private String dataScope = "ALL";

	@CreatedDate
	@Column(name = "create_time", nullable = false, updatable = false)
	private LocalDateTime createTime;

	@LastModifiedDate
	@Column(name = "update_time")
	private LocalDateTime updateTime;

}
