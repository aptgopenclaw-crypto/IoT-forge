package com.taipei.iot.import_.contract;

import com.taipei.iot.common.context.TenantContext;
import com.taipei.iot.device.entity.Contract;
import com.taipei.iot.device.enums.ContractStatus;
import com.taipei.iot.device.repository.ContractRepository;
import com.taipei.iot.import_.ImportError;
import com.taipei.iot.import_.ImportStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class ContractImportStrategy implements ImportStrategy<ContractImportRow> {

	private static final Set<String> HEADERS = Set.of("contract_code", "contract_name", "budget_year",
			"procurement_number", "contractor_name", "contractor_contact", "asset_category", "quantity", "start_date",
			"end_date", "acceptance_date", "warranty_years");

	private static final int MAX_CONTRACT_CODE_LENGTH = 100;

	private static final int MAX_CONTRACT_NAME_LENGTH = 300;

	private static final int MAX_PROCUREMENT_NUMBER_LENGTH = 100;

	private static final int MAX_CONTRACTOR_NAME_LENGTH = 200;

	private static final int MAX_CONTRACTOR_CONTACT_LENGTH = 200;

	private static final int MAX_ASSET_CATEGORY_LENGTH = 50;

	private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

	private final ContractRepository contractRepository;

	@Override
	public String getEntityType() {
		return "contract";
	}

	@Override
	public Set<String> expectedHeaders() {
		return HEADERS;
	}

	@Override
	public ContractImportRow mapToDto(Map<String, String> row) {
		ContractImportRow.ContractImportRowBuilder builder = ContractImportRow.builder()
			.contractCode(row.get("contract_code"))
			.contractName(row.get("contract_name"))
			.procurementNumber(row.get("procurement_number"))
			.contractorName(row.get("contractor_name"))
			.contractorContact(row.get("contractor_contact"))
			.assetCategory(row.get("asset_category"))
			.rawBudgetYear(row.get("budget_year"))
			.rawQuantity(row.get("quantity"))
			.rawWarrantyYears(row.get("warranty_years"))
			.rawStartDate(row.get("start_date"))
			.rawEndDate(row.get("end_date"))
			.rawAcceptanceDate(row.get("acceptance_date"));

		parseInteger(row.get("budget_year")).ifPresent(builder::budgetYear);
		parseInteger(row.get("quantity")).ifPresent(builder::quantity);
		parseInteger(row.get("warranty_years")).ifPresent(builder::warrantyYears);
		parseDate(row.get("start_date")).ifPresent(builder::startDate);
		parseDate(row.get("end_date")).ifPresent(builder::endDate);
		parseDate(row.get("acceptance_date")).ifPresent(builder::acceptanceDate);

		return builder.build();
	}

	@Override
	public List<ImportError> validate(ContractImportRow dto, int rowNum) {
		List<ImportError> errors = new ArrayList<>();

		// contract_code 必填
		if (dto.getContractCode() == null || dto.getContractCode().isBlank()) {
			errors.add(error(rowNum, "contract_code", dto.getContractCode(), "contract_code 為必填"));
		}
		else if (dto.getContractCode().length() > MAX_CONTRACT_CODE_LENGTH) {
			errors.add(error(rowNum, "contract_code", dto.getContractCode(),
					"contract_code 長度不得超過 " + MAX_CONTRACT_CODE_LENGTH));
		}

		// contract_name 必填
		if (dto.getContractName() == null || dto.getContractName().isBlank()) {
			errors.add(error(rowNum, "contract_name", dto.getContractName(), "contract_name 為必填"));
		}
		else if (dto.getContractName().length() > MAX_CONTRACT_NAME_LENGTH) {
			errors.add(error(rowNum, "contract_name", dto.getContractName(),
					"contract_name 長度不得超過 " + MAX_CONTRACT_NAME_LENGTH));
		}

		// 選填欄位長度
		if (dto.getProcurementNumber() != null && dto.getProcurementNumber().length() > MAX_PROCUREMENT_NUMBER_LENGTH) {
			errors.add(error(rowNum, "procurement_number", dto.getProcurementNumber(),
					"procurement_number 長度不得超過 " + MAX_PROCUREMENT_NUMBER_LENGTH));
		}
		if (dto.getContractorName() != null && dto.getContractorName().length() > MAX_CONTRACTOR_NAME_LENGTH) {
			errors.add(error(rowNum, "contractor_name", dto.getContractorName(),
					"contractor_name 長度不得超過 " + MAX_CONTRACTOR_NAME_LENGTH));
		}
		if (dto.getContractorContact() != null && dto.getContractorContact().length() > MAX_CONTRACTOR_CONTACT_LENGTH) {
			errors.add(error(rowNum, "contractor_contact", dto.getContractorContact(),
					"contractor_contact 長度不得超過 " + MAX_CONTRACTOR_CONTACT_LENGTH));
		}
		if (dto.getAssetCategory() != null && dto.getAssetCategory().length() > MAX_ASSET_CATEGORY_LENGTH) {
			errors.add(error(rowNum, "asset_category", dto.getAssetCategory(),
					"asset_category 長度不得超過 " + MAX_ASSET_CATEGORY_LENGTH));
		}

		// 數值格式
		validateIntegerField(errors, rowNum, "budget_year", dto.getRawBudgetYear(), dto.getBudgetYear());
		validateIntegerField(errors, rowNum, "quantity", dto.getRawQuantity(), dto.getQuantity());
		validateIntegerField(errors, rowNum, "warranty_years", dto.getRawWarrantyYears(), dto.getWarrantyYears());

		// 日期格式
		validateDateField(errors, rowNum, "start_date", dto.getRawStartDate(), dto.getStartDate());
		validateDateField(errors, rowNum, "end_date", dto.getRawEndDate(), dto.getEndDate());
		validateDateField(errors, rowNum, "acceptance_date", dto.getRawAcceptanceDate(), dto.getAcceptanceDate());

		return errors;
	}

	@Override
	public List<ImportError> batchValidate(List<ContractImportRow> dtos) {
		String tenantId = TenantContext.getCurrentTenantId();
		List<ImportError> errors = new ArrayList<>();

		// ── contract_code 檔案內重複 ──
		Map<String, List<Integer>> codeRowMap = new LinkedHashMap<>();
		for (int i = 0; i < dtos.size(); i++) {
			String code = dtos.get(i).getContractCode();
			if (code != null && !code.isBlank()) {
				codeRowMap.computeIfAbsent(code, k -> new ArrayList<>()).add(i + 2);
			}
		}
		for (Map.Entry<String, List<Integer>> entry : codeRowMap.entrySet()) {
			if (entry.getValue().size() > 1) {
				String msg = "契約代碼 " + entry.getKey() + " 在檔案中重複（第 " + entry.getValue() + " 列）";
				for (int rowNum : entry.getValue()) {
					errors.add(error(rowNum, "contract_code", entry.getKey(), msg));
				}
			}
		}

		// ── contract_code 租戶內唯一 ──
		Set<String> existingCodes = codeRowMap.keySet()
			.stream()
			.filter(c -> contractRepository.findByTenantIdAndContractCode(tenantId, c).isPresent())
			.collect(Collectors.toSet());
		for (Map.Entry<String, List<Integer>> entry : codeRowMap.entrySet()) {
			if (existingCodes.contains(entry.getKey())) {
				for (int rowNum : entry.getValue()) {
					errors.add(error(rowNum, "contract_code", entry.getKey(), "契約代碼 " + entry.getKey() + " 已存在於此租戶"));
				}
			}
		}

		return errors;
	}

	@Override
	@Transactional
	public void saveAll(List<ContractImportRow> rows) {
		String tenantId = TenantContext.getCurrentTenantId();

		List<Contract> contracts = rows.stream()
			.map(row -> Contract.builder()
				.tenantId(tenantId)
				.contractCode(row.getContractCode())
				.contractName(row.getContractName())
				.budgetYear(row.getBudgetYear())
				.procurementNumber(row.getProcurementNumber())
				.contractorName(row.getContractorName())
				.contractorContact(row.getContractorContact())
				.assetCategory(row.getAssetCategory())
				.quantity(row.getQuantity())
				.startDate(row.getStartDate())
				.endDate(row.getEndDate())
				.acceptanceDate(row.getAcceptanceDate())
				.warrantyYears(row.getWarrantyYears())
				.status(ContractStatus.ACTIVE)
				.build())
			.toList();

		contractRepository.saveAll(contracts);
		log.info("[ContractImport] tenant={} saved {} contracts", tenantId, contracts.size());
	}

	// ── helpers ──────────────────────────────────────────────────────────────

	private java.util.Optional<Integer> parseInteger(String raw) {
		if (raw == null || raw.isBlank()) {
			return java.util.Optional.empty();
		}
		try {
			return java.util.Optional.of(Integer.parseInt(raw.trim()));
		}
		catch (NumberFormatException e) {
			return java.util.Optional.empty();
		}
	}

	private java.util.Optional<LocalDate> parseDate(String raw) {
		if (raw == null || raw.isBlank()) {
			return java.util.Optional.empty();
		}
		try {
			return java.util.Optional.of(LocalDate.parse(raw.trim(), DATE_FORMATTER));
		}
		catch (DateTimeParseException e) {
			return java.util.Optional.empty();
		}
	}

	private void validateIntegerField(List<ImportError> errors, int rowNum, String field, String raw, Integer parsed) {
		if (raw != null && !raw.isBlank() && parsed == null) {
			errors.add(error(rowNum, field, raw, field + " 需為整數，收到「" + raw + "」"));
		}
	}

	private void validateDateField(List<ImportError> errors, int rowNum, String field, String raw, LocalDate parsed) {
		if (raw != null && !raw.isBlank() && parsed == null) {
			errors.add(error(rowNum, field, raw, field + " 格式應為 YYYY-MM-DD，收到「" + raw + "」"));
		}
	}

	private ImportError error(int row, String field, Object value, String message) {
		return ImportError.builder()
			.row(row)
			.field(field)
			.value(value != null ? value.toString() : "")
			.message(message)
			.build();
	}

}
