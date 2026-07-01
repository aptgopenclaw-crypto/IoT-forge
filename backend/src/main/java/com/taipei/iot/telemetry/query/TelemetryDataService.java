package com.taipei.iot.telemetry.query;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.taipei.iot.common.context.TenantContext;
import com.taipei.iot.common.enums.ErrorCode;
import com.taipei.iot.common.exception.BusinessException;
import com.taipei.iot.telemetry.query.dto.TelemetryFieldStats;
import com.taipei.iot.telemetry.query.dto.TelemetryLatestResponse;
import com.taipei.iot.telemetry.query.dto.TelemetryPointResponse;
import com.taipei.iot.telemetry.storage.TelemetryData;
import com.taipei.iot.telemetry.storage.TelemetryDataRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 遙測查詢服務：對前端開放歷史、最新值與簡單統計。
 *
 * <p>
 * {@code telemetry_data} 無 Hibernate {@code tenantFilter}，因此所有查詢都<strong>明確</strong>以當前
 * {@link TenantContext} 的租戶 ID 過濾，保證租戶隔離（跨租戶查詢只會得到空結果）。
 */
@Service
@RequiredArgsConstructor
public class TelemetryDataService {

	/** 歷史查詢單頁上限，避免大時間窗一次拉回過量資料。 */
	static final int MAX_PAGE_SIZE = 1000;

	/** 預設時間窗（未指定時往前 24 小時）。 */
	private static final Duration DEFAULT_WINDOW = Duration.ofHours(24);

	/**
	 * 統計查詢 SQL：先取出量測欄位文字，過濾出可解析為數值的樣本後再轉型，避免非數值樣本造成整批 cast 失敗。 允許使用
	 * {@link JdbcTemplate}（{@code ForbiddenNativeQueryArchTest} 僅禁止
	 * {@code EntityManager.createNativeQuery}）。
	 */
	private static final String STATS_SQL = """
			SELECT COUNT(*) AS cnt, MIN(v) AS mn, MAX(v) AS mx, AVG(v) AS av
			FROM (
			  SELECT (txt)::numeric AS v
			  FROM (
			    SELECT payload ->> ? AS txt
			    FROM telemetry_data
			    WHERE tenant_id = ? AND device_id = ? AND ts >= ? AND ts < ?
			  ) a
			  WHERE txt ~ '^-?[0-9]+(\\.[0-9]+)?$'
			) b
			""";

	private final TelemetryDataRepository repository;

	private final JdbcTemplate jdbcTemplate;

	/** 設備歷史讀數（時間窗 [from, to)），依時間遞減分頁。 */
	@Transactional(readOnly = true)
	public Page<TelemetryPointResponse> history(Long deviceId, Instant from, Instant to, int page, int size) {
		String tenantId = currentTenantId();
		TimeWindow window = TimeWindow.resolve(from, to);
		int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
		Pageable pageable = PageRequest.of(Math.max(page, 0), safeSize);
		Page<TelemetryData> rows = repository.findHistory(tenantId, deviceId, window.from(), window.to(), pageable);
		return rows.map(r -> new TelemetryPointResponse(toInstant(r.getTs()), r.getPayload()));
	}

	/** 設備最新一筆讀數；查無資料時回傳 {@code null}。 */
	@Transactional(readOnly = true)
	public TelemetryLatestResponse latest(Long deviceId) {
		String tenantId = currentTenantId();
		List<TelemetryData> rows = repository.findLatest(tenantId, deviceId, PageRequest.of(0, 1));
		if (rows.isEmpty()) {
			return null;
		}
		TelemetryData r = rows.get(0);
		return new TelemetryLatestResponse(r.getDeviceId(), toInstant(r.getTs()), r.getPayload());
	}

	/**
	 * 設備在時間窗 [from, to) 內各數值欄位的 min/max/avg。
	 *
	 * <p>
	 * 未指定 {@code fields} 時，自最新一筆讀數推導出所有數值型欄位。
	 */
	@Transactional(readOnly = true)
	public List<TelemetryFieldStats> stats(Long deviceId, Instant from, Instant to, List<String> fields) {
		String tenantId = currentTenantId();
		TimeWindow window = TimeWindow.resolve(from, to);
		List<String> targetFields = resolveFields(deviceId, fields);
		List<TelemetryFieldStats> result = new ArrayList<>(targetFields.size());
		for (String field : targetFields) {
			Map<String, Object> row = jdbcTemplate.queryForMap(STATS_SQL, field, tenantId, deviceId, window.from(),
					window.to());
			long count = toLong(row.get("cnt"));
			if (count == 0) {
				result.add(new TelemetryFieldStats(field, 0, null, null, null));
			}
			else {
				result.add(new TelemetryFieldStats(field, count, toDouble(row.get("mn")), toDouble(row.get("mx")),
						toDouble(row.get("av"))));
			}
		}
		return result;
	}

	private List<String> resolveFields(Long deviceId, List<String> fields) {
		if (fields != null && !fields.isEmpty()) {
			return fields.stream().filter(f -> f != null && !f.isBlank()).map(String::trim).distinct().toList();
		}
		TelemetryLatestResponse latest = latest(deviceId);
		if (latest == null || latest.values() == null) {
			return List.of();
		}
		return latest.values()
			.entrySet()
			.stream()
			.filter(e -> e.getValue() instanceof Number)
			.map(Map.Entry::getKey)
			.toList();
	}

	private String currentTenantId() {
		String tenantId = TenantContext.getCurrentTenantId();
		if (tenantId == null || tenantId.isBlank() || TenantContext.isSystemContext()) {
			throw new BusinessException(ErrorCode.PERMISSION_DENIED, "缺少租戶內容");
		}
		return tenantId;
	}

	private static Instant toInstant(LocalDateTime ts) {
		return ts == null ? null : ts.toInstant(ZoneOffset.UTC);
	}

	private static long toLong(Object value) {
		return value == null ? 0L : ((Number) value).longValue();
	}

	private static Double toDouble(Object value) {
		return value == null ? null : ((Number) value).doubleValue();
	}

	/** 已解析、邊界校驗過的查詢時間窗（以 UTC {@link LocalDateTime} 表示，對應 {@code telemetry_data.ts}）。 */
	private record TimeWindow(LocalDateTime from, LocalDateTime to) {

		static TimeWindow resolve(Instant from, Instant to) {
			Instant toInstant = to != null ? to : Instant.now();
			Instant fromInstant = from != null ? from : toInstant.minus(DEFAULT_WINDOW);
			if (!fromInstant.isBefore(toInstant)) {
				throw new BusinessException(ErrorCode.IOT_TELEMETRY_QUERY_INVALID_RANGE, "from 必須早於 to");
			}
			return new TimeWindow(LocalDateTime.ofInstant(fromInstant, ZoneOffset.UTC),
					LocalDateTime.ofInstant(toInstant, ZoneOffset.UTC));
		}
	}

}
