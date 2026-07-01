package com.taipei.iot.ingest.source.http;

import com.taipei.iot.common.response.BaseResponse;
import com.taipei.iot.ingest.client.IngestClientPrincipal;
import com.taipei.iot.telemetry.ingest.TelemetryIngestResult;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 第三方遙測上報入口（M2M）。認證由 {@link IngestSecurityConfig} 的 API key 鏈處理，principal 為
 * {@link IngestClientPrincipal}。
 */
@RestController
@RequestMapping("/v1/ingest")
public class TelemetryIngestController {

	private final TelemetryHttpIngestService httpIngestService;

	public TelemetryIngestController(TelemetryHttpIngestService httpIngestService) {
		this.httpIngestService = httpIngestService;
	}

	/** 單筆上報。 */
	@PostMapping("/telemetry")
	public BaseResponse<TelemetryIngestResult> ingest(@RequestBody TelemetryIngestHttpRequest request,
			Authentication authentication) {
		IngestClientPrincipal principal = (IngestClientPrincipal) authentication.getPrincipal();
		TelemetryIngestResult result = httpIngestService.ingest(principal, List.of(request)).get(0);
		return BaseResponse.success(result);
	}

	/** 批次上報（逐筆獨立處理，部分失敗不影響其他筆）。 */
	@PostMapping("/telemetry/batch")
	public BaseResponse<List<TelemetryIngestResult>> ingestBatch(@RequestBody List<TelemetryIngestHttpRequest> requests,
			Authentication authentication) {
		IngestClientPrincipal principal = (IngestClientPrincipal) authentication.getPrincipal();
		return BaseResponse.success(httpIngestService.ingest(principal, requests));
	}

}
