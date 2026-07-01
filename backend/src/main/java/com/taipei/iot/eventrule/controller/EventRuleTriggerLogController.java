package com.taipei.iot.eventrule.controller;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import com.taipei.iot.common.context.TenantContext;
import com.taipei.iot.common.dto.PageResponse;
import com.taipei.iot.common.response.BaseResponse;
import com.taipei.iot.eventrule.dto.EventRuleTriggerLogResponse;
import com.taipei.iot.eventrule.repository.EventRuleTriggerLogRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 觸發記錄查詢 API（唯讀）。
 */
@RestController
@RequestMapping("/v1/auth/event-rules")
@RequiredArgsConstructor
@Tag(name = "Event Rule Trigger Log", description = "事件規則觸發記錄查詢")
public class EventRuleTriggerLogController {

	private final EventRuleTriggerLogRepository logRepository;

	@GetMapping("/{id}/logs")
	@PreAuthorize("hasAuthority('EVENT_RULE_VIEW')")
	@Operation(summary = "指定規則的觸發記錄", description = "依時間窗分頁，預設最近 7 天")
	public BaseResponse<PageResponse<EventRuleTriggerLogResponse>> logsForRule(@PathVariable Long id,
			@RequestParam(required = false) Instant from, @RequestParam(required = false) Instant to,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
		String tenantId = TenantContext.getCurrentTenantId();
		LocalDateTime[] window = resolveWindow(from, to, 7);
		Page<EventRuleTriggerLogResponse> result = logRepository
			.findByRuleIdInWindow(tenantId, id, window[0], window[1], PageRequest.of(page, size))
			.map(EventRuleTriggerLogResponse::from);
		return BaseResponse.success(toPageResponse(result));
	}

	@GetMapping("/logs")
	@PreAuthorize("hasAuthority('EVENT_RULE_VIEW')")
	@Operation(summary = "全域觸發記錄", description = "可依時間範圍、severity 篩選")
	public BaseResponse<PageResponse<EventRuleTriggerLogResponse>> allLogs(@RequestParam(required = false) Instant from,
			@RequestParam(required = false) Instant to, @RequestParam(required = false) String severity,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
		String tenantId = TenantContext.getCurrentTenantId();
		LocalDateTime[] window = resolveWindow(from, to, 7);
		Page<EventRuleTriggerLogResponse> result = logRepository
			.findByTenantInWindow(tenantId, window[0], window[1], severity, PageRequest.of(page, size))
			.map(EventRuleTriggerLogResponse::from);
		return BaseResponse.success(toPageResponse(result));
	}

	private LocalDateTime[] resolveWindow(Instant from, Instant to, int defaultDays) {
		Instant toInstant = to != null ? to : Instant.now();
		Instant fromInstant = from != null ? from : toInstant.minusSeconds((long) defaultDays * 86400);
		return new LocalDateTime[] { LocalDateTime.ofInstant(fromInstant, ZoneOffset.UTC),
				LocalDateTime.ofInstant(toInstant, ZoneOffset.UTC) };
	}

	private <T> PageResponse<T> toPageResponse(Page<T> p) {
		return PageResponse.<T>builder()
			.content(p.getContent())
			.totalElements(p.getTotalElements())
			.totalPages(p.getTotalPages())
			.page(p.getNumber())
			.size(p.getSize())
			.build();
	}

}
