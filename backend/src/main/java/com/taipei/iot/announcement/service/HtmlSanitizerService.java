package com.taipei.iot.announcement.service;

import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;
import org.springframework.stereotype.Service;
import org.jsoup.Jsoup;

import java.util.regex.Pattern;

/**
 * 公告富文本內容安全處理。
 *
 * <p>
 * 白名單策略：
 * <ul>
 * <li>允許標籤：{@code
 *
<p>
 *  <br>
 *  <b> <strong> <i> <em> <u>
 *
<ul>
 *
 *
<ol>
 *
 *
<li>
 *
<h1>-
 *
<h4> <blockquote> <a>}</li>
 * <li>{@code <a>} 僅允許 href（http / https / mailto）並自動補上 rel="noopener noreferrer"
 * target="_blank"</li>
 * <li>禁止：{@code <script> <iframe> <object> <embed> <style>} 與任何 on* 事件屬性</li>
 * </ul>
 *
 * <p>
 * 同時提供純文字萃取（用於關鍵字搜尋的 contentText 欄位），避免搜尋命中 HTML 標籤本身。
 */
@Service
public class HtmlSanitizerService {

	private static final PolicyFactory POLICY = new HtmlPolicyBuilder()
		.allowElements("p", "br", "b", "strong", "i", "em", "u", "ul", "ol", "li", "blockquote", "h1", "h2", "h3", "h4",
				"a")
		.allowAttributes("href")
		.onElements("a")
		.allowStandardUrlProtocols() // http, https, mailto
		.requireRelNofollowOnLinks()
		.toFactory();

	/** 多餘空白壓縮（純文字萃取後使用） */
	private static final Pattern WS = Pattern.compile("\\s+");

	/**
	 * 套用白名單清洗 HTML。
	 * @param html 使用者送來的原始 HTML（可能含惡意內容）
	 * @return 安全可直接渲染的 HTML；若輸入為 null 或空白回傳空字串
	 */
	public String sanitize(String html) {
		if (html == null || html.isBlank())
			return "";
		return POLICY.sanitize(html);
	}

	/**
	 * 從 HTML 萃取純文字版本，供關鍵字搜尋使用。
	 * <p>
	 * 使用 Jsoup（透過 owasp-java-html-sanitizer 的傳遞依賴）解析後取 text()。
	 * @param html 已 sanitized 過的 HTML 或原始輸入
	 * @return 壓縮過空白的純文字；若輸入為 null/空白回傳空字串
	 */
	public String extractText(String html) {
		if (html == null || html.isBlank())
			return "";
		// Jsoup.parse 會自動解碼 HTML entities，再透過 text() 取得純文字
		String text = Jsoup.parse(html).text();
		return WS.matcher(text).replaceAll(" ").trim();
	}

}
