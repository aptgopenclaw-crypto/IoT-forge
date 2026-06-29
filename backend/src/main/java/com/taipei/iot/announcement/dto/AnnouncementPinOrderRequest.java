package com.taipei.iot.announcement.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 拖曳調整置頂順序的請求 body。
 * <p>
 * orderedIds：依新順序由前到後排列的公告 id 清單； 後端會依清單位置 (index+1) 重新寫入 pin_order，並確保僅當下租戶可見的公告被異動。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "重新排序置頂公告請求")
public class AnnouncementPinOrderRequest {

	@Schema(description = "依新順序排列的公告 id 清單（第一個為最前面）", example = "[12, 7, 3]")
	@NotEmpty
	private List<Long> orderedIds;

}
