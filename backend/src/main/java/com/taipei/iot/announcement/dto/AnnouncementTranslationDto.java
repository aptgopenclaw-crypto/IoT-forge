package com.taipei.iot.announcement.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 公告翻譯 DTO（單一語言的 title + content）。
 * <p>
 * 用於 {@link AnnouncementRequest#getTranslations()} 與
 * {@link AnnouncementResponse#getTranslations()}。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "公告翻譯（單一語言）")
public class AnnouncementTranslationDto {

	/** IETF BCP-47 標籤：zh-TW / zh-CN / en。 */
	@NotBlank
	@Pattern(regexp = "^[a-zA-Z]{2,3}(-[A-Za-z0-9]{2,8})?$",
			message = "langCode must be a valid BCP-47 tag, e.g. zh-TW, en")
	@Size(max = 10)
	@Schema(description = "語言代碼（BCP-47）", example = "zh-TW", requiredMode = Schema.RequiredMode.REQUIRED)
	private String langCode;

	@NotBlank
	@Size(max = 200)
	@Schema(description = "標題", requiredMode = Schema.RequiredMode.REQUIRED)
	private String title;

	@NotBlank
	@Size(max = 50000)
	@Schema(description = "內文", requiredMode = Schema.RequiredMode.REQUIRED)
	private String content;

}
