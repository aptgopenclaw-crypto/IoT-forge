package com.taipei.iot.telemetry.storage;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * 以 JPA 實作的 {@link TelemetryStore}。寫入 {@code telemetry_data} 月分區表（PostgreSQL 依 {@code ts}
 * 自動路由至對應分區）。
 */
@Component
@RequiredArgsConstructor
public class JpaTelemetryStore implements TelemetryStore {

	private final TelemetryDataRepository repository;

	@Override
	@Transactional
	public void save(TelemetryReading reading) {
		repository.save(toEntity(reading));
	}

	@Override
	@Transactional
	public void saveAll(List<TelemetryReading> readings) {
		repository.saveAll(readings.stream().map(this::toEntity).toList());
	}

	private TelemetryData toEntity(TelemetryReading r) {
		return TelemetryData.builder()
			.tenantId(r.tenantId())
			.deviceId(r.deviceId())
			.deviceType(r.deviceType())
			.ts(LocalDateTime.ofInstant(r.ts(), ZoneOffset.UTC))
			.receivedAt(LocalDateTime.now(ZoneOffset.UTC))
			.source(r.source())
			.sourceClientId(r.sourceClientId())
			.payload(r.values())
			.rawPayload(r.rawPayload())
			.valid(true)
			.build();
	}

}
