package com.taipei.iot.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SqlLikeEscaper [common v2 F-13]")
class SqlLikeEscaperTest {

	@Nested
	@DisplayName("escape()")
	class Escape {

		@Test
		@DisplayName("null 回傳 null")
		void nullReturnsNull() {
			assertThat(SqlLikeEscaper.escape(null)).isNull();
		}

		@Test
		@DisplayName("空字串維持空字串")
		void emptyReturnsEmpty() {
			assertThat(SqlLikeEscaper.escape("")).isEmpty();
		}

		@Test
		@DisplayName("純文字不變")
		void plainTextUnchanged() {
			assertThat(SqlLikeEscaper.escape("hello world")).isEqualTo("hello world");
		}

		@Test
		@DisplayName("escape %")
		void escapesPercent() {
			assertThat(SqlLikeEscaper.escape("50%")).isEqualTo("50\\%");
		}

		@Test
		@DisplayName("escape _")
		void escapesUnderscore() {
			assertThat(SqlLikeEscaper.escape("a_b")).isEqualTo("a\\_b");
		}

		@Test
		@DisplayName("escape backslash")
		void escapesBackslash() {
			assertThat(SqlLikeEscaper.escape("a\\b")).isEqualTo("a\\\\b");
		}

		@Test
		@DisplayName("混合 %、_、\\ 同時 escape")
		void escapesMixed() {
			assertThat(SqlLikeEscaper.escape("50%_a\\b")).isEqualTo("50\\%\\_a\\\\b");
		}

		@Test
		@DisplayName("backslash 必須先 escape，避免後續加入的 \\ 又被 escape 一次")
		void backslashOrdering() {
			// 原始輸入 "\_" → 預期 "\\\_"（不是 "\\\\_"）
			// 若順序錯，會先 "\_" → "\\_" → "\\\\_"，造成過度跳脫
			assertThat(SqlLikeEscaper.escape("\\_")).isEqualTo("\\\\\\_");
		}

	}

	@Nested
	@DisplayName("contains()")
	class Contains {

		@Test
		@DisplayName("null 回傳 null")
		void nullReturnsNull() {
			assertThat(SqlLikeEscaper.contains(null)).isNull();
		}

		@Test
		@DisplayName("以 % 包圍 escape 後字串")
		void wrapsWithPercent() {
			assertThat(SqlLikeEscaper.contains("foo")).isEqualTo("%foo%");
		}

		@Test
		@DisplayName("特殊字元 escape + wrap")
		void wrapsAndEscapes() {
			assertThat(SqlLikeEscaper.contains("50%")).isEqualTo("%50\\%%");
		}

	}

	@Test
	@DisplayName("DEFAULT_ESCAPE_CHAR = '\\\\'")
	void defaultEscapeChar() {
		assertThat(SqlLikeEscaper.DEFAULT_ESCAPE_CHAR).isEqualTo('\\');
	}

}
