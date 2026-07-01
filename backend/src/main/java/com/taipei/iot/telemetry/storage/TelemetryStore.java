package com.taipei.iot.telemetry.storage;

import java.util.List;

/**
 * 遙測儲存抽象層。封裝寫入細節，使上層（ingestion）不直接相依具體儲存實作；未來可替換 {@link JpaTelemetryStore} 為 hypertable /
 * 其他時序後端。
 */
public interface TelemetryStore {

	/** 寫入單筆遙測讀數。 */
	void save(TelemetryReading reading);

	/** 批次寫入遙測讀數。 */
	void saveAll(List<TelemetryReading> readings);

}
