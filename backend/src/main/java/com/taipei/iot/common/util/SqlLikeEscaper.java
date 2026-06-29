package com.taipei.iot.common.util;

/**
 * SQL {@code LIKE} 子句關鍵字 escape 工具，避免使用者輸入的 {@code %} / {@code _} / {@code \} 在 LIKE
 * 比對中被當成 wildcard 而擴大查詢範圍（甚至造成效能問題）。
 *
 * <p>
 * 搭配 JPQL / native SQL 的 {@code LIKE :keyword ESCAPE '\'}（PostgreSQL 預設即 {@code \}）使用。
 *
 * <h2>使用範例</h2> <pre>{@code
 *   String safe = "%" + SqlLikeEscaper.escape(keyword) + "%";
 *   repo.findByTitleLike(safe);
 * }</pre>
 *
 * <p>
 * [common v2 F-13]
 */
public final class SqlLikeEscaper {

	/** 預設使用的 escape 字元（PostgreSQL / H2 / MySQL 預設皆為 {@code \}）。 */
	public static final char DEFAULT_ESCAPE_CHAR = '\\';

	private SqlLikeEscaper() {
	}

	/**
	 * Escape LIKE 子句中具特殊意義的字元：{@code \} / {@code %} / {@code _}。
	 * <p>
	 * 順序敏感：必須先 escape 反斜線本身，否則會把之後加入的 escape 字元再次跳脫。
	 * @param input 來自使用者的原始字串；{@code null} 回傳 {@code null}
	 * @return escape 後的字串
	 */
	public static String escape(String input) {
		if (input == null) {
			return null;
		}
		return input.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
	}

	/**
	 * 便利方法：等同於 {@code "%" + escape(input) + "%"}（contains 樣式）。
	 * <p>
	 * {@code null} input 回傳 {@code null}（呼叫端決定是否退化為「不過濾」）。
	 */
	public static String contains(String input) {
		if (input == null) {
			return null;
		}
		return "%" + escape(input) + "%";
	}

}
