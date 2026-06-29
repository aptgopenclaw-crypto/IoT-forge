package com.taipei.iot.audit.controller;

import com.taipei.iot.audit.dto.AuditQueryRequest;
import com.taipei.iot.audit.dto.UserEventLogDto;
import com.taipei.iot.audit.enums.AuditCategory;
import com.taipei.iot.audit.enums.AuditEventType;
import com.taipei.iot.audit.service.AuditService;
import com.taipei.iot.audit.annotation.AuditEvent;
import com.taipei.iot.common.annotation.RateLimit;
import com.taipei.iot.common.dto.PageResponse;
import com.taipei.iot.common.response.BaseResponse;
import com.taipei.iot.common.util.PageConversionHelper;
import com.taipei.iot.common.util.SecurityContextUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/v1/auth/audit")
@RequiredArgsConstructor
@Tag(name = "Audit", description = "稽核與使用者操作紀錄查詢 / 匯出")
public class AuditController {

	private final AuditService auditService;

	@GetMapping("/categories")
	@Operation(summary = "取得稽核分類", description = "回傳所有 AuditCategory 的可用分類值")
	public BaseResponse<List<String>> getCategories() {
		List<String> categories = Arrays.stream(AuditCategory.values()).map(AuditCategory::getValue).toList();
		return BaseResponse.success(categories);
	}

	@GetMapping("/user/usage/history/export")
	@PreAuthorize("hasAuthority('AUDIT_LIST')")
	@RateLimit(key = "audit-export", limit = 5, period = 60)
	@AuditEvent(AuditEventType.EXPORT_AUDIT)
	@Operation(summary = "匯出使用者操作歷程", description = "依查詢條件匯出 CSV 或 XLSX 稽核紀錄")
	public void exportUsageHistory(@Valid AuditQueryRequest request, @RequestParam(defaultValue = "csv") String format,
			HttpServletResponse response) throws Exception {

		boolean isAdmin = SecurityContextUtils.hasAnyAuthority("ROLE_SUPER_ADMIN", "ROLE_ADMIN", "ROLE_DEPT_ADMIN");
		List<UserEventLogDto> logs = auditService.queryForExport(request, isAdmin);

		response.setCharacterEncoding("UTF-8");
		if ("xlsx".equalsIgnoreCase(format)) {
			response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
			response.setHeader("Content-Disposition", "attachment; filename=audit-logs.xlsx");
			auditService.exportXlsx(logs, response.getOutputStream());
		}
		else {
			response.setContentType("text/csv; charset=UTF-8");
			response.setHeader("Content-Disposition", "attachment; filename=audit-logs.csv");
			auditService.exportCsv(logs, response.getOutputStream());
		}
	}

	@GetMapping("/user/usage/history")
	@PreAuthorize("hasAuthority('AUDIT_LIST')")
	@Operation(summary = "查詢使用者操作歷程", description = "依條件分頁查詢稽核紀錄，供管理端檢視")
	public BaseResponse<PageResponse<UserEventLogDto>> getUserUsageHistory(@Valid AuditQueryRequest request,
			@RequestParam(defaultValue = "20") int pageSize, @RequestParam(defaultValue = "0") int page) {

		boolean isAdmin = SecurityContextUtils.hasAnyAuthority("ROLE_SUPER_ADMIN", "ROLE_ADMIN", "ROLE_DEPT_ADMIN");

		Page<UserEventLogDto> result = auditService.getUserUsageHistory(request, isAdmin,
				PageRequest.of(page, pageSize));
		return BaseResponse.success(PageConversionHelper.from(result));
	}

	@GetMapping("/user/login/my")
	@Operation(summary = "查詢我的登入紀錄", description = "回傳目前登入者的登入相關稽核紀錄，可依事件類型與時間範圍過濾")
	public BaseResponse<PageResponse<UserEventLogDto>> getMyLoginLog(@RequestParam(required = false) String eventType,
			@RequestParam(required = false) String startTimestamp, @RequestParam(required = false) String endTimestamp,
			@RequestParam(required = false, defaultValue = "DESC") String sort,
			@RequestParam(defaultValue = "20") int pageSize, @RequestParam(defaultValue = "0") int page) {

		LocalDateTime start = startTimestamp != null && !startTimestamp.isBlank()
				? OffsetDateTime.parse(startTimestamp).atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime()
				: null;
		LocalDateTime end = endTimestamp != null && !endTimestamp.isBlank()
				? OffsetDateTime.parse(endTimestamp).atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime() : null;

		Sort.Direction direction = "ASC".equalsIgnoreCase(sort) ? Sort.Direction.ASC : Sort.Direction.DESC;

		Page<UserEventLogDto> result = auditService.getMyEventLogs(eventType, start, end,
				PageRequest.of(page, pageSize, Sort.by(direction, "createTime")));
		return BaseResponse.success(PageConversionHelper.from(result));
	}

}
