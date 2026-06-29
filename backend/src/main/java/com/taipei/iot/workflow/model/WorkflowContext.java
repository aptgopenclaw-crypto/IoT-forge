package com.taipei.iot.workflow.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 傳遞給 AssigneeResolver 的業務上下文。 簽核引擎不解讀這些欄位，完全交給組織適配層判斷。
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowContext {

	private String businessId;

	private String businessType;

	private String applicantId;

	private String departmentId;

	private String district;

	private String contractId;

}
