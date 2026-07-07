package com.taipei.iot.import_.contract;

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
public class ContractImportRow {

	private String contractCode;

	private String contractName;

	private Integer budgetYear;

	private String procurementNumber;

	private String contractorName;

	private String contractorContact;

	private String assetCategory;

	private Integer quantity;

	private LocalDate startDate;

	private LocalDate endDate;

	private LocalDate acceptanceDate;

	private Integer warrantyYears;

	// raw 原始值 — validate 階段回報格式錯誤
	private String rawBudgetYear;

	private String rawQuantity;

	private String rawWarrantyYears;

	private String rawStartDate;

	private String rawEndDate;

	private String rawAcceptanceDate;

}
