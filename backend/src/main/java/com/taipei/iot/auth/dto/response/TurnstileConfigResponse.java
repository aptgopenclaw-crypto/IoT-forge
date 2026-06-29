package com.taipei.iot.auth.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Turnstile 設定資訊 — 回傳給前端用於初始化 Turnstile widget。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TurnstileConfigResponse {

	/** Turnstile 是否已啟用 */
	private boolean enabled;

	/** Turnstile site key（前端初始化 widget 用） */
	private String siteKey;

}
