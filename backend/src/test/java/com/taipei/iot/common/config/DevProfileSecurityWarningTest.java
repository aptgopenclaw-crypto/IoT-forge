package com.taipei.iot.common.config;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * N-13 驗證：{@link DevProfileSecurityWarning} 啟動時輸出安全警告且受 profile 限定。
 */
class DevProfileSecurityWarningTest {

	private ListAppender<ILoggingEvent> appender;

	private Logger logger;

	@BeforeEach
	void setUp() {
		logger = (Logger) org.slf4j.LoggerFactory.getLogger(DevProfileSecurityWarning.class);
		appender = new ListAppender<>();
		appender.start();
		logger.addAppender(appender);
	}

	@AfterEach
	void tearDown() {
		logger.detachAppender(appender);
	}

	@Test
	void warnPlaintextCredentials_logsSecurityWarning() {
		DevProfileSecurityWarning warning = new DevProfileSecurityWarning();
		warning.warnPlaintextCredentials();

		List<String> warnMessages = appender.list.stream()
			.filter(e -> e.getLevel() == Level.WARN)
			.map(ILoggingEvent::getFormattedMessage)
			.toList();

		assertThat(warnMessages).hasSizeGreaterThanOrEqualTo(5);
		assertThat(warnMessages).anyMatch(m -> m.contains("[SECURITY]"));
		assertThat(warnMessages).anyMatch(m -> m.contains("plaintext credentials"));
		assertThat(warnMessages).anyMatch(m -> m.contains("MUST NOT be used in production"));
	}

	@Test
	void class_hasProfileAnnotation_restrictedToDevAndTest() {
		Profile profile = DevProfileSecurityWarning.class.getAnnotation(Profile.class);
		assertThat(profile).isNotNull();
		assertThat(profile.value()).containsExactlyInAnyOrder("dev", "test");
	}

	@Test
	void class_hasComponentAnnotation() {
		Component component = DevProfileSecurityWarning.class.getAnnotation(Component.class);
		assertThat(component).isNotNull();
	}

	@Test
	void warnMessage_mentionsAllCredentialTypes() {
		DevProfileSecurityWarning warning = new DevProfileSecurityWarning();
		warning.warnPlaintextCredentials();

		String combined = appender.list.stream()
			.map(ILoggingEvent::getFormattedMessage)
			.reduce("", (a, b) -> a + " " + b);

		assertThat(combined).contains("DB");
		assertThat(combined).contains("Redis");
		assertThat(combined).contains("JWT");
		assertThat(combined).contains("SMTP");
	}

}
