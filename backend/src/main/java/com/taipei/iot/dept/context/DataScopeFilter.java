package com.taipei.iot.dept.context;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * 【預留框架】資料範圍過濾條件 Value Object — 描述過濾類型（精確 deptId / hierarchy 前綴 / 無限制）。
 *
 * <p>
 * <b>目前狀態（2026-05）：</b>僅被 {@link com.taipei.iot.dept.aspect.DataPermissionAspect} 建立並存入
 * {@link DataScopeContext}，但無消費端。保留為日後統一改造時啟用。
 * </p>
 */
@Getter
@Builder
@AllArgsConstructor
public class DataScopeFilter {

	public enum FilterType {

		NONE, EXACT, HIERARCHY_PREFIX

	}

	private final FilterType type;

	private final String fieldName;

	private final Object value;

	private final String hierarchyPathField;

	private final String hierarchyPathPrefix;

	public static DataScopeFilter none() {
		return DataScopeFilter.builder().type(FilterType.NONE).build();
	}

	public static DataScopeFilter exact(String fieldName, Long deptId) {
		return DataScopeFilter.builder().type(FilterType.EXACT).fieldName(fieldName).value(deptId).build();
	}

	public static DataScopeFilter hierarchyPrefix(String hierarchyPathField, String hierarchyPath) {
		return DataScopeFilter.builder()
			.type(FilterType.HIERARCHY_PREFIX)
			.hierarchyPathField(hierarchyPathField)
			.hierarchyPathPrefix(hierarchyPath)
			.build();
	}

}
