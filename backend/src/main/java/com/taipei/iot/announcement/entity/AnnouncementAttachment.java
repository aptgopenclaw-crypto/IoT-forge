package com.taipei.iot.announcement.entity;

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

/**
 * 公告附件。
 * <p>
 * 實體檔案儲存在 {@code ./uploads/announcement/{announcementId}/...}， 由
 * {@code FileStorageService} 管理；本表僅保存中繼資料與相對路徑。
 */
@Entity
@Table(name = "announcement_attachments")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@EntityListeners({ TenantEntityListener.class, AuditingEntityListener.class })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnnouncementAttachment implements TenantAware {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "tenant_id", nullable = false, length = 50)
	private String tenantId;

	@Column(name = "announcement_id", nullable = false)
	private Long announcementId;

	@Column(name = "file_name", nullable = false, length = 255)
	private String fileName;

	@Column(name = "file_size", nullable = false)
	private Long fileSize;

	@Column(name = "mime_type", nullable = false, length = 150)
	private String mimeType;

	/** FileStorageService 回傳的相對路徑（不對外暴露） */
	@Column(name = "file_path", nullable = false, length = 500)
	private String filePath;

	@Column(name = "created_by", length = 50)
	private String createdBy;

	@CreatedDate
	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@Override
	public String getTenantId() {
		return tenantId;
	}

}
