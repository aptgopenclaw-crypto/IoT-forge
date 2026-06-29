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
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Filter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "announcements")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@EntityListeners({ TenantEntityListener.class, AuditingEntityListener.class })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Announcement implements TenantAware {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "tenant_id", nullable = false, length = 50)
	private String tenantId;

	@Column(name = "title", nullable = false, length = 200)
	private String title;

	@Column(name = "content", nullable = false, columnDefinition = "TEXT")
	private String content;

	/**
	 * 純文字版本（HTML 標籤剝離後），供 keyword 搜尋使用避免比對到標籤本身。 由 service 層在 create/update 時透過
	 * HtmlSanitizerService 從 sanitized content 萃取後寫入。
	 */
	@Column(name = "content_text", columnDefinition = "TEXT")
	private String contentText;

	@Column(name = "status", nullable = false, length = 20)
	private String status;

	@Column(name = "scope", nullable = false, length = 20)
	private String scope;

	/**
	 * 公告分類，預設 GENERAL。
	 * <p>
	 * 對應 {@link AnnouncementCategory}，由 service 層在 create/update 時保證非 null。
	 */
	@Builder.Default
	@Column(name = "category", nullable = false, length = 20)
	private String category = "GENERAL";

	@Column(name = "pinned", nullable = false)
	private Boolean pinned;

	/**
	 * 置頂順序（數字越小越靠前）。
	 * <p>
	 * 僅在 pinned=true 時有意義；取消置頂時 service 層會設回 NULL。 排序：ORDER BY pinned DESC, pin_order ASC
	 * NULLS LAST, publish_at DESC。
	 */
	@Column(name = "pin_order")
	private Integer pinOrder;

	/**
	 * 是否屬「需確認」類公告（例：員工守則修訂、重要政策公告）。
	 * <p>
	 * true 時使用者需明確點「我已閱讀並了解」才會寫入 announcement_reads； 管理端並會顯示「已讀比例」與「未讀名單」。預設 false
	 * 以保持舊有行為。
	 */
	@Builder.Default
	@Column(name = "requires_ack", nullable = false)
	private Boolean requiresAck = false;

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

	/**
	 * Optimistic locking version. Auto-managed by Hibernate.
	 * <p>
	 * 新建時若為 null，Hibernate 會初始化為 0；每次 UPDATE 自動 +1， 並在 WHERE 子句加入 version 比對，防止併發編輯互相覆蓋。
	 */
	@Version
	@Column(name = "version", nullable = false)
	private Long version;

	@Override
	public String getTenantId() {
		return tenantId;
	}

	@Override
	public void setTenantId(String tenantId) {
		this.tenantId = tenantId;
	}

}
