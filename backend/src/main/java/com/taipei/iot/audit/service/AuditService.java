package com.taipei.iot.audit.service;

import com.taipei.iot.audit.dto.AuditQueryRequest;
import com.taipei.iot.audit.dto.UserEventLogDto;
import com.taipei.iot.audit.entity.UserEventLogEntity;
import com.taipei.iot.audit.repository.UserEventLogRepository;
import com.taipei.iot.auth.entity.UserEntity;
import com.taipei.iot.common.util.SecurityContextUtils;
import com.taipei.iot.dept.service.DataScopeHelper;
import com.taipei.iot.tenant.TenantContext;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Subquery;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuditService {

	private final UserEventLogRepository userEventLogRepository;

	private final DataScopeHelper dataScopeHelper;

	/** 允許的排序欄位白名單 — 防止任意屬性注入 */
	static final Set<String> ALLOWED_SORT_FIELDS = Set.of("createTime", "username", "userLabel", "eventType",
			"eventDesc", "ipAddress", "executionTime");

	/**
	 * 操作紀錄查詢（Audit History）。
	 * <p>
	 * 使用 SYSTEM context 繞過 Hibernate @Filter，由 Specification 手動過濾 tenantId + DataScope。
	 * </p>
	 * <ul>
	 * <li>tenantId 嚴格等於當前場域，排除 tenantId 為 null 的跨階段事件</li>
	 * <li>排除所有 SUPER_ADMIN 的操作紀錄，避免出現在場域稽核畫面</li>
	 * <li>isAdmin = false（DEPT_USER）→ 只看自己的紀錄</li>
	 * <li>isAdmin = true（ADMIN / DEPT_ADMIN）→ 依 DataScope 看部門範圍內的紀錄</li>
	 * </ul>
	 */
	public Page<UserEventLogDto> getUserUsageHistory(AuditQueryRequest request, boolean isAdmin, Pageable pageable) {
		String currentUserId = SecurityContextUtils.getCurrentUserId();
		String currentTenantId = TenantContext.getCurrentTenantId();

		Specification<UserEventLogEntity> spec = (root, query, cb) -> {
			List<Predicate> predicates = new ArrayList<>();

			// 手動租戶過濾：嚴格限制為當前租戶，不含 tenantId 為空的跨階段事件
			if (currentTenantId != null && !"SYSTEM".equals(currentTenantId)) {
				predicates.add(cb.equal(root.get("tenantId"), currentTenantId));
			}

			// 排除 SUPER_ADMIN 的操作紀錄：不應出現在各場域的稽核畫面中
			Subquery<String> superAdminIds = query.subquery(String.class);
			jakarta.persistence.criteria.Root<UserEntity> userRoot = superAdminIds.from(UserEntity.class);
			superAdminIds.select(userRoot.get("userId")).where(cb.isTrue(userRoot.get("isSuperAdmin")));
			predicates.add(cb.not(root.get("userId").in(superAdminIds)));

			if (!isAdmin) {
				// DEPT_USER：只能看自己的紀錄
				predicates.add(cb.equal(root.get("userId"), currentUserId));
			}
			else {
				// 管理角色：依 DataScope 過濾部門
				List<Long> visibleDeptIds = dataScopeHelper.getVisibleDeptIds();
				if (!visibleDeptIds.isEmpty()) {
					// 僅顯示可見部門的紀錄；deptId=null 的紀錄（如登入失敗/跨場域操作）
					// 只有 ALL scope 才能看到
					predicates.add(root.get("deptId").in(visibleDeptIds));
				}
				// visibleDeptIds 為空 = ALL scope，不限制（含 deptId=null）
			}

			// 事件類別過濾
			if (request.getEventDesc() != null && !request.getEventDesc().isBlank()) {
				predicates.add(cb.equal(root.get("eventDesc"), request.getEventDesc()));
			}

			// 使用者關鍵字搜尋
			if (request.getUserName() != null && !request.getUserName().isBlank()) {
				String pattern = "%" + request.getUserName().toLowerCase() + "%";
				predicates.add(cb.or(cb.like(cb.lower(root.get("username")), pattern),
						cb.like(cb.lower(root.get("userLabel")), pattern)));
			}

			// 時間範圍
			if (request.getStartTimestamp() != null && !request.getStartTimestamp().isBlank()) {
				LocalDateTime startTime = OffsetDateTime.parse(request.getStartTimestamp())
					.atZoneSameInstant(ZoneId.systemDefault())
					.toLocalDateTime();
				predicates.add(cb.greaterThanOrEqualTo(root.get("createTime"), startTime));
			}
			if (request.getEndTimestamp() != null && !request.getEndTimestamp().isBlank()) {
				LocalDateTime endTime = OffsetDateTime.parse(request.getEndTimestamp())
					.atZoneSameInstant(ZoneId.systemDefault())
					.toLocalDateTime();
				predicates.add(cb.lessThanOrEqualTo(root.get("createTime"), endTime));
			}

			return predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(new Predicate[0]));
		};

		String sortBy = request.getSortBy() != null ? request.getSortBy() : "createTime";
		if (!ALLOWED_SORT_FIELDS.contains(sortBy)) {
			sortBy = "createTime";
		}
		Sort.Direction direction = "ASC".equalsIgnoreCase(request.getSort()) ? Sort.Direction.ASC : Sort.Direction.DESC;
		Pageable sorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by(direction, sortBy));

		// 使用 SYSTEM context 繞過 @Filter，由 Specification 自行處理 tenantId 過濾
		// [Tenant v2 T-13] 改用 TenantContext.runInSystemContext 集中還原邏輯。
		return TenantContext.runInSystemContext(() -> userEventLogRepository.findAll(spec, sorted).map(this::toDto));
	}

	public Page<UserEventLogDto> getMyEventLogs(String eventType, LocalDateTime start, LocalDateTime end,
			Pageable pageable) {
		String currentUserId = SecurityContextUtils.getCurrentUserId();
		String currentTenantId = TenantContext.getCurrentTenantId();

		Specification<UserEventLogEntity> spec = (root, query, cb) -> {
			List<Predicate> predicates = new ArrayList<>();
			predicates.add(cb.equal(root.get("userId"), currentUserId));
			// Defense-in-depth: explicit tenantId filter in case @Filter is bypassed
			if (currentTenantId != null) {
				predicates.add(cb.equal(root.get("tenantId"), currentTenantId));
			}

			if (eventType != null && !eventType.isBlank()) {
				predicates.add(cb.equal(root.get("eventType"), eventType));
			}
			if (start != null) {
				predicates.add(cb.greaterThanOrEqualTo(root.get("createTime"), start));
			}
			if (end != null) {
				predicates.add(cb.lessThanOrEqualTo(root.get("createTime"), end));
			}
			return cb.and(predicates.toArray(new Predicate[0]));
		};

		return userEventLogRepository.findAll(spec, pageable).map(this::toDto);
	}

	// ─── Export ────────────────────────────────────────────────────────────

	private static final String[] EXPORT_HEADERS = { "帳號", "使用者", "事件類型", "事件描述", "API", "結果碼", "IP", "耗時(ms)", "時間" };

	private static final DateTimeFormatter EXPORT_DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

	private static final int EXPORT_MAX_ROWS = 5000;

	/**
	 * 查詢匯出用的完整資料（上限 {@value EXPORT_MAX_ROWS} 筆）。 遵循與 getUserUsageHistory 相同的 tenant +
	 * DataScope 過濾邏輯。
	 */
	public List<UserEventLogDto> queryForExport(AuditQueryRequest request, boolean isAdmin) {
		Pageable exportPage = PageRequest.of(0, EXPORT_MAX_ROWS, Sort.by(Sort.Direction.DESC, "createTime"));
		return getUserUsageHistory(request, isAdmin, exportPage).getContent();
	}

	public void exportCsv(List<UserEventLogDto> logs, OutputStream out) throws IOException {
		try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8))) {
			pw.print('\uFEFF'); // BOM
			pw.println(String.join(",", EXPORT_HEADERS));
			for (UserEventLogDto log : logs) {
				pw.println(String.join(",", csvEscape(log.getUsername()), csvEscape(log.getUserLabel()),
						csvEscape(log.getEventType()), csvEscape(log.getEventDesc()), csvEscape(log.getApiEndpoint()),
						csvEscape(log.getErrorCode()), csvEscape(log.getIpAddress()),
						log.getExecutionTime() != null ? String.valueOf(log.getExecutionTime()) : "",
						log.getCreateTime() != null ? log.getCreateTime().format(EXPORT_DT_FMT) : ""));
			}
		}
	}

	public void exportXlsx(List<UserEventLogDto> logs, OutputStream out) throws IOException {
		// SXSSFWorkbook: streaming mode, keeps only 100 rows in memory at a time
		try (SXSSFWorkbook wb = new SXSSFWorkbook(100)) {
			Sheet sheet = wb.createSheet("稽核日誌");

			// Header style
			CellStyle headerStyle = wb.createCellStyle();
			Font headerFont = wb.createFont();
			headerFont.setBold(true);
			headerStyle.setFont(headerFont);
			headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
			headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

			Row headerRow = sheet.createRow(0);
			for (int i = 0; i < EXPORT_HEADERS.length; i++) {
				Cell cell = headerRow.createCell(i);
				cell.setCellValue(EXPORT_HEADERS[i]);
				cell.setCellStyle(headerStyle);
			}

			for (int i = 0; i < logs.size(); i++) {
				UserEventLogDto log = logs.get(i);
				Row row = sheet.createRow(i + 1);
				row.createCell(0).setCellValue(val(log.getUsername()));
				row.createCell(1).setCellValue(val(log.getUserLabel()));
				row.createCell(2).setCellValue(val(log.getEventType()));
				row.createCell(3).setCellValue(val(log.getEventDesc()));
				row.createCell(4).setCellValue(val(log.getApiEndpoint()));
				row.createCell(5).setCellValue(val(log.getErrorCode()));
				row.createCell(6).setCellValue(val(log.getIpAddress()));
				if (log.getExecutionTime() != null) {
					row.createCell(7).setCellValue(log.getExecutionTime());
				}
				if (log.getCreateTime() != null) {
					row.createCell(8).setCellValue(log.getCreateTime().format(EXPORT_DT_FMT));
				}
			}

			wb.write(out);
			wb.dispose(); // clean up temporary files
		}
	}

	static String csvEscape(String value) {
		if (value == null)
			return "";
		// Neutralize CSV formula injection: prefix dangerous leading chars with single
		// quote
		if (!value.isEmpty() && "=+-@\t\r".indexOf(value.charAt(0)) >= 0) {
			value = "'" + value;
		}
		if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("'")) {
			return "\"" + value.replace("\"", "\"\"") + "\"";
		}
		return value;
	}

	private static String val(String s) {
		return s != null ? s : "";
	}

	// ─── Mapping ─────────────────────────────────────────────────────────

	private UserEventLogDto toDto(UserEventLogEntity entity) {
		return UserEventLogDto.builder()
			.userEventLogPk(entity.getUserEventLogPk())
			.userId(entity.getUserId())
			.username(entity.getUsername())
			.userLabel(entity.getUserLabel())
			.email(entity.getEmail())
			.eventType(entity.getEventType())
			.eventDesc(entity.getEventDesc())
			.apiEndpoint(entity.getApiEndpoint())
			.payload(entity.getPayload())
			.errorCode(entity.getErrorCode())
			.message(entity.getMessage())
			.ipAddress(entity.getIpAddress())
			.userAgent(entity.getUserAgent())
			.executionTime(entity.getExecutionTime())
			.deptId(entity.getDeptId())
			.impersonatedBy(entity.getImpersonatedBy())
			.createTime(entity.getCreateTime())
			.build();
	}

}
