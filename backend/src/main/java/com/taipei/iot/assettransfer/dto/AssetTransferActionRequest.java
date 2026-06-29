package com.taipei.iot.assettransfer.dto;

import jakarta.validation.constraints.Size;

public record AssetTransferActionRequest(@Size(max = 1000) String comment) {

}
