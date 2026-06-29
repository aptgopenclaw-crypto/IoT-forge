package com.taipei.iot.common.health;

import com.taipei.iot.common.service.ClamAvVirusScanService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * F-4：ClamAV health indicator 驗證。
 * <p>
 * 確認 UP / DOWN 狀態、details 必含 host / port / streamMaxLength / ping 四欄。
 * </p>
 */
class ClamAvHealthIndicatorTest {

	private ClamAvVirusScanService service;

	private ClamAvHealthIndicator indicator;

	@BeforeEach
	void setUp() {
		service = mock(ClamAvVirusScanService.class);
		when(service.getHost()).thenReturn("clamd.internal");
		when(service.getPort()).thenReturn(3310);
		when(service.getStreamMaxLength()).thenReturn(26214400L);
		indicator = new ClamAvHealthIndicator(service);
	}

	@Test
	void health_whenPingSucceeds_returnsUpWithPongDetail() {
		when(service.ping()).thenReturn(true);

		Health health = indicator.health();

		assertThat(health.getStatus()).isEqualTo(Status.UP);
		assertThat(health.getDetails()).containsEntry("host", "clamd.internal")
			.containsEntry("port", 3310)
			.containsEntry("streamMaxLength", 26214400L)
			.containsEntry("ping", "PONG");
	}

	@Test
	void health_whenPingFails_returnsDownWithFailedDetail() {
		when(service.ping()).thenReturn(false);

		Health health = indicator.health();

		assertThat(health.getStatus()).isEqualTo(Status.DOWN);
		assertThat(health.getDetails()).containsEntry("host", "clamd.internal")
			.containsEntry("port", 3310)
			.containsEntry("streamMaxLength", 26214400L)
			.containsEntry("ping", "FAILED");
	}

	@Test
	void health_alwaysExposesStreamMaxLengthForOperators() {
		// 即使 DOWN 也要保留 streamMaxLength，供 SRE 比對 clamd 設定差異
		when(service.ping()).thenReturn(false);
		when(service.getStreamMaxLength()).thenReturn(52428800L);

		Health health = indicator.health();

		assertThat(health.getDetails()).containsEntry("streamMaxLength", 52428800L);
	}

}
