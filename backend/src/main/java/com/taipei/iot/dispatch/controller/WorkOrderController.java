package com.taipei.iot.dispatch.controller;

import com.taipei.iot.common.dto.PageResponse;
import com.taipei.iot.common.response.BaseResponse;
import com.taipei.iot.common.util.SecurityContextUtils;
import com.taipei.iot.dispatch.dto.WorkOrderRequest;
import com.taipei.iot.dispatch.dto.WorkOrderResponse;
import com.taipei.iot.dispatch.dto.WorkOrderResponse.WorkOrderLogEntry;
import com.taipei.iot.dispatch.enums.WorkOrderSourceType;
import com.taipei.iot.dispatch.enums.WorkOrderStatus;
import com.taipei.iot.dispatch.service.WorkOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/v1/auth/work-orders")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "features.dispatch.enabled", havingValue = "true", matchIfMissing = false)
@Tag(name = "WorkOrder", description = "工單管理 — 通報/派工/到場/維修/覆核/結案 完整狀態機")
public class WorkOrderController {

	private final WorkOrderService workOrderService;

	@GetMapping
	@PreAuthorize("hasAuthority('WORK_ORDER_VIEW')")
	@Operation(summary = "工單分頁查詢")
	public BaseResponse<PageResponse<WorkOrderResponse>> list(@RequestParam(required = false) Long deviceId,
			@RequestParam(required = false) WorkOrderStatus status,
			@RequestParam(required = false) WorkOrderSourceType sourceType,
			@RequestParam(required = false) String keyword, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		Page<WorkOrderResponse> result = workOrderService.list(deviceId, status, sourceType, keyword,
				PageRequest.of(page, size));
		return BaseResponse.success(toPageResponse(result));
	}

	@GetMapping("/my-tasks")
	@PreAuthorize("hasAuthority('WORK_ORDER_VIEW')")
	@Operation(summary = "我的施工任務", description = "指派給當前使用者的施工單（ASSIGNED / IN_PROGRESS）")
	public BaseResponse<PageResponse<WorkOrderResponse>> myTasks(@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		String userId = SecurityContextUtils.requireCurrentUserIdStrict();
		Page<WorkOrderResponse> result = workOrderService.listMyTasks(userId, PageRequest.of(page, size));
		return BaseResponse.success(toPageResponse(result));
	}

	@GetMapping("/{id}")
	@PreAuthorize("hasAuthority('WORK_ORDER_VIEW')")
	@Operation(summary = "取得工單明細", description = "含完整 Timeline 操作紀錄")
	public BaseResponse<WorkOrderResponse> getById(@PathVariable Long id) {
		return BaseResponse.success(workOrderService.getById(id));
	}

	@PostMapping
	@PreAuthorize("hasAuthority('WORK_ORDER_MANAGE')")
	@Operation(summary = "建立工單", description = "從通報建立工單，自動凍結 location_snapshot")
	public BaseResponse<WorkOrderResponse> create(@Valid @RequestBody WorkOrderRequest request) {
		return BaseResponse.success(workOrderService.create(request));
	}

	@PostMapping("/{id}/assign")
	@PreAuthorize("hasAuthority('WORK_ORDER_DISPATCH')")
	@Operation(summary = "指派工單")
	public BaseResponse<WorkOrderResponse> assign(@PathVariable Long id, @RequestBody Map<String, String> body) {
		String currentUserId = SecurityContextUtils.requireCurrentUserIdStrict();
		return BaseResponse.success(workOrderService.assign(id, body.get("assigneeUserId"), currentUserId));
	}

	@PostMapping("/{id}/start")
	@PreAuthorize("hasAuthority('WORK_ORDER_EXECUTE')")
	@Operation(summary = "到場打卡", description = "技師 GPS 打卡開始維修")
	public BaseResponse<WorkOrderResponse> startWork(@PathVariable Long id,
			@RequestBody(required = false) Map<String, BigDecimal> gps) {
		BigDecimal lat = gps != null ? gps.get("latitude") : null;
		BigDecimal lng = gps != null ? gps.get("longitude") : null;
		String userId = SecurityContextUtils.requireCurrentUserIdStrict();
		return BaseResponse.success(workOrderService.startWork(id, lat, lng, userId));
	}

	@PostMapping("/{id}/complete")
	@PreAuthorize("hasAuthority('WORK_ORDER_EXECUTE')")
	@Operation(summary = "完成維修", description = "技師填寫完成紀錄，進入覆核狀態")
	public BaseResponse<WorkOrderResponse> complete(@PathVariable Long id, @RequestBody Map<String, Object> body) {
		return BaseResponse.success(workOrderService.complete(id, (String) body.get("remark"),
				(String) body.get("faultCause"), (Integer) body.get("repairCost")));
	}

	@PostMapping("/{id}/approve")
	@PreAuthorize("hasAuthority('WORK_ORDER_APPROVE')")
	@Operation(summary = "核准工單")
	public BaseResponse<WorkOrderResponse> approve(@PathVariable Long id, @RequestBody Map<String, String> body) {
		return BaseResponse.success(workOrderService.approve(id, body.get("reviewerId")));
	}

	@PostMapping("/{id}/reject")
	@PreAuthorize("hasAnyAuthority('WORK_ORDER_DISPATCH', 'WORK_ORDER_APPROVE')")
	@Operation(summary = "駁回工單")
	public BaseResponse<WorkOrderResponse> reject(@PathVariable Long id, @RequestBody Map<String, String> body) {
		return BaseResponse.success(workOrderService.reject(id, body.get("reviewerId"), body.get("reason")));
	}

	@PostMapping("/{id}/close")
	@PreAuthorize("hasAuthority('WORK_ORDER_MANAGE')")
	@Operation(summary = "結案")
	public BaseResponse<Void> close(@PathVariable Long id, @RequestBody Map<String, String> body) {
		workOrderService.close(id, body.get("closedBy"));
		return BaseResponse.success(null);
	}

	@GetMapping("/{id}/timeline")
	@PreAuthorize("hasAuthority('WORK_ORDER_VIEW')")
	@Operation(summary = "工單 Timeline", description = "回傳工單所有操作紀錄（含 GPS 打卡）")
	public BaseResponse<List<WorkOrderLogEntry>> getTimeline(@PathVariable Long id) {
		return BaseResponse.success(workOrderService.getTimeline(id));
	}

	private <T> PageResponse<T> toPageResponse(Page<T> page) {
		return PageResponse.<T>builder()
			.content(page.getContent())
			.totalElements(page.getTotalElements())
			.totalPages(page.getTotalPages())
			.page(page.getNumber())
			.size(page.getSize())
			.build();
	}

}
