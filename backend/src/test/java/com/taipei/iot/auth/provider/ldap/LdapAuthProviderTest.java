package com.taipei.iot.auth.provider.ldap;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taipei.iot.auth.entity.UserEntity;
import com.taipei.iot.auth.provider.AuthType;
import com.taipei.iot.auth.provider.AuthenticationRequest;
import com.taipei.iot.auth.provider.AuthenticationResult;
import com.taipei.iot.auth.repository.UserRepository;
import com.taipei.iot.common.enums.ErrorCode;
import com.taipei.iot.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ldap.AuthenticationException;
import org.springframework.ldap.CommunicationException;
import org.springframework.ldap.core.support.LdapContextSource;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LdapAuthProviderTest {

	@InjectMocks
	private LdapAuthProvider ldapAuthProvider;

	@Mock
	private UserRepository userRepository;

	private final ObjectMapper objectMapper = new ObjectMapper();

	private static final String VALID_CONFIG = """
			{
			  "url": "ldap://ad.example.tw:389",
			  "base_dn": "DC=example,DC=tw",
			  "user_dn_pattern": "mail={0},OU=Users,DC=example,DC=tw"
			}
			""";

	private UserEntity ldapUser;

	@BeforeEach
	void setUp() {
		// inject real ObjectMapper (not mocked)
		var field = org.springframework.test.util.ReflectionTestUtils.class;
		org.springframework.test.util.ReflectionTestUtils.setField(ldapAuthProvider, "objectMapper", objectMapper);

		ldapUser = UserEntity.builder()
			.userId("user-ldap-001")
			.email("alice@example.tw")
			.passwordHash("AD_AUTH")
			.displayName("Alice")
			.enabled(true)
			.locked(false)
			.loginFailCount(0)
			.authType(AuthType.LDAP)
			.isSuperAdmin(false)
			.build();
	}

	// ─── getType ─────────────────────────────────────────────────────────────

	@Test
	void getType_returnsLDAP() {
		assertEquals(AuthType.LDAP, ldapAuthProvider.getType());
	}

	// ─── config parsing ──────────────────────────────────────────────────────

	@Test
	void authenticate_nullConfig_throwsLdapConfigInvalid() {
		AuthenticationRequest request = AuthenticationRequest.builder()
			.identifier("alice@example.tw")
			.credential("password")
			.build();

		BusinessException ex = assertThrows(BusinessException.class,
				() -> ldapAuthProvider.authenticate(request, null));
		assertEquals(ErrorCode.LDAP_CONFIG_INVALID.getCode(), ex.getErrorCode().getCode());
	}

	@Test
	void authenticate_configMissingUrl_throwsLdapConfigInvalid() {
		String badConfig = """
				{"base_dn": "DC=example,DC=tw"}
				""";
		AuthenticationRequest request = AuthenticationRequest.builder()
			.identifier("alice@example.tw")
			.credential("password")
			.build();

		BusinessException ex = assertThrows(BusinessException.class,
				() -> ldapAuthProvider.authenticate(request, badConfig));
		assertEquals(ErrorCode.LDAP_CONFIG_INVALID.getCode(), ex.getErrorCode().getCode());
	}

	@Test
	void authenticate_configMissingBaseDn_throwsLdapConfigInvalid() {
		String badConfig = """
				{"url": "ldap://ad.example.tw:389"}
				""";
		AuthenticationRequest request = AuthenticationRequest.builder()
			.identifier("alice@example.tw")
			.credential("password")
			.build();

		BusinessException ex = assertThrows(BusinessException.class,
				() -> ldapAuthProvider.authenticate(request, badConfig));
		assertEquals(ErrorCode.LDAP_CONFIG_INVALID.getCode(), ex.getErrorCode().getCode());
	}

	// ─── user lookup ─────────────────────────────────────────────────────────

	@Test
	void authenticate_userNotFound_throwsUserNotFound() {
		when(userRepository.findByEmail("alice@example.tw")).thenReturn(Optional.empty());

		AuthenticationRequest request = AuthenticationRequest.builder()
			.identifier("alice@example.tw")
			.credential("password")
			.build();

		BusinessException ex = assertThrows(BusinessException.class,
				() -> ldapAuthProvider.authenticate(request, VALID_CONFIG));
		assertEquals(ErrorCode.USER_NOT_FOUND.getCode(), ex.getErrorCode().getCode());
	}

	@Test
	void authenticate_userDisabled_throwsAccountDisabled() {
		ldapUser.setEnabled(false);
		when(userRepository.findByEmail("alice@example.tw")).thenReturn(Optional.of(ldapUser));

		AuthenticationRequest request = AuthenticationRequest.builder()
			.identifier("alice@example.tw")
			.credential("password")
			.build();

		BusinessException ex = assertThrows(BusinessException.class,
				() -> ldapAuthProvider.authenticate(request, VALID_CONFIG));
		assertEquals(ErrorCode.ACCOUNT_DISABLED.getCode(), ex.getErrorCode().getCode());
	}

	@Test
	void authenticate_localUserCalledWithLdapProvider_throwsLdapAuthFailed() {
		ldapUser.setAuthType(AuthType.LOCAL);
		when(userRepository.findByEmail("alice@example.tw")).thenReturn(Optional.of(ldapUser));

		AuthenticationRequest request = AuthenticationRequest.builder()
			.identifier("alice@example.tw")
			.credential("password")
			.build();

		BusinessException ex = assertThrows(BusinessException.class,
				() -> ldapAuthProvider.authenticate(request, VALID_CONFIG));
		assertEquals(ErrorCode.LDAP_AUTH_FAILED.getCode(), ex.getErrorCode().getCode());
	}

	// ─── LDAP bind ───────────────────────────────────────────────────────────

	@Test
	void authenticate_bindAuthenticationException_throwsLdapAuthFailed() {
		when(userRepository.findByEmail("alice@example.tw")).thenReturn(Optional.of(ldapUser));

		try (MockedConstruction<LdapContextSource> mocked = mockConstruction(LdapContextSource.class,
				(mockCtx, context) -> {
					doNothing().when(mockCtx).afterPropertiesSet();
					doThrow(new AuthenticationException(
							new javax.naming.AuthenticationException("Invalid credentials")))
						.when(mockCtx)
						.getContext(anyString(), anyString());
				})) {

			AuthenticationRequest request = AuthenticationRequest.builder()
				.identifier("alice@example.tw")
				.credential("wrongPassword")
				.build();

			BusinessException ex = assertThrows(BusinessException.class,
					() -> ldapAuthProvider.authenticate(request, VALID_CONFIG));
			assertEquals(ErrorCode.LDAP_AUTH_FAILED.getCode(), ex.getErrorCode().getCode());
		}
	}

	@Test
	void authenticate_bindCommunicationException_throwsLdapServiceUnavailable() {
		when(userRepository.findByEmail("alice@example.tw")).thenReturn(Optional.of(ldapUser));

		try (MockedConstruction<LdapContextSource> mocked = mockConstruction(LdapContextSource.class,
				(mockCtx, context) -> {
					doNothing().when(mockCtx).afterPropertiesSet();
					doThrow(new CommunicationException(new javax.naming.CommunicationException("Connection refused")))
						.when(mockCtx)
						.getContext(anyString(), anyString());
				})) {

			AuthenticationRequest request = AuthenticationRequest.builder()
				.identifier("alice@example.tw")
				.credential("password")
				.build();

			BusinessException ex = assertThrows(BusinessException.class,
					() -> ldapAuthProvider.authenticate(request, VALID_CONFIG));
			assertEquals(ErrorCode.LDAP_SERVICE_UNAVAILABLE.getCode(), ex.getErrorCode().getCode());
		}
	}

	@Test
	void authenticate_unexpectedException_throwsLdapServiceUnavailable() {
		when(userRepository.findByEmail("alice@example.tw")).thenReturn(Optional.of(ldapUser));

		try (MockedConstruction<LdapContextSource> mocked = mockConstruction(LdapContextSource.class,
				(mockCtx, context) -> {
					doNothing().when(mockCtx).afterPropertiesSet();
					doThrow(new RuntimeException("Unexpected JNDI error")).when(mockCtx)
						.getContext(anyString(), anyString());
				})) {

			AuthenticationRequest request = AuthenticationRequest.builder()
				.identifier("alice@example.tw")
				.credential("password")
				.build();

			BusinessException ex = assertThrows(BusinessException.class,
					() -> ldapAuthProvider.authenticate(request, VALID_CONFIG));
			assertEquals(ErrorCode.LDAP_SERVICE_UNAVAILABLE.getCode(), ex.getErrorCode().getCode());
		}
	}

	@Test
	void authenticate_bindSuccess_returnsCorrectResult() {
		when(userRepository.findByEmail("alice@example.tw")).thenReturn(Optional.of(ldapUser));

		try (MockedConstruction<LdapContextSource> mocked = mockConstruction(LdapContextSource.class,
				(mockCtx, context) -> {
					doNothing().when(mockCtx).afterPropertiesSet();
					when(mockCtx.getContext(anyString(), anyString())).thenReturn(null);
				})) {

			AuthenticationRequest request = AuthenticationRequest.builder()
				.identifier("alice@example.tw")
				.credential("correctPassword")
				.build();

			AuthenticationResult result = ldapAuthProvider.authenticate(request, VALID_CONFIG);

			assertEquals("user-ldap-001", result.getLocalUserId());
			assertEquals("alice@example.tw", result.getEmail());
			assertEquals("Alice", result.getDisplayName());
			// externalId should be the constructed DN
			assertNotNull(result.getExternalId());
			assertTrue(result.getExternalId().contains("alice@example.tw"));
		}
	}

	// ─── LdapConfig.buildUserDn ──────────────────────────────────────────────

	@Test
	void ldapConfig_buildUserDn_withPattern() {
		LdapConfig config = new LdapConfig();
		config.setUrl("ldap://host:389");
		config.setBaseDn("DC=example,DC=tw");
		config.setUserDnPattern("mail={0},OU=Staff,DC=example,DC=tw");

		assertEquals("mail=bob@example.tw,OU=Staff,DC=example,DC=tw", config.buildUserDn("bob@example.tw"));
	}

	@Test
	void ldapConfig_buildUserDn_defaultPattern_usesBaseDn() {
		LdapConfig config = new LdapConfig();
		config.setUrl("ldap://host:389");
		config.setBaseDn("DC=example,DC=tw");
		// no userDnPattern set

		String dn = config.buildUserDn("bob@example.tw");
		assertTrue(dn.startsWith("mail=bob@example.tw,"), "DN should start with mail= identifier");
		assertTrue(dn.contains("DC=example,DC=tw"), "DN should contain baseDn");
	}

}
