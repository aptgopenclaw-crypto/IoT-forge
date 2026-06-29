package com.taipei.iot.platform.announcement.entity;

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
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 平台級公告（跨場域），由 super_admin 管理。
 * <p>
 * 不實作 {@code TenantAware}，無 tenant 過濾。
 */
@Entity
@Table(name = "platform_announcements")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlatformAnnouncement {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "title", nullable = false, length = 200)
	private String title;

	@Column(name = "content", nullable = false, columnDefinition = "TEXT")
	private String content;

	@Column(name = "content_text", columnDefinition = "TEXT")
	private String contentText;

	@Builder.Default
	@Column(name = "status", nullable = false, length = 20)
	private String status = "DRAFT";

	@Builder.Default
	@Column(name = "category", nullable = false, length = 20)
	private String category = "SYSTEM";

	@Column(name = "publish_at")
	private LocalDateTime publishAt;

	@Column(name = "expire_at")
	private LocalDateTime expireAt;

	@Column(name = "created_by", length = 50)
	private String createdBy;

	@Column(name = "created_by_name", length = 100)
	private String createdByName;

	@CreatedDate
	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@LastModifiedDate
	@Column(name = "updated_at")
	private LocalDateTime updatedAt;

}
