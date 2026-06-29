package com.taipei.iot.auth.provider.config.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taipei.iot.auth.provider.AuthType;
import com.taipei.iot.auth.provider.AuthenticationDispatcher;
import com.taipei.iot.auth.provider.config.dto.TenantAuthConfigRequest;
import com.taipei.iot.auth.provider.config.dto.TenantAuthConfigResponse;
import com.taipei.iot.auth.provider.config.entity.TenantAuthConfigEntity;
import com.taipei.iot.auth.provider.config.repository.TenantAuthConfigRepository;
import com.taipei.iot.auth.provider.config.service.TenantAuthConfigService;
import com.taipei.iot.auth.provider.crypto.AuthConfigEncryptor;
import com.taipei.iot.common.enums.ErrorCode;
import com.taipei.iot.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class TenantAuthConfigServiceImpl implements TenantAuthConfigService {

	private static final Set<String> SENSITIVE_KEYS = Set.of("password", "secret", "clientSecret", "bindPassword",
			"privateKey");

	private final TenantAuthConfigRepository repository;

	private final AuthConfigEncryptor encryptor;

	private final AuthenticationDispatcher dispatcher;

	private final ObjectMapper objectMapper;

	@Override
	public TenantAuthConfigResponse getByTenantId(String tenantId) {
		TenantAuthConfigEntity entity = repository.findByTenantId(tenantId).orElse(null);
		if (entity == null) {
			// No config = implicit LOCAL
			return TenantAuthConfigResponse.builder()
				.tenantId(tenantId)
				.authType(AuthType.LOCAL)
				.enabled(true)
				.fallbackLocal(true)
				.build();
		}
		return toResponse(entity);
	}

	@Override
	@Transactional
	public TenantAuthConfigResponse createOrUpdate(String tenantId, TenantAuthConfigRequest request) {
		TenantAuthConfigEntity entity = repository.findByTenantId(tenantId)
			.orElse(TenantAuthConfigEntity.builder().tenantId(tenantId).build());

		entity.setAuthType(request.getAuthType());
		if (request.getFallbackLocal() != null) {
			entity.setFallbackLocal(request.getFallbackLocal());
		}

		// Encrypt config if provided
		if (request.getConfig() != null && !request.getConfig().isEmpty()) {
			String configJson = serializeConfig(request.getConfig());
			entity.setConfigJson(encryptor.encrypt(configJson));
		}
		else if (request.getAuthType() == AuthType.LOCAL) {
			entity.setConfigJson(null);
		}

		entity.setEnabled(true);
		entity = repository.save(entity);
		log.info("Tenant auth config updated: tenantId={}, authType={}", tenantId, request.getAuthType());
		return toResponse(entity);
	}

	@Override
	@Transactional
	public void deleteByTenantId(String tenantId) {
		repository.deleteByTenantId(tenantId);
		log.info("Tenant auth config deleted (reverted to LOCAL): tenantId={}", tenantId);
	}

	@Override
	public boolean testConnection(String tenantId, TenantAuthConfigRequest request) {
		if (request.getAuthType() == AuthType.LOCAL) {
			return true;
		}
		String configJson = serializeConfig(request.getConfig());
		return dispatcher.testConnection(request.getAuthType(), configJson);
	}

	private TenantAuthConfigResponse toResponse(TenantAuthConfigEntity entity) {
		Map<String, Object> sanitizedConfig = null;
		if (entity.getConfigJson() != null && !entity.getConfigJson().isBlank()) {
			String decrypted = encryptor.decrypt(entity.getConfigJson());
			sanitizedConfig = sanitize(deserializeConfig(decrypted));
		}

		return TenantAuthConfigResponse.builder()
			.id(entity.getId())
			.tenantId(entity.getTenantId())
			.authType(entity.getAuthType())
			.enabled(entity.getEnabled())
			.config(sanitizedConfig)
			.fallbackLocal(entity.getFallbackLocal())
			.createdAt(entity.getCreatedAt())
			.updatedAt(entity.getUpdatedAt())
			.build();
	}

	private Map<String, Object> sanitize(Map<String, Object> config) {
		if (config == null)
			return null;
		Map<String, Object> result = new LinkedHashMap<>(config);
		for (String key : result.keySet()) {
			if (SENSITIVE_KEYS.stream().anyMatch(s -> key.toLowerCase().contains(s.toLowerCase()))) {
				result.put(key, "***");
			}
		}
		return result;
	}

	private String serializeConfig(Map<String, Object> config) {
		try {
			return objectMapper.writeValueAsString(config);
		}
		catch (Exception e) {
			throw new BusinessException(ErrorCode.UNKNOWN_ERROR);
		}
	}

	private Map<String, Object> deserializeConfig(String json) {
		try {
			return objectMapper.readValue(json, new TypeReference<>() {
			});
		}
		catch (Exception e) {
			throw new BusinessException(ErrorCode.UNKNOWN_ERROR);
		}
	}

}
