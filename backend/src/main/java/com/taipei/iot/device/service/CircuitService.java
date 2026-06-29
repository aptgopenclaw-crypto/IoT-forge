package com.taipei.iot.device.service;

import com.taipei.iot.common.enums.ErrorCode;
import com.taipei.iot.common.exception.BusinessException;
import com.taipei.iot.device.dto.CircuitRequest;
import com.taipei.iot.device.dto.CircuitResponse;
import com.taipei.iot.device.entity.Circuit;
import com.taipei.iot.device.repository.CircuitRepository;
import com.taipei.iot.device.repository.DeviceRepository;
import com.taipei.iot.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CircuitService {

	private final CircuitRepository circuitRepository;

	private final DeviceRepository deviceRepository;

	public Page<CircuitResponse> list(String keyword, Pageable pageable) {
		return circuitRepository.findByFilters(keyword, pageable).map(this::toResponse);
	}

	public CircuitResponse getById(Long id) {
		Circuit circuit = circuitRepository.findById(id)
			.orElseThrow(() -> new BusinessException(ErrorCode.CIRCUIT_NOT_FOUND));
		return toResponse(circuit);
	}

	@Transactional
	public CircuitResponse create(CircuitRequest request) {
		Circuit circuit = Circuit.builder()
			.tenantId(TenantContext.getCurrentTenantId())
			.panelBoxDeviceId(request.getPanelBoxDeviceId())
			.circuitNumber(request.getCircuitNumber())
			.circuitName(request.getCircuitName())
			.taipowerAccount(request.getTaipowerAccount())
			.usageType(request.getUsageType())
			.status("ACTIVE")
			.build();
		return toResponse(circuitRepository.save(circuit));
	}

	@Transactional
	public CircuitResponse update(Long id, CircuitRequest request) {
		Circuit circuit = circuitRepository.findById(id)
			.orElseThrow(() -> new BusinessException(ErrorCode.CIRCUIT_NOT_FOUND));
		circuit.setCircuitNumber(request.getCircuitNumber());
		circuit.setCircuitName(request.getCircuitName());
		circuit.setTaipowerAccount(request.getTaipowerAccount());
		circuit.setUsageType(request.getUsageType());
		return toResponse(circuitRepository.save(circuit));
	}

	@Transactional
	public void delete(Long id) {
		if (!circuitRepository.existsById(id)) {
			throw new BusinessException(ErrorCode.CIRCUIT_NOT_FOUND);
		}
		if (deviceRepository.countByCircuitId(id) > 0) {
			throw new BusinessException(ErrorCode.CIRCUIT_HAS_DEVICES);
		}
		circuitRepository.deleteById(id);
	}

	private CircuitResponse toResponse(Circuit circuit) {
		return CircuitResponse.builder()
			.id(circuit.getId())
			.panelBoxDeviceId(circuit.getPanelBoxDeviceId())
			.circuitNumber(circuit.getCircuitNumber())
			.circuitName(circuit.getCircuitName())
			.taipowerAccount(circuit.getTaipowerAccount())
			.usageType(circuit.getUsageType())
			.status(circuit.getStatus())
			.createdAt(circuit.getCreatedAt())
			.build();
	}

}
