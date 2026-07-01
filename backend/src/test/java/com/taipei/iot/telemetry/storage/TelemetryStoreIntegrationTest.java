package com.taipei.iot.telemetry.storage;

import com.taipei.iot.common.context.TenantContext;
import com.taipei.iot.device.entity.Device;
import com.taipei.iot.device.enums.DeviceStatus;
import com.taipei.iot.device.repository.DeviceRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 驗證 {@link JpaTelemetryStore} 寫入原生 PostgreSQL 月分區表 {@code telemetry_data}（含 FK 至
 * {@code devices}、依 {@code ts} 路由分區）。
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@Tag("integration")
class TelemetryStoreIntegrationTest {

	@Autowired
	private TelemetryStore telemetryStore;

	@Autowired
	private DeviceRepository deviceRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@AfterEach
	void cleanup() {
		TenantContext.clear();
	}

	@Test
	void save_writesRowRoutedToMonthlyPartition() {
		TenantContext.setCurrentTenantId("DEFAULT");
		Device device = deviceRepository.save(Device.builder()
			.tenantId("DEFAULT")
			.deviceType("STREET_LIGHT")
			.deviceCode("TEST-TELEM-" + System.nanoTime())
			.status(DeviceStatus.ACTIVE)
			.build());

		telemetryStore
			.save(new TelemetryReading("DEFAULT", device.getId(), "STREET_LIGHT", Instant.parse("2026-06-30T08:00:00Z"),
					Map.of("temperature", 25.5), "MQTT", "client-1", Map.of("raw", true)));

		Long count = jdbcTemplate.queryForObject("SELECT count(*) FROM telemetry_data WHERE device_id = ?", Long.class,
				device.getId());
		assertEquals(1L, count);

		// 確認落入 2026_06 月分區（非 default）
		Long inJunePartition = jdbcTemplate.queryForObject(
				"SELECT count(*) FROM telemetry_data_2026_06 WHERE device_id = ?", Long.class, device.getId());
		assertEquals(1L, inJunePartition);
	}

}
