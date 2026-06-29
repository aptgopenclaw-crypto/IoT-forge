package com.taipei.iot.auth.provider.crypto;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class AuthConfigEncryptorTest {

	private AuthConfigEncryptor encryptor;

	@BeforeEach
	void setUp() {
		encryptor = new AuthConfigEncryptor();
		// Generate a valid 32-byte key
		byte[] key = new byte[32];
		for (int i = 0; i < 32; i++)
			key[i] = (byte) (i + 1);
		String base64Key = Base64.getEncoder().encodeToString(key);
		ReflectionTestUtils.setField(encryptor, "secretKeyBase64", base64Key);
		encryptor.init();
	}

	@Test
	void encryptDecrypt_roundTrip() {
		String plainText = "{\"url\":\"ldap://localhost:389\",\"bindDn\":\"cn=admin\",\"password\":\"secret\"}";

		String encrypted = encryptor.encrypt(plainText);
		assertNotNull(encrypted);
		assertNotEquals(plainText, encrypted);

		String decrypted = encryptor.decrypt(encrypted);
		assertEquals(plainText, decrypted);
	}

	@Test
	void encrypt_producesDifferentCiphertextEachTime() {
		String plainText = "test-data";

		String encrypted1 = encryptor.encrypt(plainText);
		String encrypted2 = encryptor.encrypt(plainText);

		// Different IV each time → different ciphertext
		assertNotEquals(encrypted1, encrypted2);

		// Both decrypt to the same value
		assertEquals(plainText, encryptor.decrypt(encrypted1));
		assertEquals(plainText, encryptor.decrypt(encrypted2));
	}

	@Test
	void decrypt_nullOrBlank_returnsNull() {
		assertNull(encryptor.decrypt(null));
		assertNull(encryptor.decrypt(""));
		assertNull(encryptor.decrypt("   "));
	}

	@Test
	void decrypt_tamperedCiphertext_throwsException() {
		String plainText = "sensitive-data";
		String encrypted = encryptor.encrypt(plainText);

		// Tamper with the ciphertext
		byte[] decoded = Base64.getDecoder().decode(encrypted);
		decoded[decoded.length - 1] ^= 0xFF; // Flip last byte (GCM tag)
		String tampered = Base64.getEncoder().encodeToString(decoded);

		assertThrows(IllegalStateException.class, () -> encryptor.decrypt(tampered));
	}

	@Test
	void encrypt_withoutKey_throwsException() {
		AuthConfigEncryptor noKeyEncryptor = new AuthConfigEncryptor();
		ReflectionTestUtils.setField(noKeyEncryptor, "secretKeyBase64", "");
		noKeyEncryptor.init();

		assertThrows(IllegalStateException.class, () -> noKeyEncryptor.encrypt("test"));
	}

	@Test
	void isKeyConfigured_withKey_returnsTrue() {
		assertTrue(encryptor.isKeyConfigured());
	}

	@Test
	void isKeyConfigured_withoutKey_returnsFalse() {
		AuthConfigEncryptor noKeyEncryptor = new AuthConfigEncryptor();
		ReflectionTestUtils.setField(noKeyEncryptor, "secretKeyBase64", "");
		noKeyEncryptor.init();

		assertFalse(noKeyEncryptor.isKeyConfigured());
	}

	@Test
	void init_invalidKeyLength_throwsException() {
		AuthConfigEncryptor badEncryptor = new AuthConfigEncryptor();
		// 16 bytes instead of 32
		byte[] shortKey = new byte[16];
		String base64Key = Base64.getEncoder().encodeToString(shortKey);
		ReflectionTestUtils.setField(badEncryptor, "secretKeyBase64", base64Key);

		assertThrows(IllegalArgumentException.class, badEncryptor::init);
	}

}
