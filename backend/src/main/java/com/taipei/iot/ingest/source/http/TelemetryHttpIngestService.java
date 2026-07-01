package com.taipei.iot.ingest.source.http;

import com.taipei.iot.common.enums.ErrorCode;
import com.taipei.iot.ingest.client.DeviceExternalRef;
import com.taipei.iot.ingest.client.DeviceExternalRefRepository;
import com.taipei.iot.ingest.client.IngestClientPrincipal;
import com.taipei.iot.telemetry.ingest.TelemetryIngestRequest;
import com.taipei.iot.telemetry.ingest.TelemetryIngestResult;
import com.taipei.iot.telemetry.ingest.TelemetryIngestionService;
import com.taipei.iot.telemetry.ingest.TelemetrySource;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * HTTP ingest 應用服務：將 {@link TelemetryIngestHttpRequest} 收斂為 canonical
 * {@link TelemetryIngestRequest}（source=HTTP_API），逐筆解析設備碼後交給 telemetry 核心。外部碼解析失敗時 該筆回
 * {@link ErrorCode#IOT_INGEST_DEVICE_NOT_MAPPED}，不影響其他筆。
 */
@Service
public class TelemetryHttpIngestService {

	private final DeviceExternalRefRepository externalRefRepository;

	private final TelemetryIngestionService ingestionService;

	public TelemetryHttpIngestService(DeviceExternalRefRepository externalRefRepository,
			TelemetryIngestionService ingestionService) {
		this.externalRefRepository = externalRefRepository;
		this.ingestionService = ingestionService;
	}

	public List<TelemetryIngestResult> ingest(IngestClientPrincipal principal,
			List<TelemetryIngestHttpRequest> requests) {
		List<TelemetryIngestResult> results = new ArrayList<>(requests.size());
		for (TelemetryIngestHttpRequest request : requests) {
			results.add(ingestOne(principal, request));
		}
		return results;
	}

	private TelemetryIngestResult ingestOne(IngestClientPrincipal principal, TelemetryIngestHttpRequest request) {
		String deviceCode = resolveDeviceCode(principal.tenantId(), request);
		if (deviceCode == null) {
			String reference = request.externalCode() != null ? request.externalCode() : null;
			return TelemetryIngestResult.failure(reference, ErrorCode.IOT_INGEST_DEVICE_NOT_MAPPED.getCode(),
					ErrorCode.IOT_INGEST_DEVICE_NOT_MAPPED.getMessage());
		}
		TelemetryIngestRequest canonical = new TelemetryIngestRequest(deviceCode, principal.tenantId(), request.ts(),
				request.values(), TelemetrySource.HTTP_API, principal.clientName(), null);
		return ingestionService.ingest(canonical);
	}

	private String resolveDeviceCode(String tenantId, TelemetryIngestHttpRequest request) {
		if (request.deviceCode() != null && !request.deviceCode().isBlank()) {
			return request.deviceCode();
		}
		if (request.externalCode() == null || request.externalCode().isBlank()) {
			return null;
		}
		Optional<DeviceExternalRef> ref = externalRefRepository.findByTenantIdAndExternalCode(tenantId,
				request.externalCode());
		return ref.filter(DeviceExternalRef::isEnabled).map(DeviceExternalRef::getDeviceCode).orElse(null);
	}

}
