package com.taipei.iot.ingest.decoder;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 收錄所有 {@link TelemetryPayloadDecoder} bean，依 {@code format()} 索引。來源 adapter 依格式取得
 * decoder；未指定或未知格式時回退至預設的 {@link CanonicalTelemetryDecoder}。
 */
@Component
public class TelemetryDecoderRegistry {

	public static final String DEFAULT_FORMAT = CanonicalTelemetryDecoder.FORMAT;

	private final Map<String, TelemetryPayloadDecoder> decoders;

	public TelemetryDecoderRegistry(List<TelemetryPayloadDecoder> decoders) {
		this.decoders = decoders.stream()
			.collect(Collectors.toUnmodifiableMap(d -> d.format().toLowerCase(Locale.ROOT), Function.identity()));
	}

	/** 取得指定格式的 decoder；null/空白/未知格式回退至預設。 */
	public TelemetryPayloadDecoder get(String format) {
		if (format == null || format.isBlank()) {
			return getDefault();
		}
		TelemetryPayloadDecoder decoder = decoders.get(format.toLowerCase(Locale.ROOT));
		return decoder != null ? decoder : getDefault();
	}

	/** 取得預設 canonical decoder。 */
	public TelemetryPayloadDecoder getDefault() {
		TelemetryPayloadDecoder decoder = decoders.get(DEFAULT_FORMAT);
		if (decoder == null) {
			throw new IllegalStateException("No default telemetry decoder registered: " + DEFAULT_FORMAT);
		}
		return decoder;
	}

}
