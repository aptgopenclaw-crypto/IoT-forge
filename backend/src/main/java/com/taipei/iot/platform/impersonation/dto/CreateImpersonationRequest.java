package com.taipei.iot.platform.impersonation.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Request body for {@code POST /v1/platform/impersonations}. See ADR-002 for the
 * contract.
 */
@Data
public class CreateImpersonationRequest {

	@NotBlank
	private String tenantId;

	@NotBlank
	@Size(max = 500)
	private String reason;

	/** Duration in minutes; must be 1..60 (per ADR-002). */
	@NotNull
	@Min(1)
	@Max(60)
	private Integer durationMinutes;

}
