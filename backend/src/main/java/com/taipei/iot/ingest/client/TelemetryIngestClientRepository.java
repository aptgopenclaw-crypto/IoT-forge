package com.taipei.iot.ingest.client;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * {@link TelemetryIngestClient} 資料存取。全域實體（非租戶過濾）：以 {@code apiKey} 跨租戶查詢供認證使用。
 */
public interface TelemetryIngestClientRepository extends JpaRepository<TelemetryIngestClient, Long> {

	Optional<TelemetryIngestClient> findByApiKey(String apiKey);

}
