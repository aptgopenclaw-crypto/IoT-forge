package com.taipei.iot.device.service;

import com.taipei.iot.common.enums.ErrorCode;
import com.taipei.iot.common.exception.BusinessException;
import com.taipei.iot.device.dto.ContractRequest;
import com.taipei.iot.device.dto.ContractResponse;
import com.taipei.iot.device.entity.Contract;
import com.taipei.iot.device.enums.ContractStatus;
import com.taipei.iot.device.repository.ContractRepository;
import com.taipei.iot.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ContractService {

	private final ContractRepository contractRepository;

	public Page<ContractResponse> list(ContractStatus status, String keyword, Pageable pageable) {
		return contractRepository.findByFilters(status, keyword, pageable).map(this::toResponse);
	}

	public ContractResponse getById(Long id) {
		Contract contract = contractRepository.findById(id)
			.orElseThrow(() -> new BusinessException(ErrorCode.CONTRACT_NOT_FOUND));
		return toResponse(contract);
	}

	@Transactional
	public ContractResponse create(ContractRequest request) {
		LocalDate warrantyExpiry = request.getAcceptanceDate() != null && request.getWarrantyYears() != null
				? request.getAcceptanceDate().plusYears(request.getWarrantyYears()) : null;

		Contract contract = Contract.builder()
			.tenantId(TenantContext.getCurrentTenantId())
			.contractCode(request.getContractCode())
			.contractName(request.getContractName())
			.budgetYear(request.getBudgetYear())
			.procurementNumber(request.getProcurementNumber())
			.contractorName(request.getContractorName())
			.contractorContact(request.getContractorContact())
			.assetCategory(request.getAssetCategory())
			.quantity(request.getQuantity())
			.startDate(request.getStartDate())
			.endDate(request.getEndDate())
			.acceptanceDate(request.getAcceptanceDate())
			.warrantyYears(request.getWarrantyYears())
			.warrantyExpiry(warrantyExpiry)
			.status(ContractStatus.ACTIVE)
			.build();
		return toResponse(contractRepository.save(contract));
	}

	@Transactional
	public ContractResponse update(Long id, ContractRequest request) {
		Contract contract = contractRepository.findById(id)
			.orElseThrow(() -> new BusinessException(ErrorCode.CONTRACT_NOT_FOUND));

		contract.setContractCode(request.getContractCode());
		contract.setContractName(request.getContractName());
		contract.setBudgetYear(request.getBudgetYear());
		contract.setProcurementNumber(request.getProcurementNumber());
		contract.setContractorName(request.getContractorName());
		contract.setContractorContact(request.getContractorContact());
		contract.setAssetCategory(request.getAssetCategory());
		contract.setQuantity(request.getQuantity());
		contract.setStartDate(request.getStartDate());
		contract.setEndDate(request.getEndDate());
		contract.setAcceptanceDate(request.getAcceptanceDate());
		contract.setWarrantyYears(request.getWarrantyYears());

		LocalDate warrantyExpiry = request.getAcceptanceDate() != null && request.getWarrantyYears() != null
				? request.getAcceptanceDate().plusYears(request.getWarrantyYears()) : null;
		contract.setWarrantyExpiry(warrantyExpiry);

		return toResponse(contractRepository.save(contract));
	}

	private ContractResponse toResponse(Contract contract) {
		return ContractResponse.builder()
			.id(contract.getId())
			.contractCode(contract.getContractCode())
			.contractName(contract.getContractName())
			.budgetYear(contract.getBudgetYear())
			.procurementNumber(contract.getProcurementNumber())
			.contractorName(contract.getContractorName())
			.contractorContact(contract.getContractorContact())
			.quantity(contract.getQuantity())
			.startDate(contract.getStartDate())
			.endDate(contract.getEndDate())
			.acceptanceDate(contract.getAcceptanceDate())
			.warrantyExpiry(contract.getWarrantyExpiry())
			.status(contract.getStatus())
			.createdBy(contract.getCreatedBy())
			.createdAt(contract.getCreatedAt())
			.updatedAt(contract.getUpdatedAt())
			.build();
	}

}
