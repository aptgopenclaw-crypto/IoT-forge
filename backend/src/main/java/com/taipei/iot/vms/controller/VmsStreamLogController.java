package com.taipei.iot.vms.controller;

import com.taipei.iot.common.response.BaseResponse;
import com.taipei.iot.vms.dto.VmsStreamLogDTO;
import com.taipei.iot.vms.service.VmsStreamLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/v1/auth/vms/stream-logs")
@RequiredArgsConstructor
public class VmsStreamLogController {

	private final VmsStreamLogService vmsStreamLogService;

	@GetMapping
	public BaseResponse<Page<VmsStreamLogDTO>> query(@RequestParam(required = false) Long userId,
			@RequestParam(required = false) Long cameraId, @RequestParam(required = false) String streamType,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
			@PageableDefault(size = 20) Pageable pageable) {
		return BaseResponse
			.success(vmsStreamLogService.queryLogs(userId, cameraId, streamType, startDate, endDate, pageable));
	}

}
