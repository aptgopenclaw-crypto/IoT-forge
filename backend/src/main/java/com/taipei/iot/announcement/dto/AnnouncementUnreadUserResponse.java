package com.taipei.iot.announcement.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 未讀公告之使用者（管理端追蹤名單用）。
 * <p>
 * 不回傳敏感欄位（密碼、login 紀錄）；email 僅給管理端參考。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "未讀使用者")
public class AnnouncementUnreadUserResponse {

	@Schema(description = "使用者 ID")
	private String userId;

	@Schema(description = "顯示名稱")
	private String displayName;

	@Schema(description = "Email")
	private String email;

	@Schema(description = "所屬部門 ID")
	private Long deptId;

	@Schema(description = "所屬部門名稱")
	private String deptName;

}
