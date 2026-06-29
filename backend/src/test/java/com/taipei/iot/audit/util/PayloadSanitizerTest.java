package com.taipei.iot.audit.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PayloadSanitizerTest {

	@Test
	void sanitize_shouldMaskPassword() {
		Object[] args = new Object[] { new TestPayload("user@test.com", "abc123") };
		String result = PayloadSanitizer.sanitize(args);
		assertNotNull(result);
		assertFalse(result.contains("abc123"));
		assertTrue(result.contains("***"));
		assertTrue(result.contains("user@test.com"));
	}

	@Test
	void sanitize_shouldMaskToken() {
		Object[] args = new Object[] { new TokenPayload("eyJhbGciOi...", "logout") };
		String result = PayloadSanitizer.sanitize(args);
		assertNotNull(result);
		assertFalse(result.contains("eyJhbGciOi"));
		assertTrue(result.contains("***"));
		assertTrue(result.contains("logout"));
	}

	@Test
	void sanitize_shouldMaskNestedSensitiveFields() {
		Object[] args = new Object[] { new NestedPayload("test", new TestPayload("user@test.com", "secret123")) };
		String result = PayloadSanitizer.sanitize(args);
		assertNotNull(result);
		assertFalse(result.contains("secret123"));
		assertTrue(result.contains("***"));
	}

	@Test
	void sanitize_shouldReturnNullForEmptyArgs() {
		assertNull(PayloadSanitizer.sanitize(null));
		assertNull(PayloadSanitizer.sanitize(new Object[] {}));
	}

	@Test
	void sanitize_shouldTruncateLongPayload() {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < 300; i++) {
			sb.append("abcdefghij");
		}
		Object[] args = new Object[] { sb.toString() };
		String result = PayloadSanitizer.sanitize(args);
		assertNotNull(result);
		assertTrue(result.length() <= 2000);
	}

	@Test
	void sanitize_shouldMaskRefreshToken() {
		Object[] args = new Object[] { new RefreshTokenPayload("abc-refresh-token-value") };
		String result = PayloadSanitizer.sanitize(args);
		assertNotNull(result);
		assertFalse(result.contains("abc-refresh-token-value"));
		assertTrue(result.contains("***"));
	}

	// ── A-10: contains-based matching covers field name variants ──

	@Test
	void sanitize_shouldMaskOldPassword() {
		Object[] args = new Object[] { new OldPasswordPayload("user@test.com", "oldPass123") };
		String result = PayloadSanitizer.sanitize(args);
		assertNotNull(result);
		assertFalse(result.contains("oldPass123"));
		assertTrue(result.contains("***"));
		assertTrue(result.contains("user@test.com"));
	}

	@Test
	void sanitize_shouldMaskConfirmPassword() {
		Object[] args = new Object[] { new ConfirmPasswordPayload("secret456") };
		String result = PayloadSanitizer.sanitize(args);
		assertNotNull(result);
		assertFalse(result.contains("secret456"));
		assertTrue(result.contains("***"));
	}

	@Test
	void sanitize_shouldMaskClientSecret() {
		Object[] args = new Object[] { new ClientSecretPayload("oidc-client-001", "super-secret-value") };
		String result = PayloadSanitizer.sanitize(args);
		assertNotNull(result);
		assertFalse(result.contains("super-secret-value"));
		assertTrue(result.contains("***"));
		assertTrue(result.contains("oidc-client-001"));
	}

	@Test
	void sanitize_shouldMaskAuthorizationHeader() {
		Object[] args = new Object[] { new AuthHeaderPayload("Bearer eyJ...", "/api/data") };
		String result = PayloadSanitizer.sanitize(args);
		assertNotNull(result);
		assertFalse(result.contains("Bearer eyJ"));
		assertTrue(result.contains("***"));
		assertTrue(result.contains("/api/data"));
	}

	@Test
	void sanitize_shouldMaskBindPassword_ldap() {
		Object[] args = new Object[] { new LdapPayload("cn=admin", "ldap-bind-pass") };
		String result = PayloadSanitizer.sanitize(args);
		assertNotNull(result);
		assertFalse(result.contains("ldap-bind-pass"));
		assertTrue(result.contains("***"));
		assertTrue(result.contains("cn=admin"));
	}

	// Test helper classes
	static class TestPayload {

		public String email;

		public String password;

		TestPayload(String email, String password) {
			this.email = email;
			this.password = password;
		}

	}

	static class TokenPayload {

		public String accessToken;

		public String action;

		TokenPayload(String accessToken, String action) {
			this.accessToken = accessToken;
			this.action = action;
		}

	}

	static class NestedPayload {

		public String name;

		public TestPayload detail;

		NestedPayload(String name, TestPayload detail) {
			this.name = name;
			this.detail = detail;
		}

	}

	static class RefreshTokenPayload {

		public String refreshToken;

		RefreshTokenPayload(String refreshToken) {
			this.refreshToken = refreshToken;
		}

	}

	static class OldPasswordPayload {

		public String email;

		public String oldPassword;

		OldPasswordPayload(String email, String oldPassword) {
			this.email = email;
			this.oldPassword = oldPassword;
		}

	}

	static class ConfirmPasswordPayload {

		public String confirmPassword;

		ConfirmPasswordPayload(String confirmPassword) {
			this.confirmPassword = confirmPassword;
		}

	}

	static class ClientSecretPayload {

		public String clientId;

		public String clientSecret;

		ClientSecretPayload(String clientId, String clientSecret) {
			this.clientId = clientId;
			this.clientSecret = clientSecret;
		}

	}

	static class AuthHeaderPayload {

		public String authorization;

		public String url;

		AuthHeaderPayload(String authorization, String url) {
			this.authorization = authorization;
			this.url = url;
		}

	}

	static class LdapPayload {

		public String bindDn;

		public String bindPassword;

		LdapPayload(String bindDn, String bindPassword) {
			this.bindDn = bindDn;
			this.bindPassword = bindPassword;
		}

	}

}
