package com.taipei.iot.import_.circuit;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CircuitImportRow {

	private String circuitNumber;

	private String circuitName;

	private String taipowerAccount;

	private String usageType;

	/** 原始值 — validate 階段用於回報格式錯誤 */
	private String rawPanelBoxDeviceId;

	/** 解析後值（可為 null） */
	private Long panelBoxDeviceId;

}
