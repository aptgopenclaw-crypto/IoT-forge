package com.taipei.iot.import_.circuit;

import com.taipei.iot.common.context.TenantContext;
import com.taipei.iot.device.entity.Circuit;
import com.taipei.iot.device.repository.CircuitRepository;
import com.taipei.iot.import_.ImportError;
import com.taipei.iot.import_.ImportStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class CircuitImportStrategy implements ImportStrategy<CircuitImportRow> {

	private static final Set<String> HEADERS = Set.of("circuit_number", "circuit_name", "taipower_account",
			"usage_type", "panel_box_device_id");

	private static final int MAX_CIRCUIT_NUMBER_LENGTH = 50;

	private static final int MAX_CIRCUIT_NAME_LENGTH = 200;

	private static final int MAX_TAIPOWER_ACCOUNT_LENGTH = 50;

	private static final int MAX_USAGE_TYPE_LENGTH = 50;

	private final CircuitRepository circuitRepository;

	@Override
	public String getEntityType() {
		return "circuit";
	}

	@Override
	public Set<String> expectedHeaders() {
		return HEADERS;
	}

	@Override
	public CircuitImportRow mapToDto(Map<String, String> row) {
		CircuitImportRow.CircuitImportRowBuilder builder = CircuitImportRow.builder()
			.circuitNumber(row.get("circuit_number"))
			.circuitName(row.get("circuit_name"))
			.taipowerAccount(row.get("taipower_account"))
			.usageType(row.get("usage_type"));

		String rawId = row.get("panel_box_device_id");
		builder.rawPanelBoxDeviceId(rawId);
		if (rawId != null && !rawId.isBlank()) {
			try {
				builder.panelBoxDeviceId(Long.parseLong(rawId.trim()));
			}
			catch (NumberFormatException ignored) {
				// validate 階段回報
			}
		}

		return builder.build();
	}

	@Override
	public List<ImportError> validate(CircuitImportRow dto, int rowNum) {
		List<ImportError> errors = new ArrayList<>();

		// circuit_number 必填
		if (dto.getCircuitNumber() == null || dto.getCircuitNumber().isBlank()) {
			errors.add(error(rowNum, "circuit_number", dto.getCircuitNumber(), "circuit_number 為必填"));
		}
		else if (dto.getCircuitNumber().length() > MAX_CIRCUIT_NUMBER_LENGTH) {
			errors.add(error(rowNum, "circuit_number", dto.getCircuitNumber(),
					"circuit_number 長度不得超過 " + MAX_CIRCUIT_NUMBER_LENGTH));
		}

		// circuit_name 長度
		if (dto.getCircuitName() != null && dto.getCircuitName().length() > MAX_CIRCUIT_NAME_LENGTH) {
			errors.add(error(rowNum, "circuit_name", dto.getCircuitName(),
					"circuit_name 長度不得超過 " + MAX_CIRCUIT_NAME_LENGTH));
		}

		// taipower_account 長度
		if (dto.getTaipowerAccount() != null && dto.getTaipowerAccount().length() > MAX_TAIPOWER_ACCOUNT_LENGTH) {
			errors.add(error(rowNum, "taipower_account", dto.getTaipowerAccount(),
					"taipower_account 長度不得超過 " + MAX_TAIPOWER_ACCOUNT_LENGTH));
		}

		// usage_type 長度
		if (dto.getUsageType() != null && dto.getUsageType().length() > MAX_USAGE_TYPE_LENGTH) {
			errors.add(error(rowNum, "usage_type", dto.getUsageType(), "usage_type 長度不得超過 " + MAX_USAGE_TYPE_LENGTH));
		}

		// panel_box_device_id 格式
		if (dto.getRawPanelBoxDeviceId() != null && !dto.getRawPanelBoxDeviceId().isBlank()
				&& dto.getPanelBoxDeviceId() == null) {
			errors.add(error(rowNum, "panel_box_device_id", dto.getRawPanelBoxDeviceId(),
					"panel_box_device_id 需為整數，收到「" + dto.getRawPanelBoxDeviceId() + "」"));
		}

		return errors;
	}

	@Override
	public List<ImportError> batchValidate(List<CircuitImportRow> dtos) {
		String tenantId = TenantContext.getCurrentTenantId();
		List<ImportError> errors = new ArrayList<>();

		// ── circuit_number 檔案內重複 ──
		Map<String, List<Integer>> numberRowMap = new LinkedHashMap<>();
		for (int i = 0; i < dtos.size(); i++) {
			String number = dtos.get(i).getCircuitNumber();
			if (number != null && !number.isBlank()) {
				numberRowMap.computeIfAbsent(number, k -> new ArrayList<>()).add(i + 2);
			}
		}
		for (Map.Entry<String, List<Integer>> entry : numberRowMap.entrySet()) {
			if (entry.getValue().size() > 1) {
				String msg = "迴路編號 " + entry.getKey() + " 在檔案中重複（第 " + entry.getValue() + " 列）";
				for (int rowNum : entry.getValue()) {
					errors.add(error(rowNum, "circuit_number", entry.getKey(), msg));
				}
			}
		}

		// ── circuit_number 租戶內唯一 ──
		Set<String> existingNumbers = numberRowMap.keySet()
			.stream()
			.filter(n -> circuitRepository.findByTenantIdAndCircuitNumber(tenantId, n).isPresent())
			.collect(Collectors.toSet());
		for (Map.Entry<String, List<Integer>> entry : numberRowMap.entrySet()) {
			if (existingNumbers.contains(entry.getKey())) {
				for (int rowNum : entry.getValue()) {
					errors.add(error(rowNum, "circuit_number", entry.getKey(), "迴路編號 " + entry.getKey() + " 已存在於此租戶"));
				}
			}
		}

		return errors;
	}

	@Override
	@Transactional
	public void saveAll(List<CircuitImportRow> rows) {
		String tenantId = TenantContext.getCurrentTenantId();

		List<Circuit> circuits = rows.stream()
			.map(row -> Circuit.builder()
				.tenantId(tenantId)
				.circuitNumber(row.getCircuitNumber())
				.circuitName(row.getCircuitName())
				.taipowerAccount(row.getTaipowerAccount())
				.usageType(row.getUsageType())
				.panelBoxDeviceId(row.getPanelBoxDeviceId())
				.status("ACTIVE")
				.build())
			.toList();

		circuitRepository.saveAll(circuits);
		log.info("[CircuitImport] tenant={} saved {} circuits", tenantId, circuits.size());
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
