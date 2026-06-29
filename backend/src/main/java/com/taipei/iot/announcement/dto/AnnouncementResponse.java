package com.taipei.iot.announcement.dto;

import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "公告回應")
public class AnnouncementResponse {

	@Schema(description = "公告 ID")
	private Long id;

	@Schema(description = "標題")
	private String title;

	@Schema(description = "內文")
	private String content;

	@Schema(description = "狀態", allowableValues = { "DRAFT", "PUBLISHED" })
	private String status;

	@Schema(description = "受眾範圍", allowableValues = { "ALL", "DEPT" })
	private String scope;

	@Schema(description = "分類", allowableValues = { "GENERAL", "SYSTEM", "POLICY", "EVENT", "MAINTENANCE" })
	private String category;

	@Schema(description = "目標部門 ID 清單")
	private List<Long> targetDeptIds;

	@Schema(description = "目標部門名稱清單（與 targetDeptIds 對應）")
	private List<String> targetDeptNames;

	@Schema(description = "是否置頂")
	private Boolean pinned;

	@Schema(description = "置頂順序（數字越小越靠前）", example = "1")
	private Integer pinOrder;

	@Schema(description = "是否為需確認公告；front-end 揚「我已閱讀並了解」按鈕依據")
	private Boolean requiresAck;

	@Schema(description = "發佈時間")
	private LocalDateTime publishAt;

	@Schema(description = "失效時間；null = 永不過期")
	private LocalDateTime expireAt;

	@Schema(description = "建立者 user ID")
	private String createdBy;

	@Schema(description = "建立者顯示名稱")
	private String createdByName;

	@Schema(description = "建立時間")
	private LocalDateTime createdAt;

	@Schema(description = "最後更新時間")
	private LocalDateTime updatedAt;

	@Schema(description = "當前登入使用者是否已讀")
	private Boolean isRead;

	@Schema(description = "當前登入使用者是否可編輯（僅管理端列表 / 詳情回傳）")
	private Boolean editable;

	@Schema(description = "樂觀鎖版本號；編輯時需原值帶回")
	private Long version;

	@Schema(description = "附件清單（依上傳順序）")
	private List<AnnouncementAttachmentResponse> attachments;

	@Schema(description = "本筆回應 title / content 對應的解析後語言（lang resolution 結果；可能 fallback）", example = "zh-TW")
	private String resolvedLang;

	@Schema(description = "所有語言翻譯（含預設語言 zh-TW）；給管理端編輯使用")
	private List<AnnouncementTranslationDto> translations;

}
