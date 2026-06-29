package com.taipei.iot.auth.provider;

import com.taipei.iot.auth.entity.UserEntity;
import com.taipei.iot.auth.provider.config.entity.TenantAuthConfigEntity;
import com.taipei.iot.auth.provider.config.repository.TenantAuthConfigRepository;
import com.taipei.iot.auth.provider.crypto.AuthConfigEncryptor;
import com.taipei.iot.auth.repository.UserRepository;
import com.taipei.iot.common.enums.ErrorCode;
import com.taipei.iot.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Routes authentication requests to the appropriate {@link AuthenticationProvider} based
 * on the tenant's configured auth type.
 */
@Component
@Slf4j
public class AuthenticationDispatcher {

	private final Map<AuthType, AuthenticationProvider> providerMap;

	private final TenantAuthConfigRepository configRepository;

	private final AuthConfigEncryptor encryptor;

	private final UserRepository userRepository;

	public AuthenticationDispatcher(List<AuthenticationProvider> providers, TenantAuthConfigRepository configRepository,
			AuthConfigEncryptor encryptor, UserRepository userRepository) {
		this.providerMap = providers.stream()
			.collect(Collectors.toMap(AuthenticationProvider::getType, Function.identity()));
		this.configRepository = configRepository;
		this.encryptor = encryptor;
		this.userRepository = userRepository;
		log.info("AuthenticationDispatcher initialized with providers: {}", providerMap.keySet());
	}

	/**
	 * Dispatch an authentication request to the correct provider.
	 * @param request the authentication request
	 * @return authentication result from the matched provider
	 */
	public AuthenticationResult dispatch(AuthenticationRequest request) {
		// 1. Check per-user auth_type first (takes precedence over tenant config)
		AuthType authType = resolveUserAuthType(request.getIdentifier());

		TenantAuthConfigEntity config = null;
		if (authType == null) {
			// 2. Fall back to tenant auth config
			config = resolveConfig(request.getTenantId());
			authType = determineAuthType(config);
		}

		// 3. Find matching provider
		AuthenticationProvider provider = providerMap.get(authType);
		if (provider == null) {
			log.error("No provider registered for auth type: {}", authType);
			throw new BusinessException(ErrorCode.AUTH_PROVIDER_NOT_SUPPORTED);
		}

		// 4. Decrypt config (already resolved above if authType came from tenant config)
		String decryptedConfig = decryptConfig(config);

		// For per-user non-LOCAL types, tenant config holds the connection details.
		// tenantId is resolved by AuthServiceImpl before dispatch, so it is available
		// here.
		if (config == null && authType != AuthType.LOCAL) {
			config = resolveConfig(request.getTenantId());
			log.info("Resolved tenant auth config for tenantId={}: configJsonPresent={}, fallbackLocal={}",
					request.getTenantId(), config != null && config.getConfigJson() != null,
					config != null ? config.getFallbackLocal() : "N/A");
			decryptedConfig = decryptConfig(config);
			log.info("Decrypted config result: {}",
					decryptedConfig != null ? "success (" + decryptedConfig.length() + " chars)" : "null");
		}

		try {
			return provider.authenticate(request, decryptedConfig);
		}
		catch (BusinessException e) {
			// If external provider fails and fallback is enabled, try LOCAL
			if (authType != AuthType.LOCAL && config != null && Boolean.TRUE.equals(config.getFallbackLocal())) {
				log.warn("External auth ({}) failed for tenant {}, falling back to LOCAL", authType,
						request.getTenantId());
				AuthenticationProvider localProvider = providerMap.get(AuthType.LOCAL);
				if (localProvider != null) {
					return localProvider.authenticate(request, null);
				}
			}
			throw e;
		}
	}

	/**
	 * Test connection for a given auth type and config JSON.
	 */
	public boolean testConnection(AuthType authType, String configJson) {
		AuthenticationProvider provider = providerMap.get(authType);
		if (provider == null) {
			throw new BusinessException(ErrorCode.AUTH_PROVIDER_NOT_SUPPORTED);
		}
		return provider.testConnection(configJson);
	}

	private TenantAuthConfigEntity resolveConfig(String tenantId) {
		if (tenantId == null || tenantId.isBlank()) {
			return null;
		}
		return configRepository.findByTenantId(tenantId).orElse(null);
	}

	/**
	 * Returns the user's configured auth type if explicitly set, or null to fall back to
	 * tenant-level routing. This ensures LOCAL users are always routed to LOCAL even when
	 * the tenant switches to LDAP, and LDAP users are always routed to LDAP even when the
	 * tenant has no external config.
	 */
	private AuthType resolveUserAuthType(String identifier) {
		if (identifier == null || identifier.isBlank()) {
			return null;
		}
		Optional<UserEntity> user = userRepository.findByEmail(identifier);
		if (user.isEmpty()) {
			return null;
		}
		AuthType userAuthType = user.get().getAuthType();
		// If the user has an explicit auth_type (including LOCAL), honour it and skip
		// tenant-level routing. Only null means "inherit from tenant config".
		if (userAuthType != null) {
			log.debug("Per-user auth type: identifier={} authType={}", identifier, userAuthType);
			return userAuthType;
		}
		return null;
	}

	private AuthType determineAuthType(TenantAuthConfigEntity config) {
		if (config == null || !Boolean.TRUE.equals(config.getEnabled())) {
			return AuthType.LOCAL;
		}
		return config.getAuthType();
	}

	private String decryptConfig(TenantAuthConfigEntity config) {
		if (config == null || config.getConfigJson() == null || config.getConfigJson().isBlank()) {
			return null;
		}
		return encryptor.decrypt(config.getConfigJson());
	}

}
