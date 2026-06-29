package com.taipei.iot.workflow.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

@Schema(description = "設定代理人的請求。delegateFor（被代理人）由 SecurityContext 自動填入，僅能替自己設定代理。")
public record DelegateSetRequest(
		@Schema(description = "代理人 userId，代理期間該用戶將接手被代理人的審核待辦", example = "user-uuid-456") @NotBlank String delegateTo,

		@Schema(description = "限定生效的業務類型，null 表示適用所有業務類型", example = "ASSET_TRANSFER",
				nullable = true) String businessType,

		@Schema(description = "代理生效起始日（含）", example = "2026-07-01") @NotNull LocalDate effectiveFrom,

		@Schema(description = "代理生效結束日（含）", example = "2026-07-31") @NotNull LocalDate effectiveTo) {

}
