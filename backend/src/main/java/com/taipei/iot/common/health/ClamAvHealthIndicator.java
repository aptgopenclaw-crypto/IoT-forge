package com.taipei.iot.common.health;

import com.taipei.iot.common.service.ClamAvVirusScanService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * F-4：ClamAV 連線健康度 actuator endpoint。
 *
 * <p>
 * 暴露於 {@code /actuator/health/clamav}（bean name {@code clamav}）：
 * </p>
 * <ul>
 * <li>UP：clamd PING 回應 {@code PONG}</li>
 * <li>DOWN：連線失敗 / 逾時 / 非預期回應</li>
 * </ul>
 *
 * <p>
 * details 欄位（兩種狀態皆會輸出）：
 * </p>
 * <ul>
 * <li>{@code host} — clamd 連線位址</li>
 * <li>{@code port} — clamd 連線埠</li>
 * <li>{@code streamMaxLength} — 設定中的 INSTREAM 上限（bytes，需與 clamd {@code StreamMaxLength}
 * 一致；詳見 N-4）</li>
 * <li>{@code ping} — {@code PONG} / {@code FAILED}</li>
 * </ul>
 *
 * <p>
 * 僅在 {@code virus-scan.enabled=true} 時註冊；NoOp 模式不暴露此 endpoint。
 * </p>
 */
@Slf4j
@Component("clamav")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "virus-scan.enabled", havingValue = "true")
public class ClamAvHealthIndicator implements HealthIndicator {

	private final ClamAvVirusScanService clamAvVirusScanService;

	@Override
	public Health health() {
		boolean pong = clamAvVirusScanService.ping();
		Health.Builder builder = pong ? Health.up() : Health.down();
		return builder.withDetail("host", clamAvVirusScanService.getHost())
			.withDetail("port", clamAvVirusScanService.getPort())
			.withDetail("streamMaxLength", clamAvVirusScanService.getStreamMaxLength())
			.withDetail("ping", pong ? "PONG" : "FAILED")
			.build();
	}

}
