package com.taipei.iot.auth.provider.crypto;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Encrypts/decrypts tenant auth configuration JSON using AES-256-GCM.
 * <p>
 * Storage format: base64( IV[12] || ciphertext || GCM-tag[16] )
 */
@Component
@Slf4j
public class AuthConfigEncryptor {

	private static final String ALGORITHM = "AES/GCM/NoPadding";

	private static final int GCM_IV_LENGTH = 12;

	private static final int GCM_TAG_LENGTH = 128; // bits

	@Value("${app.auth.config-secret-key:}")
	private String secretKeyBase64;

	private SecretKeySpec secretKey;

	private final SecureRandom secureRandom = new SecureRandom();

	@PostConstruct
	void init() {
		if (secretKeyBase64 == null || secretKeyBase64.isBlank()) {
			log.warn("AUTH_CONFIG_SECRET_KEY not configured. "
					+ "Tenant auth config encryption/decryption will fail if invoked.");
			return;
		}
		byte[] keyBytes = Base64.getDecoder().decode(secretKeyBase64);
		if (keyBytes.length != 32) {
			throw new IllegalArgumentException(
					"AUTH_CONFIG_SECRET_KEY must be 32 bytes (256 bits), got " + keyBytes.length);
		}
		this.secretKey = new SecretKeySpec(keyBytes, "AES");
	}

	public String encrypt(String plainText) {
		requireKey();
		try {
			byte[] iv = new byte[GCM_IV_LENGTH];
			secureRandom.nextBytes(iv);

			Cipher cipher = Cipher.getInstance(ALGORITHM);
			cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));

			byte[] cipherText = cipher.doFinal(plainText.getBytes(java.nio.charset.StandardCharsets.UTF_8));

			// IV || ciphertext+tag
			ByteBuffer buffer = ByteBuffer.allocate(iv.length + cipherText.length);
			buffer.put(iv);
			buffer.put(cipherText);

			return Base64.getEncoder().encodeToString(buffer.array());
		}
		catch (Exception e) {
			throw new IllegalStateException("Encryption failed", e);
		}
	}

	public String decrypt(String encryptedBase64) {
		if (encryptedBase64 == null || encryptedBase64.isBlank()) {
			return null;
		}
		requireKey();
		try {
			byte[] decoded = Base64.getDecoder().decode(encryptedBase64);

			ByteBuffer buffer = ByteBuffer.wrap(decoded);
			byte[] iv = new byte[GCM_IV_LENGTH];
			buffer.get(iv);
			byte[] cipherText = new byte[buffer.remaining()];
			buffer.get(cipherText);

			Cipher cipher = Cipher.getInstance(ALGORITHM);
			cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));

			byte[] plainText = cipher.doFinal(cipherText);
			return new String(plainText, java.nio.charset.StandardCharsets.UTF_8);
		}
		catch (Exception e) {
			throw new IllegalStateException("Decryption failed", e);
		}
	}

	public boolean isKeyConfigured() {
		return secretKey != null;
	}

	private void requireKey() {
		if (secretKey == null) {
			throw new IllegalStateException(
					"AUTH_CONFIG_SECRET_KEY is not configured. Cannot encrypt/decrypt auth config.");
		}
	}

}
