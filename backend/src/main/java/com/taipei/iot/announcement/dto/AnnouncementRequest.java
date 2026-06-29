package com.taipei.iot.announcement.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "公告新增 / 編輯請求")
public class AnnouncementRequest {

	@NotBlank
	@Size(max = 200)
	@Schema(description = "標題（最長 200 字）", example = "系統維護通知", requiredMode = Schema.RequiredMode.REQUIRED)
	private String title;

	@NotBlank
	@Size(max = 50000)
	@Schema(description = "內文（純文字，最長 50000 字）", example = "本系統將於 2026-06-01 凌晨 2:00 進行維護。",
			requiredMode = Schema.RequiredMode.REQUIRED)
	private String content;

	@NotNull
	@Pattern(regexp = "^(DRAFT|PUBLISHED)$", message = "status must be DRAFT or PUBLISHED")
	@Schema(description = "狀態", allowableValues = { "DRAFT", "PUBLISHED" }, requiredMode = Schema.RequiredMode.REQUIRED)
	private String status;

	@NotNull
	@Pattern(regexp = "^(ALL|DEPT)$", message = "scope must be ALL or DEPT")
	@Schema(description = "受眾範圍：ALL=全公司、DEPT=指定部門", allowableValues = { "ALL", "DEPT" },
			requiredMode = Schema.RequiredMode.REQUIRED)
	private String scope;

	@Pattern(regexp = "^(GENERAL|SYSTEM|POLICY|EVENT|MAINTENANCE)$",
			message = "category must be one of GENERAL/SYSTEM/POLICY/EVENT/MAINTENANCE")
	@Schema(description = "分類；省略時 service 端預設為 GENERAL",
			allowableValues = { "GENERAL", "SYSTEM", "POLICY", "EVENT", "MAINTENANCE" }, example = "GENERAL")
	private String category;

	@Schema(description = "目標部門 ID 清單；當 scope=DEPT 時必填", example = "[10, 20]")
	private List<Long> targetDeptIds;

	@Schema(description = "是否置頂", example = "false")
	private Boolean pinned;

	@Schema(description = "置頂順序（數字越小越靠前）；取消置頂會被清為 null", example = "1")
	private Integer pinOrder;

	@Schema(description = "是否需使用者明確確認（需點「我已閱讀並了解」），預設 false", example = "false")
	private Boolean requiresAck;

	@Schema(description = "發佈時間；為 null 表示立即發佈", example = "2026-06-01T09:00:00")
	private LocalDateTime publishAt;

	@Schema(description = "失效時間；為 null 表示永不過期；必須晚於 publishAt", example = "2026-06-30T23:59:59")
	private LocalDateTime expireAt;

	/**
	 * 樂觀鎖版本號。
	 * <p>
	 * 編輯時必須帶上從 GET 取回的 version，否則視為非法請求。 新增時可省略（後端不檢查）。
	 */
	@Schema(description = "樂觀鎖版本號；編輯時必填，新增時可省略", example = "0")
	private Long version;

	/**
	 * 額外語言翻譯（非預設語言 zh-TW）；可為空。
	 * <p>
	 * {@link #title} / {@link #content} 視為預設語言 zh-TW 的內容； 此欄位可帶入 zh-CN / en
	 * 等其他語言的翻譯，service 會寫入 announcement_translations 子表。 若清單內出現 lang_code=zh-TW，會以
	 * {@link #title} / {@link #content} 為準（覆蓋）。
	 */
	@Valid
	@Schema(description = "額外語言翻譯（不含預設語言 zh-TW）")
	private List<AnnouncementTranslationDto> translations;

	/**
	 * 跨欄位驗證：expireAt 必須晚於 publishAt。
	 * <p>
	 * 規則：
	 * <ul>
	 * <li>expireAt 為 null（永不過期）→ 通過</li>
	 * <li>publishAt 為 null（service 端會替換為 now()）→ 與 now() 比較</li>
	 * <li>否則 expireAt 必須嚴格晚於 publishAt</li>
	 * </ul>
	 * 防止惡意客戶端送出 expireAt &lt; publishAt 產生「永遠不可見」的殭屍公告。
	 */
	@JsonIgnore
	@AssertTrue(message = "expireAt 必須晚於 publishAt")
	public boolean isExpireAfterPublish() {
		if (expireAt == null)
			return true;
		LocalDateTime effectivePublishAt = publishAt != null ? publishAt : LocalDateTime.now();
		return expireAt.isAfter(effectivePublishAt);
	}

}
