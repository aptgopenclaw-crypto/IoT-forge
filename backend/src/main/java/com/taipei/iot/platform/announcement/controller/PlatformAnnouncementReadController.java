package com.taipei.iot.platform.announcement.controller;

import com.taipei.iot.common.dto.PageResponse;
import com.taipei.iot.common.response.BaseResponse;
import com.taipei.iot.platform.announcement.dto.PlatformAnnouncementResponse;
import com.taipei.iot.platform.announcement.service.PlatformAnnouncementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 租戶端讀取平台公告（唯讀）— 所有登入使用者皆可存取。
 * <p>
 * 路徑 {@code /v1/auth/platform-announcements}，走正常的 TENANT / IMPERSONATION
 * scope，只回傳已發佈且未過期的平台公告。
 */
@RestController
@RequestMapping("/v1/auth/platform-announcements")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
@Tag(name = "PlatformAnnouncementRead", description = "平台公告（租戶端唯讀）")
public class PlatformAnnouncementReadController {

	private final PlatformAnnouncementService service;

	@GetMapping
	@Operation(summary = "已發佈的平台公告列表", description = "回傳租戶端公告欄可見的已發佈且未過期平台公告")
	public BaseResponse<PageResponse<PlatformAnnouncementResponse>> list(
			@RequestParam(required = false) String category, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "10") int size) {
		return BaseResponse.success(service.listPublished(category, page, size));
	}

}
