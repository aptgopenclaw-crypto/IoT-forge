package com.taipei.iot.common.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

class NoOpVirusScanServiceTest {

	private final NoOpVirusScanService service = new NoOpVirusScanService();

	private ListAppender<ILoggingEvent> appender;

	private Logger logger;

	@BeforeEach
	void setUp() {
		logger = (Logger) LoggerFactory.getLogger(NoOpVirusScanService.class);
		appender = new ListAppender<>();
		appender.start();
		logger.addAppender(appender);
	}

	@AfterEach
	void tearDown() {
		logger.detachAppender(appender);
	}

	@Test
	void scan_alwaysReturnsClean() {
		assertEquals(VirusScanService.ScanResult.CLEAN, service.scan("/any/path/file.jpg"));
	}

	@Test
	void scan_nullPath_returnsClean() {
		assertEquals(VirusScanService.ScanResult.CLEAN, service.scan(null));
	}

	/** N-2: 啟動時必須 WARN 提示「未真正掃毒」，避免維運誤判。 */
	@Test
	void warnOnStartup_emitsWarnWithProductionGuidance() {
		service.warnOnStartup();

		List<ILoggingEvent> events = appender.list;
		assertThat(events).hasSize(1);
		ILoggingEvent evt = events.get(0);
		assertThat(evt.getLevel()).isEqualTo(Level.WARN);
		String msg = evt.getFormattedMessage();
		assertThat(msg).contains("DISABLED");
		assertThat(msg).contains("CLEAN");
		assertThat(msg).contains("production");
		assertThat(msg).contains("virus-scan.enabled=true");
	}

	/** N-2: @ConditionalOnProperty 必須移除 matchIfMissing，避免設定遺漏時靜默放行。 */
	@Test
	void conditionalOnProperty_doesNotMatchIfMissing() {
		ConditionalOnProperty annotation = NoOpVirusScanService.class.getAnnotation(ConditionalOnProperty.class);
		assertThat(annotation).isNotNull();
		assertThat(annotation.name()).containsExactly("virus-scan.enabled");
		assertThat(annotation.havingValue()).isEqualTo("false");
		assertThat(annotation.matchIfMissing()).as("matchIfMissing 必須為 false，否則 prod 漏設即靜默走 NoOp").isFalse();
	}

	/** N-2: 必須限定僅 dev / test profile 載入。 */
	@Test
	void profile_restrictedToDevAndTest() {
		Profile profile = NoOpVirusScanService.class.getAnnotation(Profile.class);
		assertThat(profile).isNotNull();
		assertThat(profile.value()).containsExactlyInAnyOrder("dev", "test");
	}

	/** N-2: 仍保留 @Service 以利 Spring 掃描。 */
	@Test
	void stillRegisteredAsService() {
		assertThat(NoOpVirusScanService.class.getAnnotation(Service.class)).isNotNull();
	}

}
