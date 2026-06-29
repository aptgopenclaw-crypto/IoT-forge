package com.taipei.iot.common.util;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * 多語系語言代碼正規化工具。
 *
 * <p>
 * 提供「將 client 傳入的 lang code 限制在白名單內、空值 / 非法值 fallback 為預設值」的共用邏輯， 避免各 service 重複手寫相同樣板。
 *
 * <h2>使用範例</h2> <pre>{@code
 *   // 模組層自訂支援語系
 *   private static final Set<String> SUPPORTED = Set.of("zh-TW", "zh-CN", "en");
 *   private static final String DEFAULT = "zh-TW";
 *
 *   String safe = LangNormalizer.normalize(langCode, SUPPORTED, DEFAULT);
 * }</pre>
 *
 * <p>
 * 另提供常用語系常數（{@link #ZH_TW} / {@link #ZH_CN} / {@link #EN_US} / {@link #EN}），
 * 各模組可視需求宣告自己的支援集合。
 *
 * <p>
 * [common v2 F-17]
 */
public final class LangNormalizer {

	public static final String ZH_TW = "zh-TW";

	public static final String ZH_CN = "zh-CN";

	public static final String EN = "en";

	public static final String EN_US = "en-US";

	private LangNormalizer() {
	}

	/**
	 * 將 lang code 限制在支援白名單內；空值 / null / 非白名單成員 fallback 為 {@code defaultLang}。
	 * @param langCode 來自 client 的 lang code（可能為 null / 空白 / 非法值）
	 * @param supportedLangs 支援的 lang code 白名單；不可 null（{@link Set#of()} 可表示「無支援」， 此時永遠回
	 * defaultLang）
	 * @param defaultLang 白名單不命中時的預設語系；不可 null/blank
	 * @return 安全的 lang code（必定為 defaultLang 或 supportedLangs 中的成員）
	 * @throws IllegalArgumentException defaultLang 為 null/blank 或 supportedLangs 為 null
	 */
	public static String normalize(String langCode, Set<String> supportedLangs, String defaultLang) {
		if (defaultLang == null || defaultLang.isBlank()) {
			throw new IllegalArgumentException("defaultLang must not be null/blank");
		}
		if (supportedLangs == null) {
			throw new IllegalArgumentException("supportedLangs must not be null");
		}
		if (langCode == null || langCode.isBlank()) {
			return defaultLang;
		}
		return supportedLangs.contains(langCode) ? langCode : defaultLang;
	}

	/**
	 * 便利建構器：以可變參數建立 immutable 支援語系集合。 用於宣告模組層的 {@code SUPPORTED_LANGS} 常數。
	 */
	public static Set<String> supportedSet(String... langs) {
		if (langs == null || langs.length == 0) {
			return Collections.emptySet();
		}
		return Collections.unmodifiableSet(new HashSet<>(Arrays.asList(langs)));
	}

}
