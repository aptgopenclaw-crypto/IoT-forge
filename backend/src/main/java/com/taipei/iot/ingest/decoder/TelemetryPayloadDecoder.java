package com.taipei.iot.ingest.decoder;

import java.util.List;

/**
 * 廠商 payload 解碼器擴展點。各來源（MQTT/HTTP/廠商私有格式）的位元組 payload 由對應 decoder 轉成
 * {@link DecodedReading} 清單，使 telemetry 核心與協定/廠商格式無關。
 * <p>
 * 新增廠商格式只需實作此介面並註冊為 Spring bean，{@link TelemetryDecoderRegistry} 會自動收錄。
 */
public interface TelemetryPayloadDecoder {

	/** 此 decoder 對應的格式識別碼（大小寫不敏感，需唯一）。 */
	String format();

	/**
	 * 將原始 payload 解碼成一筆或多筆讀數。
	 * @param payload 原始位元組
	 * @return 解碼後的讀數（不可為 null；無資料時回空清單）
	 */
	List<DecodedReading> decode(byte[] payload);

}
