package com.taipei.iot.announcement.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "未讀公告計數回應")
public class UnreadCountResponse {

	@Schema(description = "當前登入使用者尚未讀取的公告數量", example = "3")
	private Integer count;

}
