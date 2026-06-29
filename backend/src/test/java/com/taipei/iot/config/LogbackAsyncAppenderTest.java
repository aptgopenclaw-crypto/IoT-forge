package com.taipei.iot.config;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * [Config v2 N-6] 驗證 logback-spring.xml 的 SECURITY 和 AUDIT logger 使用 AsyncAppender， 且
 * discardingThreshold=0（安全/審計事件不允許丟棄）。
 */
class LogbackAsyncAppenderTest {

	private static Document doc;

	@BeforeAll
	static void loadLogbackXml() throws Exception {
		try (InputStream is = LogbackAsyncAppenderTest.class.getResourceAsStream("/logback-spring.xml")) {
			assertThat(is).as("logback-spring.xml should exist on classpath").isNotNull();
			doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(is);
		}
	}

	@Test
	void asyncSecurityFile_appenderExists() {
		Element appender = findAppenderByName("ASYNC_SECURITY_FILE");
		assertThat(appender).as("ASYNC_SECURITY_FILE appender should exist").isNotNull();
		assertThat(appender.getAttribute("class")).contains("AsyncAppender");
	}

	@Test
	void asyncSecurityFile_discardingThresholdIsZero() {
		Element appender = findAppenderByName("ASYNC_SECURITY_FILE");
		String threshold = getChildText(appender, "discardingThreshold");
		assertThat(threshold).isEqualTo("0");
	}

	@Test
	void asyncSecurityFile_queueSizeIsReasonable() {
		Element appender = findAppenderByName("ASYNC_SECURITY_FILE");
		int queueSize = Integer.parseInt(getChildText(appender, "queueSize"));
		assertThat(queueSize).isGreaterThanOrEqualTo(256);
	}

	@Test
	void asyncAuditFile_appenderExists() {
		Element appender = findAppenderByName("ASYNC_AUDIT_FILE");
		assertThat(appender).as("ASYNC_AUDIT_FILE appender should exist").isNotNull();
		assertThat(appender.getAttribute("class")).contains("AsyncAppender");
	}

	@Test
	void asyncAuditFile_discardingThresholdIsZero() {
		Element appender = findAppenderByName("ASYNC_AUDIT_FILE");
		String threshold = getChildText(appender, "discardingThreshold");
		assertThat(threshold).isEqualTo("0");
	}

	@Test
	void asyncAuditFile_queueSizeIsReasonable() {
		Element appender = findAppenderByName("ASYNC_AUDIT_FILE");
		int queueSize = Integer.parseInt(getChildText(appender, "queueSize"));
		assertThat(queueSize).isGreaterThanOrEqualTo(256);
	}

	private Element findAppenderByName(String name) {
		NodeList appenders = doc.getElementsByTagName("appender");
		for (int i = 0; i < appenders.getLength(); i++) {
			Element el = (Element) appenders.item(i);
			if (name.equals(el.getAttribute("name"))) {
				return el;
			}
		}
		return null;
	}

	private String getChildText(Element parent, String tagName) {
		NodeList children = parent.getElementsByTagName(tagName);
		assertThat(children.getLength()).as(tagName + " element should exist").isGreaterThan(0);
		return children.item(0).getTextContent().trim();
	}

}
