package com.taipei.iot.auth.service;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.mail.MailSenderAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.context.TestPropertySource;

import java.util.Properties;
import java.util.UUID;

/**
 * Live SMTP integration test for {@link PasswordResetMailService}.
 *
 * <p>
 * <b>Disabled by default.</b> Performs a real outbound SMTP delivery against Office 365 —
 * running it in CI would spam an external mailbox and require live credentials. To
 * execute manually:
 *
 * <pre>{@code
 * export O_MAILUSER=fetprivatenetwork@feto365.tw
 * export O_MAILPASS='<smtp-password>'
 * cd backend && mvn -o test \
 *     -Dtest=PasswordResetMailServiceLiveIT \
 *     -DfailIfNoTests=false \
 *     -Dsurefire.failIfNoSpecifiedTests=false
 * }</pre>
 *
 * <p>
 * The recipient is hard-coded to {@code kevinchang4@fareastone.com.tw} per the reviewer's
 * request; change {@link #RECIPIENT} for ad-hoc testing.
 */
@Disabled("Live SMTP — enable manually after exporting O_MAILUSER / O_MAILPASS")
@SpringBootTest(classes = PasswordResetMailServiceLiveIT.TestConfig.class,
		webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import(PasswordResetMailService.class)
@TestPropertySource(properties = { "spring.main.web-application-type=none",
		"app.frontend-base-url=http://localhost:5173",
		// The values below mirror application-dev.yml; environment variables override
		// them.
		"spring.mail.host=${O_MAILSER:smtp.office365.com}", "spring.mail.port=${O_MAILPORT:587}",
		"spring.mail.username=${O_MAILUSER:fetprivatenetwork@feto365.tw}", "spring.mail.password=${O_MAILPASS:}",
		"spring.mail.properties.mail.smtp.auth=true", "spring.mail.properties.mail.smtp.starttls.enable=true" })
class PasswordResetMailServiceLiveIT {

	private static final String RECIPIENT = "kevinchang4@fareastone.com.tw";

	@Autowired
	private PasswordResetMailService passwordResetMailService;

	@Test
	void sendsRealPasswordResetEmail() {
		// The token is a throw-away UUID — the test verifies delivery, not redemption.
		String token = "live-it-" + UUID.randomUUID();
		passwordResetMailService.send(RECIPIENT, "Kevin Chang", token);
		// No assertion on SMTP success — PasswordResetMailService swallows
		// MessagingException and only logs. Check the test log output and the
		// recipient mailbox to confirm delivery.
	}

	/**
	 * Minimal Spring context that picks up Spring Boot's mail auto-configuration (which
	 * is intentionally excluded in application-test.yml). Importing it explicitly here
	 * keeps the live test self-contained.
	 */
	@Configuration
	@EnableAutoConfiguration
	@Import(MailSenderAutoConfiguration.class)
	static class TestConfig {

		/**
		 * Provides a {@link JavaMailSender} that is configured exclusively from the
		 * {@link TestPropertySource} above so the test does not depend on
		 * application-dev.yml being on the test classpath.
		 */
		@Bean
		JavaMailSender javaMailSender(
				@org.springframework.beans.factory.annotation.Value("${spring.mail.host}") String host,
				@org.springframework.beans.factory.annotation.Value("${spring.mail.port}") int port,
				@org.springframework.beans.factory.annotation.Value("${spring.mail.username}") String username,
				@org.springframework.beans.factory.annotation.Value("${spring.mail.password}") String password) {
			JavaMailSenderImpl sender = new JavaMailSenderImpl();
			sender.setHost(host);
			sender.setPort(port);
			sender.setUsername(username);
			sender.setPassword(password);
			Properties props = sender.getJavaMailProperties();
			props.put("mail.transport.protocol", "smtp");
			props.put("mail.smtp.auth", "true");
			props.put("mail.smtp.starttls.enable", "true");
			return sender;
		}

	}

}
