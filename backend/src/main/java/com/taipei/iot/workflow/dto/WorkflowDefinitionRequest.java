package com.taipei.iot.workflow.dto;

import jakarta.validation.constraints.NotBlank;

public record WorkflowDefinitionRequest(@NotBlank String code, @NotBlank String name, @NotBlank String stepsJson,
		boolean enabled) {
}
