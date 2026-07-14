package com.taipei.iot.vms.service;

import com.taipei.iot.auth.provider.crypto.AuthConfigEncryptor;
import com.taipei.iot.common.enums.ErrorCode;
import com.taipei.iot.common.exception.BusinessException;
import com.taipei.iot.vms.dto.VmsServerDTO;
import com.taipei.iot.vms.dto.VmsServerRequest;
import com.taipei.iot.vms.entity.VmsServerEntity;
import com.taipei.iot.vms.repository.VmsServerRepository;
import com.taipei.iot.vms.token.NxTokenManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class VmsServerService {

	private final VmsServerRepository repository;

	private final AuthConfigEncryptor encryptor;

	private final NxTokenManager nxTokenManager;

	public List<VmsServerDTO> findAll() {
		return repository.findAll().stream().map(this::toDTO).toList();
	}

	public VmsServerDTO findById(Long id) {
		return toDTO(repository.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.VMS_SERVER_NOT_FOUND)));
	}

	public VmsServerDTO create(VmsServerRequest request) {
		String encryptedPassword = request.getAuthPassword() != null ? encryptor.encrypt(request.getAuthPassword())
				: null;

		VmsServerEntity entity = VmsServerEntity.builder()
			.tenantId("0")
			.name(request.getName())
			.vmsType(request.getVmsType())
			.baseUrl(request.getBaseUrl())
			.authType(request.getAuthType())
			.authUsername(request.getAuthUsername())
			.authPassword(encryptedPassword)
			.apiToken(request.getApiToken())
			.isActive(request.getIsActive() != null ? request.getIsActive() : true)
			.build();
		entity = repository.save(entity);

		// Trigger initial token acquisition
		if (Boolean.TRUE.equals(entity.getIsActive())) {
			try {
				nxTokenManager.refreshToken(entity.getId());
			}
			catch (Exception e) {
				// Server saved but token acquisition failed — user can test connection
				// later
			}
		}

		return toDTO(entity);
	}

	public VmsServerDTO update(Long id, VmsServerRequest request) {
		VmsServerEntity entity = repository.findById(id)
			.orElseThrow(() -> new BusinessException(ErrorCode.VMS_SERVER_NOT_FOUND));

		entity.setName(request.getName());
		entity.setVmsType(request.getVmsType());
		entity.setBaseUrl(request.getBaseUrl());
		entity.setAuthType(request.getAuthType());
		entity.setAuthUsername(request.getAuthUsername());
		if (request.getAuthPassword() != null) {
			entity.setAuthPassword(encryptor.encrypt(request.getAuthPassword()));
		}
		entity.setApiToken(request.getApiToken());
		if (request.getIsActive() != null) {
			entity.setIsActive(request.getIsActive());
			if (!request.getIsActive()) {
				nxTokenManager.invalidateToken(id);
			}
		}

		return toDTO(repository.save(entity));
	}

	public void delete(Long id) {
		nxTokenManager.invalidateToken(id);
		repository.deleteById(id);
	}

	public void testConnection(Long id) {
		nxTokenManager.refreshToken(id);
	}

	private VmsServerDTO toDTO(VmsServerEntity entity) {
		return VmsServerDTO.builder()
			.id(entity.getId())
			.name(entity.getName())
			.vmsType(entity.getVmsType())
			.baseUrl(entity.getBaseUrl())
			.authType(entity.getAuthType())
			.authUsername(entity.getAuthUsername())
			.apiToken(entity.getApiToken())
			.isActive(entity.getIsActive())
			.createdAt(entity.getCreatedAt())
			.build();
	}

}
