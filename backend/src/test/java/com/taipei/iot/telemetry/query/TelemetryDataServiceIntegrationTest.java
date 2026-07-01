package com.taipei.iot.telemetry.query;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.taipei.iot.common.context.TenantContext;
import com.taipei.iot.common.exception.BusinessException;
import com.taipei.iot.device.entity.Device;
import com.taipei.iot.device.enums.DeviceStatus;
import com.taipei.iot.device.repository.DeviceRepository;
import com.taipei.iot.telemetry.query.dto.TelemetryFieldStats;
import com.taipei.iot.telemetry.query.dto.TelemetryLatestResponse;
import com.taipei.iot.telemetry.query.dto.TelemetryPointResponse;
import com.taipei.iot.telemetry.storage.TelemetryReading;
import com.taipei.iot.telemetry.storage.TelemetryStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link TelemetryDataService} 打真 PostgreSQL 的整合測試：歷史 / 最新值 / 統計，並驗證租戶隔離 —— 同一 device_id
 * 下他租戶（{@code OTHER}）的列不得被當前租戶（{@code DEFAULT}）查到。
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@Tag("integration")
class TelemetryDataServiceIntegrationTest {

	@Autowired
	private TelemetryDataService telemetryDataService;

	@Autowired
	private TelemetryStore telemetryStore;

	@Autowired
	private DeviceRepository deviceRepository;

	private static final Instant FROM = Instant.parse("2026-06-30T00:00:00Z");

	private static final Instant TO = Instant.parse("2026-07-01T00:00:00Z");

	private Long deviceId;

	@BeforeEach
	void seed() {
		TenantContext.setCurrentTenantId("DEFAULT");
		Device device = deviceRepository.save(Device.builder()
			.tenantId("DEFAULT")
			.deviceType("STREET_LIGHT")
			.deviceCode("DEV-QRY-" + System.nanoTime())
			.status(DeviceStatus.ACTIVE)
			.build());
		this.deviceId = device.getId();

		// DEFAULT 租戶三筆，溫度 10/20/30，時間遞增
		telemetryStore.save(reading("DEFAULT", "2026-06-30T08:00:00Z", 10.0));
		telemetryStore.save(reading("DEFAULT", "2026-06-30T09:00:00Z", 20.0));
		telemetryStore.save(reading("DEFAULT", "2026-06-30T10:00:00Z", 30.0));
		// 同一 device_id 但屬於他租戶 OTHER 的一筆（溫度 99）—— 必須被隔離
		telemetryStore.save(reading("OTHER", "2026-06-30T11:00:00Z", 99.0));
	}

	@AfterEach
	void cleanup() {
		TenantContext.clear();
	}

	private TelemetryReading reading(String tenantId, String ts, double temperature) {
		return new TelemetryReading(tenantId, deviceId, "STREET_LIGHT", Instant.parse(ts),
				Map.of("temperature", temperature), "MQTT", null, null);
	}

	@Test
	void history_returnsOnlyCurrentTenantRows_descByTs() {
		TenantContext.setCurrentTenantId("DEFAULT");

		Page<TelemetryPointResponse> page = telemetryDataService.history(deviceId, FROM, TO, 0, 100);

		assertThat(page.getTotalElements()).isEqualTo(3);
		assertThat(page.getContent()).extracting(p -> p.values().get("temperature")).containsExactly(30.0, 20.0, 10.0);
		assertThat(page.getContent()).noneMatch(p -> p.values().get("temperature").equals(99.0));
	}

	@Test
	void latest_returnsMostRecentCurrentTenantRow() {
		TenantContext.setCurrentTenantId("DEFAULT");

		TelemetryLatestResponse latest = telemetryDataService.latest(deviceId);

		assertThat(latest).isNotNull();
		assertThat(latest.deviceId()).isEqualTo(deviceId);
		assertThat(latest.values().get("temperature")).isEqualTo(30.0);
		assertThat(latest.ts()).isEqualTo(Instant.parse("2026-06-30T10:00:00Z"));
	}

	@Test
	void stats_aggregatesOnlyCurrentTenantNumericValues() {
		TenantContext.setCurrentTenantId("DEFAULT");

		List<TelemetryFieldStats> stats = telemetryDataService.stats(deviceId, FROM, TO, List.of("temperature"));

		assertThat(stats).hasSize(1);
		TelemetryFieldStats temperature = stats.get(0);
		assertThat(temperature.field()).isEqualTo("temperature");
		assertThat(temperature.count()).isEqualTo(3);
		assertThat(temperature.min()).isEqualTo(10.0);
		assertThat(temperature.max()).isEqualTo(30.0);
		assertThat(temperature.avg()).isEqualTo(20.0);
	}

	@Test
	void stats_withoutFields_derivesNumericFieldsFromLatest() {
		TenantContext.setCurrentTenantId("DEFAULT");

		List<TelemetryFieldStats> stats = telemetryDataService.stats(deviceId, FROM, TO, null);

		assertThat(stats).extracting(TelemetryFieldStats::field).containsExactly("temperature");
		assertThat(stats.get(0).count()).isEqualTo(3);
	}

	@Test
	void otherTenantCannotSeeDefaultRows() {
		TenantContext.setCurrentTenantId("OTHER");

		Page<TelemetryPointResponse> page = telemetryDataService.history(deviceId, FROM, TO, 0, 100);

		assertThat(page.getTotalElements()).isEqualTo(1);
		assertThat(page.getContent().get(0).values().get("temperature")).isEqualTo(99.0);
	}

	@Test
	void history_withInvalidRange_throws() {
		TenantContext.setCurrentTenantId("DEFAULT");

		assertThatThrownBy(() -> telemetryDataService.history(deviceId, TO, FROM, 0, 100))
			.isInstanceOf(BusinessException.class);
	}

}
