package com.taipei.iot.assettransfer.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record AssetTransferCreateRequest(@NotBlank @Size(max = 64) String assetCode,
		@NotBlank @Size(max = 256) String assetName,
		@NotBlank @Pattern(regexp = "INTERNAL|EXTERNAL|DISPOSAL|RETURN") String transferType,
		@NotNull Long departmentId, Long targetDepartmentId, @Size(max = 2000) String reason,
		@DecimalMin("0.00") BigDecimal assetValue) {

}
