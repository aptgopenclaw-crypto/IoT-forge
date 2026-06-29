package com.taipei.iot.announcement.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HtmlSanitizerServiceTest {

	private final HtmlSanitizerService service = new HtmlSanitizerService();

	@Test
	void sanitize_null_returnsEmpty() {
		assertEquals("", service.sanitize(null));
		assertEquals("", service.sanitize(""));
		assertEquals("", service.sanitize("   "));
	}

	@Test
	void sanitize_keepsAllowedTags() {
		String html = "<p>Hello <b>bold</b> <i>italic</i></p>";
		String result = service.sanitize(html);
		assertTrue(result.contains("<p>"));
		assertTrue(result.contains("<b>bold</b>"));
		assertTrue(result.contains("<i>italic</i>"));
	}

	@Test
	void sanitize_keepsListsAndHeadings() {
		String html = "<h2>Title</h2><ul><li>a</li><li>b</li></ul>";
		String result = service.sanitize(html);
		assertTrue(result.contains("<h2>Title</h2>"));
		assertTrue(result.contains("<ul>"));
		assertTrue(result.contains("<li>a</li>"));
	}

	@Test
	void sanitize_stripsScriptTag() {
		String html = "<p>safe</p><script>alert('xss')</script>";
		String result = service.sanitize(html);
		assertFalse(result.contains("<script"), "script must be stripped");
		assertFalse(result.toLowerCase().contains("alert("), "script content must be stripped");
		assertTrue(result.contains("safe"));
	}

	@Test
	void sanitize_stripsIframeAndObject() {
		String html = "<iframe src='evil'></iframe><object data='x'></object><p>ok</p>";
		String result = service.sanitize(html);
		assertFalse(result.contains("<iframe"));
		assertFalse(result.contains("<object"));
		assertTrue(result.contains("ok"));
	}

	@Test
	void sanitize_stripsOnEventAttributes() {
		String html = "<a href=\"http://e.com\" onclick=\"alert('x')\">link</a>";
		String result = service.sanitize(html);
		assertFalse(result.contains("onclick"), "on* attributes must be stripped");
		assertTrue(result.contains("href"));
	}

	@Test
	void sanitize_blocksJavascriptProtocol() {
		String html = "<a href=\"javascript:alert(1)\">x</a>";
		String result = service.sanitize(html);
		assertFalse(result.toLowerCase().contains("javascript:"), "javascript: protocol must be blocked");
	}

	@Test
	void sanitize_allowsHttpHttpsMailto() {
		String https = service.sanitize("<a href=\"https://example.com\">x</a>");
		String http = service.sanitize("<a href=\"http://example.com\">x</a>");
		String mailto = service.sanitize("<a href=\"mailto:a@b.com\">x</a>");
		assertTrue(https.contains("https://example.com"));
		assertTrue(http.contains("http://example.com"));
		// OWASP 會把 mailto 內的 @ 編碼為 &#64; (HTML entity)，瀏覽器仍會正常解析
		assertTrue(mailto.startsWith("<a href=\"mailto:"), "mailto: 必須被保留");
		assertTrue(mailto.contains("b.com"));
	}

	@Test
	void extractText_stripsAllTags() {
		String html = "<p>Hello <b>World</b></p><script>x</script>";
		String text = service.extractText(html);
		assertEquals("Hello World", text);
	}

	@Test
	void extractText_collapsesWhitespace() {
		String html = "<p>A  </p>\n\n<p>  B</p>";
		String text = service.extractText(html);
		assertEquals("A B", text);
	}

	@Test
	void extractText_decodesEntities() {
		String html = "<p>Tom &amp; Jerry</p>";
		String text = service.extractText(html);
		assertEquals("Tom & Jerry", text);
	}

	@Test
	void extractText_emptyForBlank() {
		assertEquals("", service.extractText(null));
		assertEquals("", service.extractText("   "));
	}

}
