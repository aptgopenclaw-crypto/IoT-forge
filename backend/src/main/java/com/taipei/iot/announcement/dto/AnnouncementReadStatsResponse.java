package com.taipei.iot.announcement.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 公告已讀統計（管理端）。
 * <p>
 * readRatio 由 service 端計算：totalAudience &gt; 0 時為 readCount/totalAudience（四捨五入到 4 位小數），
 * 否則為 0。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "公告已讀統計")
public class AnnouncementReadStatsResponse {

	@Schema(description = "公告 ID")
	private Long announcementId;

	@Schema(description = "是否為需確認公告")
	private Boolean requiresAck;

	@Schema(description = "目標受眾總人數（依 scope 計算：ALL=租戶內啟用使用者數；DEPT=目標部門啟用使用者數）")
	private Long totalAudience;

	@Schema(description = "已讀人數（限受眾範圍內）")
	private Long readCount;

	@Schema(description = "未讀人數 = totalAudience - readCount")
	private Long unreadCount;

	@Schema(description = "已讀比例（0.0000 ~ 1.0000）；totalAudience=0 時為 0")
	private BigDecimal readRatio;

}
