package com.taipei.iot.audit.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Filter;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_event_log")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@Getter
@Setter
@NoArgsConstructor
public class UserEventLogEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "user_event_log_pk")
	private Long userEventLogPk;

	@Column(name = "tenant_id", length = 50)
	private String tenantId;

	@Column(name = "user_id", nullable = false, length = 50)
	private String userId;

	@Column(name = "username", length = 100)
	private String username;

	@Column(name = "user_label", length = 100)
	private String userLabel;

	@Column(name = "email", length = 200)
	private String email;

	@Column(name = "event_type", nullable = false, length = 50)
	private String eventType;

	@Column(name = "event_desc", length = 50)
	private String eventDesc;

	@Column(name = "api_endpoint", length = 100)
	private String apiEndpoint;

	@Column(name = "payload", length = 2000)
	private String payload;

	@Column(name = "error_code", length = 50)
	private String errorCode;

	@Column(name = "message", length = 50)
	private String message;

	@Column(name = "ip_address", length = 50)
	private String ipAddress;

	@Column(name = "user_agent", length = 500)
	private String userAgent;

	@Column(name = "execution_time")
	private Long executionTime;

	@Column(name = "dept_id")
	private Long deptId;

	/**
	 * [Phase B] SUPER_ADMIN 在 tenant context 下執行時填入其 userId；一般操作為 null。
	 */
	@Column(name = "impersonated_by", length = 50)
	private String impersonatedBy;

	@Column(name = "create_time", nullable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
	private LocalDateTime createTime;

}
