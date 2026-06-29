package com.taipei.iot.announcement.entity;

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

/**
 * 公告翻譯：對應 {@code announcement_translations} 子表。
 * <p>
 * 主表 {@link Announcement#title} / {@link Announcement#content} 視為預設語言（zh-TW）的快取與
 * fallback； 子表則記錄各語言的對應內容。
 */
@Entity
@Table(name = "announcement_translations")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnnouncementTranslation {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "announcement_id", nullable = false)
	private Long announcementId;

	/** IETF BCP-47 標籤，例如 zh-TW / zh-CN / en。 */
	@Column(name = "lang_code", nullable = false, length = 10)
	private String langCode;

	@Column(name = "title", nullable = false, length = 200)
	private String title;

	@Column(name = "content", nullable = false, columnDefinition = "TEXT")
	private String content;

	@Column(name = "content_text", nullable = false, columnDefinition = "TEXT")
	private String contentText;

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

}
