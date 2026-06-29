package com.taipei.iot.tenant;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Collection;
import java.util.Map;

/**
 * [Tenant v2 T-9] {@link TenantEntity#config}（jsonb {@code Map<String, Object>}）的尺寸／結構防護。
 *
 * <p>
 * 目前沒有任何 API 寫入 {@code config}，但 entity 已暴露 setter，未來如新增「更新 tenant 設定」 API
 * 時，本驗證會自動把關，避免被當成 NoSQL 注入點（塞入超大 JSON 撐爆 DB / index）。
 *
 * <p>
 * 限制：
 * <ul>
 * <li>序列化後 JSON 字串長度 ≤ {@link #MAX_SERIALIZED_BYTES} bytes（UTF-8）</li>
 * <li>top-level keys 數量 ≤ {@link #MAX_TOP_LEVEL_KEYS}</li>
 * <li>巢狀深度 ≤ {@link #MAX_DEPTH}</li>
 * </ul>
 *
 * <p>
 * 違反時拋 {@link IllegalArgumentException}，由 {@code GlobalExceptionHandler} 轉成 400 Bad
 * Request；不會發生資料壞掉的情形。
 */
public final class TenantConfigValidator {

	/** 10 KB 上限 — 一般合理的 tenant feature flag / quota 設定遠小於此值。 */
	public static final int MAX_SERIALIZED_BYTES = 10 * 1024;

	/** 50 個 top-level keys 上限 — 防止扁平結構繞過 size 限制造成 index 膨脹。 */
	public static final int MAX_TOP_LEVEL_KEYS = 50;

	/** 5 層巢狀上限 — 防止 deeply-nested payload 造成解析 OOM / stack overflow。 */
	public static final int MAX_DEPTH = 5;

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private TenantConfigValidator() {
		// utility
	}

	/**
	 * 驗證 tenant config。{@code null} 與空 map 視為合法（不寫 jsonb 欄位）。
	 * @throws IllegalArgumentException 違反任一限制
	 */
	public static void validate(Map<String, Object> config) {
		if (config == null || config.isEmpty()) {
			return;
		}

		if (config.size() > MAX_TOP_LEVEL_KEYS) {
			throw new IllegalArgumentException(
					"tenant.config top-level keys 超出上限：" + config.size() + " > " + MAX_TOP_LEVEL_KEYS);
		}

		int depth = depthOf(config, 1);
		if (depth > MAX_DEPTH) {
			throw new IllegalArgumentException("tenant.config 巢狀深度超出上限：" + depth + " > " + MAX_DEPTH);
		}

		int size = serializedSize(config);
		if (size > MAX_SERIALIZED_BYTES) {
			throw new IllegalArgumentException(
					"tenant.config 序列化後大小超出上限：" + size + " bytes > " + MAX_SERIALIZED_BYTES + " bytes");
		}
	}

	private static int serializedSize(Map<String, Object> config) {
		try {
			return MAPPER.writeValueAsBytes(config).length;
		}
		catch (JsonProcessingException e) {
			throw new IllegalArgumentException("tenant.config 無法序列化為 JSON：" + e.getOriginalMessage(), e);
		}
	}

	private static int depthOf(Object node, int current) {
		if (current > MAX_DEPTH + 1) {
			// 早退 — 不必繼續算更深
			return current;
		}
		if (node instanceof Map<?, ?> map) {
			int max = current;
			for (Object v : map.values()) {
				max = Math.max(max, depthOf(v, current + 1));
			}
			return max;
		}
		if (node instanceof Collection<?> col) {
			int max = current;
			for (Object v : col) {
				max = Math.max(max, depthOf(v, current + 1));
			}
			return max;
		}
		return current;
	}

}
