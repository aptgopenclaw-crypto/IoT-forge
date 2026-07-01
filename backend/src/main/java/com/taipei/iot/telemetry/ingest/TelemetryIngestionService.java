package com.taipei.iot.telemetry.ingest;

import java.util.List;

/**
 * 遙測接入核心入口。各來源 adapter 收斂成 {@link TelemetryIngestRequest} 後呼叫；核心負責設備解析、schema 驗證、寫入儲存、發出
 * {@code TelemetryReceivedEvent}。
 */
public interface TelemetryIngestionService {

	/** 接入單筆遙測。 */
	TelemetryIngestResult ingest(TelemetryIngestRequest request);

	/** 接入一批遙測（逐筆獨立處理，部分失敗不影響其他筆）。 */
	List<TelemetryIngestResult> ingestBatch(List<TelemetryIngestRequest> requests);

}
