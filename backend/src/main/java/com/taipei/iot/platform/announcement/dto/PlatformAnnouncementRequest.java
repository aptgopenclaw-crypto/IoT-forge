package com.taipei.iot.platform.announcement.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
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
@Schema(description = "平台公告新增 / 編輯請求")
public class PlatformAnnouncementRequest {

	@NotBlank
	@Size(max = 200)
	@Schema(description = "標題", example = "系統維護通知")
	private String title;

	@NotBlank
	@Size(max = 50000)
	@Schema(description = "內文（HTML）", example = "本系統將於 2026-06-01 凌晨 2:00 進行維護。")
	private String content;

	@NotBlank
	@Pattern(regexp = "^(DRAFT|PUBLISHED)$", message = "status must be DRAFT or PUBLISHED")
	@Schema(description = "狀態", allowableValues = { "DRAFT", "PUBLISHED" })
	private String status;

	@Pattern(regexp = "^(SYSTEM|MAINTENANCE|GENERAL)$", message = "category must be one of SYSTEM/MAINTENANCE/GENERAL")
	@Schema(description = "分類；省略時預設 SYSTEM", allowableValues = { "SYSTEM", "MAINTENANCE", "GENERAL" })
	private String category;

	@Schema(description = "排程發佈時間；null 表示立即發佈")
	private LocalDateTime publishAt;

	@Schema(description = "失效時間；null 表示永不過期")
	private LocalDateTime expireAt;

}
