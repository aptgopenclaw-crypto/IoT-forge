package com.taipei.iot.telemetry.query;

import java.time.Instant;
import java.util.List;

import com.taipei.iot.common.dto.PageResponse;
import com.taipei.iot.common.response.BaseResponse;
import com.taipei.iot.telemetry.query.dto.TelemetryFieldStats;
import com.taipei.iot.telemetry.query.dto.TelemetryLatestResponse;
import com.taipei.iot.telemetry.query.dto.TelemetryPointResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 遙測查詢 REST API：對前端開放單一設備的歷史曲線、最新值與簡單統計。
 *
 * <p>
 * 走既有 {@code /v1/auth/...} 認證鏈與 {@code DEVICE_VIEW} 權限；租戶隔離由 {@link TelemetryDataService}
 * 以當前租戶 ID 明確過濾保證。時間參數接受 ISO-8601 instant（例如 {@code 2026-06-30T00:00:00Z}）。
 */
@RestController
@RequestMapping("/v1/auth/telemetry")
@RequiredArgsConstructor
@Tag(name = "Telemetry Query", description = "遙測歷史 / 最新值 / 統計查詢")
public class TelemetryController {

	private final TelemetryDataService telemetryDataService;

	@GetMapping("/devices/{deviceId}/history")
	@PreAuthorize("hasAuthority('DEVICE_VIEW')")
	@Operation(summary = "設備遙測歷史", description = "依時間窗 [from, to) 分頁查詢，預設最近 24 小時、依時間遞減")
	public BaseResponse<PageResponse<TelemetryPointResponse>> history(@PathVariable Long deviceId,
			@RequestParam(required = false) Instant from, @RequestParam(required = false) Instant to,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "100") int size) {
		Page<TelemetryPointResponse> result = telemetryDataService.history(deviceId, from, to, page, size);
		return BaseResponse.success(toPageResponse(result));
	}

	@GetMapping("/devices/{deviceId}/latest")
	@PreAuthorize("hasAuthority('DEVICE_VIEW')")
	@Operation(summary = "設備最新遙測值", description = "回傳設備最新一筆讀數，查無資料時 body 為 null")
	public BaseResponse<TelemetryLatestResponse> latest(@PathVariable Long deviceId) {
		return BaseResponse.success(telemetryDataService.latest(deviceId));
	}

	@GetMapping("/devices/{deviceId}/stats")
	@PreAuthorize("hasAuthority('DEVICE_VIEW')")
	@Operation(summary = "設備遙測統計", description = "各數值欄位在時間窗內的 min/max/avg；未指定 fields 時自最新讀數推導數值欄位")
	public BaseResponse<List<TelemetryFieldStats>> stats(@PathVariable Long deviceId,
			@RequestParam(required = false) Instant from, @RequestParam(required = false) Instant to,
			@RequestParam(required = false) List<String> fields) {
		return BaseResponse.success(telemetryDataService.stats(deviceId, from, to, fields));
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
