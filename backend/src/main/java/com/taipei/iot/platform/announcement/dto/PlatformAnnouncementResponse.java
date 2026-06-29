package com.taipei.iot.platform.announcement.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "平台公告回應")
public class PlatformAnnouncementResponse {

	@Schema(description = "公告 ID")
	private Long id;

	@Schema(description = "標題")
	private String title;

	@Schema(description = "內文")
	private String content;

	@Schema(description = "狀態", allowableValues = { "DRAFT", "PUBLISHED" })
	private String status;

	@Schema(description = "分類", allowableValues = { "SYSTEM", "MAINTENANCE", "GENERAL" })
	private String category;

	@Schema(description = "排程發佈時間")
	private LocalDateTime publishAt;

	@Schema(description = "失效時間")
	private LocalDateTime expireAt;

	@Schema(description = "建立者 user ID")
	private String createdBy;

	@Schema(description = "建立者顯示名稱")
	private String createdByName;

	@Schema(description = "建立時間")
	private LocalDateTime createdAt;

	@Schema(description = "最後更新時間")
	private LocalDateTime updatedAt;

}
