package com.taipei.iot.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

@DisplayName("LangNormalizer [common v2 F-17]")
class LangNormalizerTest {

	private static final Set<String> SUPPORTED = Set.of("zh-TW", "zh-CN", "en");

	private static final String DEFAULT = "zh-TW";

	@Test
	@DisplayName("白名單命中：原值回傳")
	void supportedReturnsAsIs() {
		assertThat(LangNormalizer.normalize("zh-CN", SUPPORTED, DEFAULT)).isEqualTo("zh-CN");
		assertThat(LangNormalizer.normalize("en", SUPPORTED, DEFAULT)).isEqualTo("en");
	}

	@Test
	@DisplayName("白名單未命中：fallback 為 default")
	void unsupportedReturnsDefault() {
		assertThat(LangNormalizer.normalize("ja", SUPPORTED, DEFAULT)).isEqualTo(DEFAULT);
		assertThat(LangNormalizer.normalize("xxx", SUPPORTED, DEFAULT)).isEqualTo(DEFAULT);
	}

	@Test
	@DisplayName("null / blank langCode：fallback 為 default")
	void nullOrBlankReturnsDefault() {
		assertThat(LangNormalizer.normalize(null, SUPPORTED, DEFAULT)).isEqualTo(DEFAULT);
		assertThat(LangNormalizer.normalize("", SUPPORTED, DEFAULT)).isEqualTo(DEFAULT);
		assertThat(LangNormalizer.normalize("   ", SUPPORTED, DEFAULT)).isEqualTo(DEFAULT);
	}

	@Test
	@DisplayName("defaultLang 為 null 拋 IAE")
	void nullDefaultThrows() {
		assertThatIllegalArgumentException().isThrownBy(() -> LangNormalizer.normalize("en", SUPPORTED, null));
	}

	@Test
	@DisplayName("defaultLang 為 blank 拋 IAE")
	void blankDefaultThrows() {
		assertThatIllegalArgumentException().isThrownBy(() -> LangNormalizer.normalize("en", SUPPORTED, "  "));
	}

	@Test
	@DisplayName("supportedLangs 為 null 拋 IAE")
	void nullSupportedThrows() {
		assertThatIllegalArgumentException().isThrownBy(() -> LangNormalizer.normalize("en", null, DEFAULT));
	}

	@Test
	@DisplayName("empty supportedLangs：永遠回 default")
	void emptySupportedAlwaysDefault() {
		assertThat(LangNormalizer.normalize("en", Collections.emptySet(), DEFAULT)).isEqualTo(DEFAULT);
	}

	@Test
	@DisplayName("supportedSet() 回 immutable")
	void supportedSetIsImmutable() {
		Set<String> set = LangNormalizer.supportedSet("a", "b");
		assertThat(set).containsExactlyInAnyOrder("a", "b");
		org.junit.jupiter.api.Assertions.assertThrows(UnsupportedOperationException.class, () -> set.add("c"));
	}

	@Test
	@DisplayName("supportedSet(null / empty) 回 empty set")
	void supportedSetNullOrEmpty() {
		assertThat(LangNormalizer.supportedSet((String[]) null)).isEmpty();
		assertThat(LangNormalizer.supportedSet()).isEmpty();
	}

	@Test
	@DisplayName("常數值正確")
	void constants() {
		assertThat(LangNormalizer.ZH_TW).isEqualTo("zh-TW");
		assertThat(LangNormalizer.ZH_CN).isEqualTo("zh-CN");
		assertThat(LangNormalizer.EN).isEqualTo("en");
		assertThat(LangNormalizer.EN_US).isEqualTo("en-US");
	}

}
