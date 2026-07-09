package com.taipei.iot.vms;

import com.taipei.iot.common.exception.BusinessException;
import com.taipei.iot.common.enums.ErrorCode;
import com.taipei.iot.vms.enums.VmsType;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * VMS Adapter 管理器（Registry）。
 *
 * <p>
 * 收錄所有 {@link VmsAdapter} bean，依 {@link VmsType} 索引。 與 {@code TelemetryDecoderRegistry}
 * 相同模式：List injection → Map 自動註冊。
 * </p>
 */
@Component
public class VmsAdapterManager {

	private final Map<VmsType, VmsAdapter> adapterMap;

	public VmsAdapterManager(List<VmsAdapter> adapters) {
		this.adapterMap = adapters.stream()
			.collect(Collectors.toUnmodifiableMap(VmsAdapter::getType, Function.identity()));
	}

	/** 依 VMS 類型取得對應 adapter；不支援時拋 {@code VMS_TYPE_UNSUPPORTED}。 */
	public VmsAdapter getAdapter(VmsType type) {
		VmsAdapter adapter = adapterMap.get(type);
		if (adapter == null) {
			throw new BusinessException(ErrorCode.VMS_TYPE_UNSUPPORTED, "不支援的 VMS 類型: " + type);
		}
		return adapter;
	}

	/** 取得所有已註冊的 adapter。 */
	public Collection<VmsAdapter> allAdapters() {
		return adapterMap.values();
	}

}
