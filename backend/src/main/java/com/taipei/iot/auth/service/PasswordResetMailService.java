package com.taipei.iot.auth.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetMailService {

	private final JavaMailSender mailSender;

	@Value("${app.frontend-base-url}")
	private String frontendBaseUrl;

	@Value("${spring.mail.username:noreply@example.com}")
	private String fromAddress;

	public void send(String toEmail, String displayName, String token) {
		String resetUrl = frontendBaseUrl + "/reset-password?token=" + token;
		String subject = "[台北市路燈平台] 密碼重設通知";
		String html = buildHtml(displayName, resetUrl);

		try {
			MimeMessage mimeMessage = mailSender.createMimeMessage();
			MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, "UTF-8");
			helper.setFrom(fromAddress);
			helper.setTo(toEmail);
			helper.setSubject(subject);
			helper.setText(html, true);
			mailSender.send(mimeMessage);
			log.info("Password reset email sent to {}", toEmail);
		}
		catch (MessagingException e) {
			log.error("Failed to send password reset email to {}: {}", toEmail, e.getMessage());
		}
	}

	private String buildHtml(String displayName, String resetUrl) {
		String name = (displayName != null && !displayName.isBlank()) ? displayName : "使用者";
		return """
				<!DOCTYPE html>
				<html lang="zh-TW">
				<head><meta charset="UTF-8"></head>
				<body style="font-family: Arial, sans-serif; line-height: 1.6; color: #333;">
				  <div style="max-width: 600px; margin: 0 auto; padding: 20px;">
				    <h2 style="color: #1a73e8;">台北市路燈平台 — 密碼重設</h2>
				    <p>%s 您好，</p>
				    <p>我們收到您的密碼重設請求。請點擊下方按鈕重設密碼：</p>
				    <div style="text-align: center; margin: 30px 0;">
				      <a href="%s"
				         style="background-color: #1a73e8; color: #fff; padding: 12px 32px;
				                text-decoration: none; border-radius: 4px; font-size: 16px;">
				        重設密碼
				      </a>
				    </div>
				    <p style="font-size: 14px; color: #666;">
				      此連結將於 <strong>30 分鐘</strong>後失效。若您未提出此請求，請忽略此信件。
				    </p>
				    <hr style="border: none; border-top: 1px solid #eee; margin: 20px 0;">
				    <p style="font-size: 12px; color: #999;">此為系統自動發送，請勿直接回覆。</p>
				  </div>
				</body>
				</html>
				""".formatted(name, resetUrl);
	}

}
