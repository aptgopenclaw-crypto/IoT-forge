package com.taipei.iot.assettransfer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AssetTransferRejectRequest(@Size(max = 1000) String comment, @NotBlank String targetStepId) {

}
