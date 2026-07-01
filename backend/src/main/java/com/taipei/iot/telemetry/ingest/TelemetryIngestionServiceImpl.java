package com.taipei.iot.telemetry.ingest;

import com.taipei.iot.common.context.TenantContext;
import com.taipei.iot.common.device.port.DeviceLookupPort;
import com.taipei.iot.common.device.port.DeviceRef;
import com.taipei.iot.common.enums.ErrorCode;
import com.taipei.iot.common.event.TelemetryReceivedEvent;
import com.taipei.iot.telemetry.storage.TelemetryReading;
import com.taipei.iot.telemetry.storage.TelemetryStore;
import com.taipei.iot.telemetry.validation.TelemetryValidationResult;
import com.taipei.iot.telemetry.validation.TelemetryValidationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * {@link TelemetryIngestionService} 核心實作。協定無關：
 * <ol>
 * <li>{@link DeviceLookupPort#resolve} 解析 deviceCode（找不到→拒絕）。</li>
 * <li>{@link TelemetryValidationService} 以 telemetry schema 驗證 values（不符→拒絕）。</li>
 * <li>{@link TelemetryStore} 寫入（標記 source）。</li>
 * <li>{@link ApplicationEventPublisher} 發出 {@link TelemetryReceivedEvent} 供 event-rule
 * 訂閱。</li>
 * </ol>
 *
 * <p>
 * 以 {@code request.tenantId()} 顯式建立 {@link TenantContext}，使 schema 查詢（租戶範圍）正確；處理完畢還原。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TelemetryIngestionServiceImpl implements TelemetryIngestionService {

	private final DeviceLookupPort deviceLookupPort;

	private final TelemetryValidationService validationService;

	private final TelemetryStore telemetryStore;

	private final ApplicationEventPublisher eventPublisher;

	@Override
	public TelemetryIngestResult ingest(TelemetryIngestRequest request) {
		String previousTenant = TenantContext.getCurrentTenantId();
		try {
			TenantContext.setCurrentTenantId(request.tenantId());
			return doIngest(request);
		}
		finally {
			if (previousTenant != null) {
				TenantContext.setCurrentTenantId(previousTenant);
			}
			else {
				TenantContext.clear();
			}
		}
	}

	@Override
	public List<TelemetryIngestResult> ingestBatch(List<TelemetryIngestRequest> requests) {
		return requests.stream().map(this::ingest).toList();
	}

	private TelemetryIngestResult doIngest(TelemetryIngestRequest request) {
		Optional<DeviceRef> deviceOpt = deviceLookupPort.resolve(request.deviceCode(), request.tenantId());
		if (deviceOpt.isEmpty()) {
			return TelemetryIngestResult.failure(request.deviceCode(), ErrorCode.DEVICE_NOT_FOUND.getCode(),
					"Device not found: " + request.deviceCode());
		}
		DeviceRef device = deviceOpt.get();

		TelemetryValidationResult validation = validationService.validate(device.deviceType(), request.values());
		if (!validation.valid()) {
			log.warn("Telemetry validation failed: device={} type={} errors={}", device.deviceCode(),
					device.deviceType(), validation.errors());
			return TelemetryIngestResult.validationFailure(request.deviceCode(),
					ErrorCode.IOT_TELEMETRY_VALIDATION_FAILED.getCode(), "Telemetry validation failed",
					validation.errors());
		}

		Instant ts = request.ts() != null ? request.ts() : Instant.now();
		TelemetrySource source = request.source() != null ? request.source() : TelemetrySource.MQTT;
		telemetryStore.save(new TelemetryReading(device.tenantId(), device.deviceId(), device.deviceType(), ts,
				request.values(), source.name(), request.sourceClientId(), request.rawPayload()));

		eventPublisher.publishEvent(new TelemetryReceivedEvent(device.tenantId(), device.deviceId(),
				device.deviceType(), ts, request.values()));

		return TelemetryIngestResult.success(request.deviceCode(), device.deviceId());
	}

}
