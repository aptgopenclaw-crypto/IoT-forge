package com.taipei.iot.announcement.dto;

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
@Schema(description = "公告附件回應")
public class AnnouncementAttachmentResponse {

	@Schema(description = "附件 ID")
	private Long id;

	@Schema(description = "所屬公告 ID")
	private Long announcementId;

	@Schema(description = "檔案名稱（含副檔名）")
	private String fileName;

	@Schema(description = "檔案大小（bytes）")
	private Long fileSize;

	@Schema(description = "MIME type")
	private String mimeType;

	@Schema(description = "建立時間")
	private LocalDateTime createdAt;

}
