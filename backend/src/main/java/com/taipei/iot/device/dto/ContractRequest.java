package com.taipei.iot.device.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "標案契約請求")
public class ContractRequest {

	@NotBlank(message = "契約編號為必填")
	@Schema(description = "契約編號", example = "CON-2024-001")
	private String contractCode;

	@NotBlank(message = "契約名稱為必填")
	@Schema(description = "契約名稱", example = "路燈維護案")
	private String contractName;

	@Schema(description = "預算年度", example = "2024")
	private Integer budgetYear;

	@Schema(description = "標案案號")
	private String procurementNumber;

	@Schema(description = "承包商名稱")
	private String contractorName;

	@Schema(description = "承包商聯絡方式")
	private String contractorContact;

	@Schema(description = "設備分類", example = "STREET_LIGHT")
	private String assetCategory;

	@Schema(description = "數量")
	private Integer quantity;

	@Schema(description = "契約開始日期")
	private LocalDate startDate;

	@Schema(description = "契約結束日期")
	private LocalDate endDate;

	@Schema(description = "驗收日期")
	private LocalDate acceptanceDate;

	@Schema(description = "保固年限")
	private Integer warrantyYears;

}
