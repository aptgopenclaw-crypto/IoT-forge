package com.taipei.iot.common.util;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.taipei.iot.common.enums.SecurityEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.event.KeyValuePair;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SecurityLogger sanitize() [common v2 N-1]")
class SecurityLoggerTest {

	private Logger securityLogger;

	private ListAppender<ILoggingEvent> appender;

	@BeforeEach
	void setUp() {
		securityLogger = (Logger) LoggerFactory.getLogger("SECURITY");
		appender = new ListAppender<>();
		appender.start();
		securityLogger.addAppender(appender);
	}

	@AfterEach
	void tearDown() {
		securityLogger.detachAppender(appender);
		appender.stop();
	}

	private String lastMessage() {
		assertThat(appender.list).isNotEmpty();
		ILoggingEvent event = appender.list.get(appender.list.size() - 1);
		return event.getFormattedMessage();
	}

	@Test
	@DisplayName("\\r \\n \\t 全部被移除")
	void stripsCrLfTab() {
		SecurityLogger.warn(SecurityEvent.LOGIN_FAILED, "1.2.3.4\r\n[SECURITY] FAKE", "email=a\tb");
		String msg = lastMessage();
		assertThat(msg).doesNotContain("\r").doesNotContain("\n").doesNotContain("\t");
		assertThat(msg).contains("1.2.3.4[SECURITY] FAKE");
		assertThat(msg).contains("email=ab");
	}

	@Test
	@DisplayName("U+2028 LINE SEPARATOR 被移除")
	void stripsLineSeparator() {
		SecurityLogger.warn(SecurityEvent.LOGIN_FAILED, "1.2.3.4\u2028injected", "k=v");
		String msg = lastMessage();
		assertThat(msg).doesNotContain("\u2028");
		assertThat(msg).contains("1.2.3.4injected");
	}

	@Test
	@DisplayName("U+2029 PARAGRAPH SEPARATOR 被移除")
	void stripsParagraphSeparator() {
		SecurityLogger.warn(SecurityEvent.LOGIN_FAILED, "ip", "email=a\u2029b@x.com");
		String msg = lastMessage();
		assertThat(msg).doesNotContain("\u2029");
		assertThat(msg).contains("email=ab@x.com");
	}

	@Test
	@DisplayName("U+0000 NULL byte 被移除")
	void stripsNullByte() {
		SecurityLogger.warn(SecurityEvent.LOGIN_FAILED, "1.2.3.4\u0000truncated", "k=v");
		String msg = lastMessage();
		assertThat(msg).doesNotContain("\u0000");
		assertThat(msg).contains("1.2.3.4truncated");
	}

	@Test
	@DisplayName("多種控制字元混合一次清理")
	void stripsAllControlCharsMixed() {
		String evil = "x\r\ny\t\u2028z\u2029w\u0000q";
		SecurityLogger.warn(SecurityEvent.LOGIN_FAILED, evil, "k=" + evil);
		String msg = lastMessage();
		assertThat(msg).doesNotContain("\r")
			.doesNotContain("\n")
			.doesNotContain("\t")
			.doesNotContain("\u2028")
			.doesNotContain("\u2029")
			.doesNotContain("\u0000");
		// 字面內容應全部保留（僅控制字元被剝除）
		assertThat(msg).contains("xyzwq");
	}

	@Test
	@DisplayName("null 輸入轉為字面 \"null\"")
	void nullBecomesLiteralNull() {
		SecurityLogger.warn(SecurityEvent.LOGIN_FAILED, null, (String[]) null);
		String msg = lastMessage();
		assertThat(msg).contains("ip=null");
	}

	@Test
	@DisplayName("純文字不受影響")
	void plainTextUnchanged() {
		SecurityLogger.info(SecurityEvent.LOGIN_FAILED, "10.0.0.1", "email=user@example.com");
		String msg = lastMessage();
		assertThat(msg).contains("ip=10.0.0.1").contains("email=user@example.com");
	}

	@Test
	@DisplayName("warn 級別輸出 WARN event")
	void warnEmitsWarnLevel() {
		SecurityLogger.warn(SecurityEvent.LOGIN_FAILED, "1.1.1.1");
		assertThat(appender.list).hasSize(1);
		assertThat(appender.list.get(0).getLevel()).isEqualTo(Level.WARN);
	}

