package com.taipei.iot.device.dto;

import com.taipei.iot.device.enums.ContractStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "標案契約回應")
public class ContractResponse {

	@Schema(description = "契約 ID")
	private Long id;

	@Schema(description = "契約編號")
	private String contractCode;

	@Schema(description = "契約名稱")
	private String contractName;

	@Schema(description = "預算年度")
	private Integer budgetYear;

	@Schema(description = "標案案號")
	private String procurementNumber;

	@Schema(description = "承包商名稱")
	private String contractorName;

	@Schema(description = "承包商聯絡方式")
	private String contractorContact;

	@Schema(description = "數量")
	private Integer quantity;

	@Schema(description = "契約開始日期")
	private LocalDate startDate;

	@Schema(description = "契約結束日期")
	private LocalDate endDate;

	@Schema(description = "驗收日期")
	private LocalDate acceptanceDate;

	@Schema(description = "保固到期日")
	private LocalDate warrantyExpiry;

	@Schema(description = "狀態")
	private ContractStatus status;

	@Schema(description = "建立者")
	private String createdBy;

	@Schema(description = "建立時間")
	private LocalDateTime createdAt;

	@Schema(description = "更新時間")
	private LocalDateTime updatedAt;

}
