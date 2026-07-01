package com.taipei.iot.telemetry.storage;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * {@link TelemetryData} 的 JPA repository。Step 5 查詢 API 以此擴充歷史/最新值查詢。
 *
 * <p>
 * {@code telemetry_data} 不掛 Hibernate {@code tenantFilter}，故所有查詢都<strong>明確</strong>帶入
 * {@code tenantId} 條件以確保租戶隔離（避免跨租戶資料外洩）。
 */
public interface TelemetryDataRepository extends JpaRepository<TelemetryData, Long> {

	/** 指定設備在時間窗 [from, to) 內的歷史讀數，依時間遞減分頁。 */
	@Query("""
			SELECT t FROM TelemetryData t
			WHERE t.tenantId = :tenantId AND t.deviceId = :deviceId
			  AND t.ts >= :from AND t.ts < :to
			ORDER BY t.ts DESC
			""")
	Page<TelemetryData> findHistory(@Param("tenantId") String tenantId, @Param("deviceId") Long deviceId,
			@Param("from") LocalDateTime from, @Param("to") LocalDateTime to, Pageable pageable);

	/** 指定設備的最新讀數（搭配 {@code PageRequest.of(0, 1)} 取第一筆）。 */
	@Query("""
			SELECT t FROM TelemetryData t
			WHERE t.tenantId = :tenantId AND t.deviceId = :deviceId
			ORDER BY t.ts DESC
			""")
	List<TelemetryData> findLatest(@Param("tenantId") String tenantId, @Param("deviceId") Long deviceId,
			Pageable pageable);

}
