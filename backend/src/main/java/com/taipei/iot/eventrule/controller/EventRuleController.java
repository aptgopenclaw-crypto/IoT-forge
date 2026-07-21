package com.taipei.iot.eventrule.controller;

import com.taipei.iot.common.dto.PageResponse;
import com.taipei.iot.common.response.BaseResponse;
import com.taipei.iot.eventrule.dto.EventRuleRequest;
import com.taipei.iot.eventrule.dto.EventRuleResponse;
import com.taipei.iot.eventrule.dto.ToggleEnabledRequest;
import com.taipei.iot.eventrule.service.EventRuleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 事件規則 CRUD API。
 *
 * <p>
 * 走既有 {@code /v1/auth/...} 鏈（JWT + scope），使用 {@code EVENT_RULE_VIEW} /
 * {@code EVENT_RULE_MANAGE} 權限。
 */
@RestController
@RequestMapping("/v1/auth/event-rules")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "features.eventrule.enabled", havingValue = "true", matchIfMissing = false)
@Tag(name = "Event Rule", description = "事件規則 CRUD + 啟用/停用")
public class EventRuleController {

	private final EventRuleService eventRuleService;

	@GetMapping
	@PreAuthorize("hasAuthority('EVENT_RULE_VIEW')")
	@Operation(summary = "規則列表", description = "可依 deviceType / enabled 篩選，分頁")
	public BaseResponse<PageResponse<EventRuleResponse>> list(@RequestParam(required = false) String deviceType,
			@RequestParam(required = false) Boolean enabled, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		Page<EventRuleResponse> result = eventRuleService.list(deviceType, enabled, PageRequest.of(page, size));
		return BaseResponse.success(toPageResponse(result));
	}

	@GetMapping("/{id}")
	@PreAuthorize("hasAuthority('EVENT_RULE_VIEW')")
	@Operation(summary = "取得單一規則")
	public BaseResponse<EventRuleResponse> getById(@PathVariable Long id) {
		return BaseResponse.success(eventRuleService.getById(id));
	}

	@PostMapping
	@PreAuthorize("hasAuthority('EVENT_RULE_MANAGE')")
	@Operation(summary = "建立規則", description = "condition 欄位經 schema.telemetry.properties 白名單驗證")
	public BaseResponse<EventRuleResponse> create(@Valid @RequestBody EventRuleRequest req) {
		return BaseResponse.success(eventRuleService.create(req));
	}

	@PutMapping("/{id}")
	@PreAuthorize("hasAuthority('EVENT_RULE_MANAGE')")
	@Operation(summary = "更新規則", description = "同時失效規則快取")
	public BaseResponse<EventRuleResponse> update(@PathVariable Long id, @Valid @RequestBody EventRuleRequest req) {
		return BaseResponse.success(eventRuleService.update(id, req));
	}

	@PatchMapping("/{id}/enabled")
	@PreAuthorize("hasAuthority('EVENT_RULE_MANAGE')")
	@Operation(summary = "啟用 / 停用規則")
	public BaseResponse<EventRuleResponse> toggleEnabled(@PathVariable Long id, @RequestBody ToggleEnabledRequest req) {
		return BaseResponse.success(eventRuleService.toggleEnabled(id, req.enabled()));
	}

	@DeleteMapping("/{id}")
	@PreAuthorize("hasAuthority('EVENT_RULE_MANAGE')")
	@Operation(summary = "刪除規則")
	public BaseResponse<Void> delete(@PathVariable Long id) {
		eventRuleService.delete(id);
		return BaseResponse.success(null);
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
