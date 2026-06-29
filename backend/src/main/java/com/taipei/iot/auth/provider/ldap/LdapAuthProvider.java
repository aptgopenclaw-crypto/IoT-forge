package com.taipei.iot.auth.provider.ldap;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taipei.iot.auth.entity.UserEntity;
import com.taipei.iot.auth.provider.AuthType;
import com.taipei.iot.auth.provider.AuthenticationProvider;
import com.taipei.iot.auth.provider.AuthenticationRequest;
import com.taipei.iot.auth.provider.AuthenticationResult;
import com.taipei.iot.auth.repository.UserRepository;
import com.taipei.iot.common.enums.ErrorCode;
import com.taipei.iot.common.enums.SecurityEvent;
import com.taipei.iot.common.exception.BusinessException;
import com.taipei.iot.common.util.SecurityLogger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ldap.AuthenticationException;
import org.springframework.ldap.CommunicationException;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.core.support.LdapContextSource;
import org.springframework.stereotype.Component;

/**
 * LDAP/AD authentication provider using direct LDAP bind.
 *
 * <p>
 * 流程：
 * <ol>
 * <li>從 {@code configJson} 解析 {@link LdapConfig}</li>
 * <li>查本地 user table（by email）— 帳號必須由管理員預先建立，auth_type = LDAP</li>
 * <li>用使用者的 email + 密碼對 AD 做 LDAP bind（直接 bind，不需 service account）</li>
 * <li>bind 成功 → 回傳本地 userId；密碼錯誤 → 401；AD 無法連線 → 503</li>
 * </ol>
 *
 * <p>
 * 本 provider <b>不</b>做 JIT provisioning：本地帳號不存在時直接拋 USER_NOT_FOUND， 避免任意 AD
 * 帳號繞過管理員審核直接登入平台。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LdapAuthProvider implements AuthenticationProvider {

	private final UserRepository userRepository;

	private final ObjectMapper objectMapper;

	@Override
	public AuthType getType() {
		return AuthType.LDAP;
	}

	@Override
	public AuthenticationResult authenticate(AuthenticationRequest request, String configJson) {
		String email = request.getIdentifier();
		String password = request.getCredential();

		// 1. Parse LDAP config
		LdapConfig ldapConfig = parseConfig(configJson);

		// 2. Look up local user — must exist and be marked auth_type = LDAP
		UserEntity user = userRepository.findByEmail(email).orElse(null);
		if (user == null) {
			SecurityLogger.warn(SecurityEvent.LOGIN_FAILED, null, "email=" + email, "reason=user_not_found");
			throw new BusinessException(ErrorCode.USER_NOT_FOUND);
		}
		if (!user.getEnabled()) {
			SecurityLogger.warn(SecurityEvent.LOGIN_FAILED, null, "email=" + email, "reason=account_disabled");
			throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
		}
		if (!AuthType.LDAP.equals(user.getAuthType())) {
			// 本地帳號不應走 LDAP provider（dispatcher 透過 tenant config 路由，理論上不會到這裡）
			SecurityLogger.warn(SecurityEvent.LOGIN_FAILED, null, "email=" + email, "reason=auth_type_mismatch",
					"userAuthType=" + user.getAuthType());
			throw new BusinessException(ErrorCode.LDAP_AUTH_FAILED);
		}

		// 3. LDAP bind
		String userDn = ldapConfig.buildUserDn(email);
		log.debug("Attempting LDAP bind for email={} dn={}", email, userDn);
		bindToLdap(ldapConfig, userDn, password, email);

		log.info("LDAP bind successful for email={}", email);
		return AuthenticationResult.builder()
			.localUserId(user.getUserId())
			.externalId(userDn)
			.email(email)
			.displayName(user.getDisplayName())
			.build();
	}

	@Override
	public boolean testConnection(String configJson) {
		LdapConfig ldapConfig = parseConfig(configJson);
		LdapContextSource contextSource = buildContextSource(ldapConfig, null, null);
		contextSource.afterPropertiesSet();
		LdapTemplate ldapTemplate = new LdapTemplate(contextSource);
		try {
			ldapTemplate.lookup("");
			return true;
		}
		catch (Exception e) {
			log.warn("LDAP test connection failed for url={}: {}", ldapConfig.getUrl(), e.getMessage());
			return false;
		}
	}

	// ── private helpers ───────────────────────────────────────────────────────

	private void bindToLdap(LdapConfig ldapConfig, String userDn, String password, String email) {
		LdapContextSource contextSource = buildContextSource(ldapConfig, userDn, password);
		try {
			contextSource.afterPropertiesSet();
			// getContext() performs the actual bind; if credentials are wrong it throws
			// AuthenticationException; if server is unreachable it throws
			// CommunicationException
			contextSource.getContext(userDn, password);
		}
		catch (AuthenticationException e) {
			SecurityLogger.warn(SecurityEvent.LOGIN_FAILED, null, "email=" + email, "reason=ldap_bind_failed");
			log.debug("LDAP bind failed for dn={}: {}", userDn, e.getMessage());
			throw new BusinessException(ErrorCode.LDAP_AUTH_FAILED);
		}
		catch (CommunicationException e) {
			SecurityLogger.warn(SecurityEvent.LOGIN_FAILED, null, "email=" + email, "reason=ldap_unreachable",
					"url=" + ldapConfig.getUrl());
			log.error("LDAP server unreachable at url={}: {}", ldapConfig.getUrl(), e.getMessage());
			throw new BusinessException(ErrorCode.LDAP_SERVICE_UNAVAILABLE);
		}
		catch (Exception e) {
			// Covers NamingException (invalid DN, bad base DN, etc.)
			SecurityLogger.warn(SecurityEvent.LOGIN_FAILED, null, "email=" + email, "reason=ldap_error",
					"errorType=" + e.getClass().getSimpleName());
			log.error("Unexpected LDAP error for dn={}: {}", userDn, e.getMessage());
			throw new BusinessException(ErrorCode.LDAP_SERVICE_UNAVAILABLE);
		}
	}

	private LdapContextSource buildContextSource(LdapConfig ldapConfig, String userDn, String password) {
		LdapContextSource contextSource = new LdapContextSource();
		contextSource.setUrl(ldapConfig.getUrl());
		contextSource.setBase(ldapConfig.getBaseDn() != null ? ldapConfig.getBaseDn() : "");
		if (userDn != null) {
			contextSource.setUserDn(userDn);
			contextSource.setPassword(password != null ? password : "");
		}
		// Connect and read timeouts (passed as environment properties to JNDI)
		java.util.Hashtable<String, Object> env = new java.util.Hashtable<>();
		env.put("com.sun.jndi.ldap.connect.timeout", String.valueOf(ldapConfig.getConnectTimeoutMs()));
		env.put("com.sun.jndi.ldap.read.timeout", String.valueOf(ldapConfig.getReadTimeoutMs()));
		contextSource.setBaseEnvironmentProperties(env);
		return contextSource;
	}

	private LdapConfig parseConfig(String configJson) {
		if (configJson == null || configJson.isBlank()) {
			throw new BusinessException(ErrorCode.LDAP_CONFIG_INVALID);
		}
		try {
			LdapConfig config = objectMapper.readValue(configJson, LdapConfig.class);
			if (config.getUrl() == null || config.getUrl().isBlank()) {
				throw new BusinessException(ErrorCode.LDAP_CONFIG_INVALID);
			}
			if (config.getBaseDn() == null || config.getBaseDn().isBlank()) {
				throw new BusinessException(ErrorCode.LDAP_CONFIG_INVALID);
			}
			return config;
		}
		catch (BusinessException e) {
			throw e;
		}
		catch (Exception e) {
			log.error("Failed to parse LDAP config: {}", e.getMessage());
			throw new BusinessException(ErrorCode.LDAP_CONFIG_INVALID);
		}
	}

}