	@Test
	@DisplayName("info 級別輸出 INFO event")
	void infoEmitsInfoLevel() {
		SecurityLogger.info(SecurityEvent.LOGIN_FAILED, "1.1.1.1");
		assertThat(appender.list).hasSize(1);
		assertThat(appender.list.get(0).getLevel()).isEqualTo(Level.INFO);
	}

	// ─── F-7: 結構化 JSON key-value pairs 測試 ──────────────────────────────

	@Nested
	@DisplayName("F-7 Structured key-value pairs")
	class StructuredOutputTest {

		private Map<String, Object> lastKeyValues() {
			assertThat(appender.list).isNotEmpty();
			ILoggingEvent event = appender.list.get(appender.list.size() - 1);
			List<KeyValuePair> kvs = event.getKeyValuePairs();
			assertThat(kvs).isNotNull().isNotEmpty();
			return kvs.stream().collect(Collectors.toMap(kv -> kv.key, kv -> kv.value));
		}

		@Test
		@DisplayName("warn 附帶 security_event / security_ip / security_category 欄位")
		void warnAttachesBaseFields() {
			SecurityLogger.warn(SecurityEvent.RATE_LIMITED, "10.0.0.1");
			Map<String, Object> kv = lastKeyValues();

			assertThat(kv).containsEntry("security_event", "RATE_LIMITED");
			assertThat(kv).containsEntry("security_ip", "10.0.0.1");
			assertThat(kv).containsEntry("security_category", "SECURITY");
		}

		@Test
		@DisplayName("info 附帶 security_event / security_ip / security_category 欄位")
		void infoAttachesBaseFields() {
			SecurityLogger.info(SecurityEvent.PASSWORD_RESET_REQUEST, "192.168.1.1");
			Map<String, Object> kv = lastKeyValues();

			assertThat(kv).containsEntry("security_event", "PASSWORD_RESET_REQUEST");
			assertThat(kv).containsEntry("security_ip", "192.168.1.1");
			assertThat(kv).containsEntry("security_category", "SECURITY");
		}

		@Test
		@DisplayName("details 中的 key=value 拆解為 security_{key} 欄位")
		void detailsKeyValueParsedAsStructuredFields() {
			SecurityLogger.warn(SecurityEvent.LOGIN_FAILED, "1.2.3.4", "email=test@x.com", "reason=bad_password");
			Map<String, Object> kv = lastKeyValues();

			assertThat(kv).containsEntry("security_email", "test@x.com");
			assertThat(kv).containsEntry("security_reason", "bad_password");
		}

		@Test
		@DisplayName("details 中無 '=' 的字串以 security_detail_N 欄位命名")
		void detailsWithoutEqualsGetIndexedName() {
			SecurityLogger.warn(SecurityEvent.SUSPICIOUS_INPUT, "1.1.1.1", "some-unstructured-detail");
			Map<String, Object> kv = lastKeyValues();

			assertThat(kv).containsEntry("security_detail_0", "some-unstructured-detail");
		}

		@Test
		@DisplayName("ip 中的控制字元在 structured 欄位中也被清理")
		void structuredIpIsSanitized() {
			SecurityLogger.warn(SecurityEvent.LOGIN_FAILED, "1.2.3.4\r\nevil");
			Map<String, Object> kv = lastKeyValues();

			assertThat(kv).containsEntry("security_ip", "1.2.3.4evil");
		}

		@Test
		@DisplayName("多個 key=value details 全部解析")
		void multipleKeyValueDetailsAllParsed() {
			SecurityLogger.warn(SecurityEvent.RATE_LIMITED, "10.0.0.1", "endpoint=login", "count=11/10", "period=60s");
			Map<String, Object> kv = lastKeyValues();

			assertThat(kv).containsEntry("security_endpoint", "login");
			assertThat(kv).containsEntry("security_count", "11/10");
			assertThat(kv).containsEntry("security_period", "60s");
		}

		@Test
		@DisplayName("null details 不產生 NPE，只有 base 欄位")
		void nullDetailsDoesNotThrow() {
			SecurityLogger.warn(SecurityEvent.ACCESS_DENIED, "10.0.0.1", (String[]) null);
			Map<String, Object> kv = lastKeyValues();

			assertThat(kv).containsEntry("security_event", "ACCESS_DENIED");
			assertThat(kv).hasSize(3); // only base fields
		}

	}

}
