package com.taipei.iot.ingest.source.mqtt;

import com.taipei.iot.ingest.decoder.DecodedReading;
import com.taipei.iot.ingest.decoder.TelemetryDecoderRegistry;
import com.taipei.iot.telemetry.ingest.TelemetryIngestRequest;
import com.taipei.iot.telemetry.ingest.TelemetryIngestResult;
import com.taipei.iot.telemetry.ingest.TelemetryIngestionService;
import com.taipei.iot.telemetry.ingest.TelemetrySource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * MQTT 來源 adapter 的核心處理邏輯（與 broker 解耦，可獨立單元測試）。topic 形如
 * {@code device/{deviceCode}/telemetry}，payload 經預設 decoder 解碼後收斂為 canonical
 * {@link TelemetryIngestRequest}（source=MQTT），逐筆交給 telemetry 核心。
 * <p>
 * MQTT 不於 topic 攜帶租戶；以 {@code mqtt.default-tenant-id} 設定（單租戶/同棚部署）。多租戶 broker 隔離由 EMQX
 * 端依憑證對應 topic 命名空間達成，屬部署層議題，核心不變。
 */
@Component
public class TelemetryMqttHandler {

	private static final Logger log = LoggerFactory.getLogger(TelemetryMqttHandler.class);

	private static final Pattern TELEMETRY_TOPIC = Pattern.compile("^device/(?<code>[^/]+)/telemetry$");

	private final TelemetryDecoderRegistry decoderRegistry;

	private final TelemetryIngestionService ingestionService;

	private final String defaultTenantId;

	public TelemetryMqttHandler(TelemetryDecoderRegistry decoderRegistry, TelemetryIngestionService ingestionService,
			@Value("${mqtt.default-tenant-id:DEFAULT}") String defaultTenantId) {
		this.decoderRegistry = decoderRegistry;
		this.ingestionService = ingestionService;
		this.defaultTenantId = defaultTenantId;
	}

	/**
	 * 處理一則 MQTT 訊息。
	 * @param topic 收到的 topic
	 * @param payload 原始位元組 payload
	 */
	public void handle(String topic, byte[] payload) {
		String deviceCodeFromTopic = extractDeviceCode(topic);
		List<DecodedReading> readings;
		try {
			readings = decoderRegistry.getDefault().decode(payload);
		}
		catch (RuntimeException ex) {
			log.warn("Failed to decode MQTT telemetry: topic={} error={}", topic, ex.getMessage());
			return;
		}

		for (DecodedReading reading : readings) {
			String deviceCode = reading.deviceCode() != null ? reading.deviceCode() : deviceCodeFromTopic;
			if (deviceCode == null || deviceCode.isBlank()) {
				log.warn("Skipping MQTT telemetry without deviceCode: topic={}", topic);
				continue;
			}
			TelemetryIngestRequest request = new TelemetryIngestRequest(deviceCode, defaultTenantId, reading.ts(),
					reading.values(), TelemetrySource.MQTT, null, null);
			TelemetryIngestResult result = ingestionService.ingest(request);
			if (!result.success()) {
				log.warn("MQTT telemetry rejected: device={} errorCode={} msg={}", deviceCode, result.errorCode(),
						result.message());
			}
		}
	}

	private String extractDeviceCode(String topic) {
		if (topic == null) {
			return null;
		}
		Matcher matcher = TELEMETRY_TOPIC.matcher(topic);
		return matcher.matches() ? matcher.group("code") : null;
	}

}
