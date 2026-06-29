package com.taipei.iot.auth.service.impl;

import com.taipei.iot.auth.dto.response.CaptchaResponse;
import com.taipei.iot.auth.service.CaptchaService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.CubicCurve2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@Profile("!test")
@RequiredArgsConstructor
public class CaptchaServiceImpl implements CaptchaService {

	private final StringRedisTemplate stringRedisTemplate;

	@Value("${captcha.ttl:300}")
	private int captchaTtl;

	@Value("${captcha.length:4}")
	private int captchaLength;

	@Value("${captcha.skip-verification:false}")
	private boolean skipVerification;

	private static final String CAPTCHA_PREFIX = "captcha:";

	private static final String CHARS = "0123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz";

	private static final SecureRandom RANDOM = new SecureRandom();

	@Override
	public CaptchaResponse generate() {
		String text = generateRandomText();
		String captchaKey = UUID.randomUUID().toString();
		String image = generateImage(text);

		stringRedisTemplate.opsForValue()
			.set(CAPTCHA_PREFIX + captchaKey, text.toLowerCase(), captchaTtl, TimeUnit.SECONDS);

		return CaptchaResponse.builder().captchaKey(captchaKey).captchaImage("data:image/png;base64," + image).build();
	}

	@Override
	public boolean verify(String captchaKey, String captchaValue) {
		if (skipVerification) {
			return true;
		}

		String key = CAPTCHA_PREFIX + captchaKey;
		String stored = stringRedisTemplate.opsForValue().get(key);

		// Delete the key regardless of result (one-time use)
		stringRedisTemplate.delete(key);

		if (stored == null || captchaValue == null) {
			return false;
		}

		// [v2 N-1] 使用常數時間比對，避免 short-circuit 比對被 timing attack 透視逋字符。
		// 先統一轉為 upper-case（保留原 case-insensitive 語意）再以 byte array 交由
		// MessageDigest.isEqual 進行常數時間比對。長度不同本身不是機密（captcha 長度固定），
		// 可提早 return。
		byte[] a = stored.toUpperCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8);
		byte[] b = captchaValue.toUpperCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8);
		if (a.length != b.length) {
			return false;
		}
		return MessageDigest.isEqual(a, b);
	}

	private String generateRandomText() {
		StringBuilder sb = new StringBuilder(captchaLength);
		for (int i = 0; i < captchaLength; i++) {
			sb.append(CHARS.charAt(RANDOM.nextInt(CHARS.length())));
		}
		return sb.toString();
	}

	private String generateImage(String text) {
		int width = 160;
		int height = 50;
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = image.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

		// ---- 背景：隨機漸變色塊，打亂 OCR 的二值化處理 ----
		for (int x = 0; x < width; x += 4) {
			for (int y = 0; y < height; y += 4) {
				g.setColor(new Color(220 + RANDOM.nextInt(36), 220 + RANDOM.nextInt(36), 220 + RANDOM.nextInt(36)));
				g.fillRect(x, y, 4, 4);
			}
		}

		// ---- 粗曲線干擾（貝茲曲線）：穿過文字區域，與文字顏色接近，干擾 OCR 分割 ----
		for (int i = 0; i < 3; i++) {
			g.setColor(new Color(RANDOM.nextInt(120), RANDOM.nextInt(120), RANDOM.nextInt(120)));
			g.setStroke(new BasicStroke(1.5f + RANDOM.nextFloat() * 1.5f));
			CubicCurve2D curve = new CubicCurve2D.Double(0, RANDOM.nextInt(height), RANDOM.nextInt(width),
					RANDOM.nextInt(height), RANDOM.nextInt(width), RANDOM.nextInt(height), width,
					RANDOM.nextInt(height));
			g.draw(curve);
		}

		// ---- 細直線干擾：較淡的背景線條 ----
		for (int i = 0; i < 8; i++) {
			g.setColor(new Color(160 + RANDOM.nextInt(60), 160 + RANDOM.nextInt(60), 160 + RANDOM.nextInt(60)));
			g.setStroke(new BasicStroke(1.0f));
			g.drawLine(RANDOM.nextInt(width), RANDOM.nextInt(height), RANDOM.nextInt(width), RANDOM.nextInt(height));
		}

		// ---- 繪製文字：每個字元隨機旋轉、偏移、變色、變大小 ----
		String[] fontNames = { "Arial", "Verdana", "Tahoma", "Georgia", "Courier New" };
		int charSpacing = width / (text.length() + 1);
		for (int i = 0; i < text.length(); i++) {
			// 隨機字型與大小（26~34px），人眼可讀但 OCR 難以統一識別
			Font font = new Font(fontNames[RANDOM.nextInt(fontNames.length)], Font.BOLD, 26 + RANDOM.nextInt(9));
			g.setFont(font);

			// 深色系文字（0~80），確保人眼可辨識
			g.setColor(new Color(RANDOM.nextInt(80), RANDOM.nextInt(80), RANDOM.nextInt(80)));

			// 隨機旋轉 -20° ~ +20°，打亂 OCR 字元對齊
			AffineTransform original = g.getTransform();
			double angle = Math.toRadians(-20 + RANDOM.nextInt(41));
			int x = charSpacing * (i + 1) - 8 + RANDOM.nextInt(6);
			int y = 30 + RANDOM.nextInt(10);
			g.rotate(angle, x, y - 10);
			g.drawString(String.valueOf(text.charAt(i)), x, y);
			g.setTransform(original);
		}

		// ---- 噪點：大小與顏色隨機，部分與文字色接近 ----
		for (int i = 0; i < 60; i++) {
			int x = RANDOM.nextInt(width);
			int y = RANDOM.nextInt(height);
			int size = 1 + RANDOM.nextInt(3);
			g.setColor(new Color(RANDOM.nextInt(180), RANDOM.nextInt(180), RANDOM.nextInt(180)));
			g.fillOval(x, y, size, size);
		}

		// ---- 文字上方疊加半透明曲線，增加 OCR 去噪難度 ----
		for (int i = 0; i < 2; i++) {
			g.setColor(new Color(RANDOM.nextInt(100), RANDOM.nextInt(100), RANDOM.nextInt(100), 80));
			g.setStroke(new BasicStroke(2.0f));
			CubicCurve2D curve = new CubicCurve2D.Double(0, 10 + RANDOM.nextInt(30), RANDOM.nextInt(width),
					RANDOM.nextInt(height), RANDOM.nextInt(width), RANDOM.nextInt(height), width,
					10 + RANDOM.nextInt(30));
			g.draw(curve);
		}

		g.dispose();

		try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
			ImageIO.write(image, "png", baos);
			return Base64.getEncoder().encodeToString(baos.toByteArray());
		}
		catch (Exception e) {
			throw new RuntimeException("Failed to generate captcha image", e);
		}
	}

}
