package com.taipei.iot.telemetry.storage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * {@code telemetry_data} 月分區維護（取代 TimescaleDB retention policy）。
 *
 * <ul>
 * <li><b>預建分區</b>：確保當月 + 未來數月的月分區存在（缺則建立）。</li>
 * <li><b>保留清理</b>：{@code DROP} 超過保留期（{@value #RETENTION_DAYS} 天）的舊月分區——O(1) 中繼資料操作，無
 * {@code DELETE} 的 vacuum 壓力；DEFAULT 分區不受影響。</li>
 * </ul>
 *
 * <p>
 * 以 {@link JdbcTemplate} 執行 DDL（非 {@code EntityManager.createNativeQuery}，不觸及 tenant
 * filter）。分區名稱由日期推導，非使用者輸入，無 SQL injection 風險。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TelemetryPartitionMaintenanceJob {

	static final int RETENTION_DAYS = 90;

	static final int PRECREATE_MONTHS = 2;

	private static final String PARENT = "telemetry_data";

	private static final DateTimeFormatter SUFFIX = DateTimeFormatter.ofPattern("yyyy_MM");

	private final JdbcTemplate jdbcTemplate;

	/** 每日 02:15 維護分區。 */
	@Scheduled(cron = "0 15 2 * * ?")
	public void maintainPartitions() {
		precreatePartitions();
		dropExpiredPartitions();
	}

	void precreatePartitions() {
		YearMonth start = YearMonth.now(ZoneOffset.UTC);
		for (int i = 0; i <= PRECREATE_MONTHS; i++) {
			createPartition(start.plusMonths(i));
		}
	}

	private void createPartition(YearMonth month) {
		String name = PARENT + "_" + month.format(SUFFIX);
		LocalDate from = month.atDay(1);
		LocalDate to = month.plusMonths(1).atDay(1);
		jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS " + name + " PARTITION OF " + PARENT + " FOR VALUES FROM ('"
				+ from + "') TO ('" + to + "')");
	}

	void dropExpiredPartitions() {
		LocalDate cutoff = LocalDate.now(ZoneOffset.UTC).minusDays(RETENTION_DAYS);
		List<String> partitions = jdbcTemplate.queryForList(
				"SELECT c.relname FROM pg_inherits i " + "JOIN pg_class c ON c.oid = i.inhrelid "
						+ "WHERE i.inhparent = ?::regclass AND c.relname ~ '^" + PARENT + "_[0-9]{4}_[0-9]{2}$'",
				String.class, PARENT);
		for (String partition : partitions) {
			YearMonth month = YearMonth.parse(partition.substring(PARENT.length() + 1), SUFFIX);
			LocalDate partitionEnd = month.plusMonths(1).atDay(1);
			if (!partitionEnd.isAfter(cutoff)) {
				jdbcTemplate.execute("DROP TABLE IF EXISTS " + partition);
				log.info("TelemetryPartitionMaintenanceJob: dropped expired partition {}", partition);
			}
		}
	}

}
