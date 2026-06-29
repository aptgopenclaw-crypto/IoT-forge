package com.taipei.iot.platform.announcement.controller;

import com.taipei.iot.audit.annotation.AuditEvent;
import com.taipei.iot.audit.enums.AuditEventType;
import com.taipei.iot.common.dto.PageResponse;
import com.taipei.iot.common.response.BaseResponse;
import com.taipei.iot.platform.announcement.dto.PlatformAnnouncementRequest;
import com.taipei.iot.platform.announcement.dto.PlatformAnnouncementResponse;
import com.taipei.iot.platform.announcement.service.PlatformAnnouncementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 平台公告管理 — super_admin 專用（PLATFORM scope）。
 * <p>
 * 路徑 {@code /v1/platform/announcements}，由 {@code ScopeEnforcementFilter} 保證只有 PLATFORM
 * token 可存取。
 */
@RestController
@RequestMapping("/v1/platform/announcements")
@RequiredArgsConstructor
@Validated
@PreAuthorize("hasAuthority('PLATFORM_ANNOUNCEMENT_MANAGE')")
@Tag(name = "PlatformAnnouncement", description = "平台公告管理（super_admin）")
public class PlatformAnnouncementController {

	private final PlatformAnnouncementService service;

	@GetMapping
	@Operation(summary = "平台公告列表（管理端）")
	public BaseResponse<PageResponse<PlatformAnnouncementResponse>> list(
			@RequestParam(required = false) String statusFilter, @RequestParam(required = false) String category,
			@RequestParam(required = false) String keyword, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "10") int size) {
		return BaseResponse.success(service.listAdmin(statusFilter, category, keyword, page, size));
	}

	@GetMapping("/{id}")
	@Operation(summary = "平台公告詳情")
	public BaseResponse<PlatformAnnouncementResponse> getById(@PathVariable Long id) {
		return BaseResponse.success(service.getById(id));
	}

	@PostMapping
	@AuditEvent(AuditEventType.CREATE_PLATFORM_ANNOUNCEMENT)
	@Operation(summary = "新增平台公告")
	public BaseResponse<PlatformAnnouncementResponse> create(@Valid @RequestBody PlatformAnnouncementRequest request) {
		return BaseResponse.success(service.create(request));
	}

	@PutMapping("/{id}")
	@AuditEvent(AuditEventType.UPDATE_PLATFORM_ANNOUNCEMENT)
	@Operation(summary = "編輯平台公告")
	public BaseResponse<PlatformAnnouncementResponse> update(@PathVariable Long id,
			@Valid @RequestBody PlatformAnnouncementRequest request) {
		return BaseResponse.success(service.update(id, request));
	}

	@DeleteMapping("/{id}")
	@AuditEvent(AuditEventType.DELETE_PLATFORM_ANNOUNCEMENT)
	@Operation(summary = "刪除平台公告")
	public BaseResponse<Void> delete(@PathVariable Long id) {
		service.delete(id);
		return BaseResponse.success(null);
	}

}
