package com.taipei.iot.vms.entity;

import com.taipei.iot.common.tenant.TenantAware;
import com.taipei.iot.common.tenant.TenantEntityListener;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Filter;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "vms_stream_log")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@EntityListeners({ TenantEntityListener.class, AuditingEntityListener.class })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VmsStreamLogEntity implements TenantAware {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "tenant_id", nullable = false)
	private String tenantId;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Column(name = "camera_id", nullable = false)
	private Long cameraId;

	@Column(name = "stream_type", nullable = false, length = 10)
	private String streamType;

	@Column(name = "session_token", nullable = false, length = 36)
	private String sessionToken;

	@Column(name = "started_at", nullable = false)
	private LocalDateTime startedAt;

	@Column(name = "ended_at")
	private LocalDateTime endedAt;

	@Column(name = "duration_seconds")
	private Integer durationSeconds;

	@Column(name = "playback_start_time")
	private LocalDateTime playbackStartTime;

	@Column(name = "playback_end_time")
	private LocalDateTime playbackEndTime;

	@Override
	public String getTenantId() {
		return tenantId;
	}

	@Override
	public void setTenantId(String tenantId) {
		this.tenantId = tenantId;
	}

}
