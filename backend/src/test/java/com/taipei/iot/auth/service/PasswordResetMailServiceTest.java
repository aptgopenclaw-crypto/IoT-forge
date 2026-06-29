package com.taipei.iot.auth.service;

import jakarta.mail.Address;
import jakarta.mail.Message;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pure unit test for {@link PasswordResetMailService}. Uses a mocked
 * {@link JavaMailSenderImpl} and asserts that the outgoing {@link MimeMessage} carries
 * the expected recipient, subject and reset link.
 */
class PasswordResetMailServiceTest {

	private static final String RECIPIENT = "kevinchang4@fareastone.com.tw";

	private static final String DISPLAY_NAME = "Kevin Chang";

	private static final String TOKEN = "unit-test-token-uuid";

	private static final String FRONTEND_BASE_URL = "https://example.test";

	private static final String FROM_ADDRESS = "noreply@taipei-iot.local";

	private JavaMailSenderImpl mailSender;

	private PasswordResetMailService service;

	@BeforeEach
	void setUp() {
		mailSender = mock(JavaMailSenderImpl.class);
		// Use a real MimeMessage factory so MimeMessageHelper can populate headers.
		when(mailSender.createMimeMessage()).thenAnswer(invocation -> new MimeMessage((jakarta.mail.Session) null));
		doNothing().when(mailSender).send(any(MimeMessage.class));

		service = new PasswordResetMailService(mailSender);
		ReflectionTestUtils.setField(service, "frontendBaseUrl", FRONTEND_BASE_URL);
		ReflectionTestUtils.setField(service, "fromAddress", FROM_ADDRESS);
	}

	@Test
	void send_buildsMimeMessageWithExpectedHeadersAndBody() throws Exception {
		service.send(RECIPIENT, DISPLAY_NAME, TOKEN);

		ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
		verify(mailSender).send(captor.capture());
		MimeMessage sent = captor.getValue();

		// --- Headers ---
		Address[] from = sent.getFrom();
		assertThat(from).hasSize(1);
		assertThat(from[0].toString()).isEqualTo(FROM_ADDRESS);

		Address[] to = sent.getRecipients(Message.RecipientType.TO);
		assertThat(to).hasSize(1);
		assertThat(to[0].toString()).isEqualTo(RECIPIENT);

		assertThat(sent.getSubject()).isEqualTo("[台北市路燈平台] 密碼重設通知");

		// --- Body ---
		String body = extractBody(sent);
		assertThat(body).contains(DISPLAY_NAME)
			.contains(FRONTEND_BASE_URL + "/reset-password?token=" + TOKEN)
			.contains("重設密碼")
			.contains("30 分鐘");
	}

	@Test
	void send_blankDisplayName_fallsBackToGenericGreeting() throws Exception {
		service.send(RECIPIENT, "  ", TOKEN);

		ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
		verify(mailSender).send(captor.capture());
		MimeMessage sent = captor.getValue();

		String body = extractBody(sent);
		assertThat(body).contains("使用者");
	}

	/**
	 * Walk the MIME parts produced by
	 * {@link org.springframework.mail.javamail.MimeMessageHelper} and return the decoded
	 * text/html body as a UTF-8 string.
	 */
	private String extractBody(MimeMessage message) throws Exception {
		Object content = message.getContent();
		if (content instanceof String s) {
			return s;
		}
		if (content instanceof Multipart multipart) {
			return readFirstHtmlPart(multipart);
		}
		throw new IllegalStateException("Unexpected message content: " + content.getClass());
	}

	private String readFirstHtmlPart(Multipart multipart) throws Exception {
		for (int i = 0; i < multipart.getCount(); i++) {
			Part part = multipart.getBodyPart(i);
			Object body = part.getContent();
			if (body instanceof String s && part.isMimeType("text/html")) {
				return s;
			}
			if (body instanceof Multipart nested) {
				String found = readFirstHtmlPart(nested);
				if (found != null) {
					return found;
				}
			}
		}
		return null;
	}

}
