package com.taipei.iot.workflow.service;

import com.taipei.iot.workflow.model.StepDefinition;
import com.taipei.iot.workflow.model.WorkflowContext;

/**
 * 審核人解析器（組織適配層的核心介面）。
 * <p>
 * 簽核引擎透過此介面獲取審核人，不直接依賴組織表。
 */
public interface IAssigneeResolver {

	/**
	 * 根據步驟定義與業務上下文，回傳實際審核人 userId。
	 * @param stepDef 步驟定義（含 roleCode）
	 * @param context 業務上下文
	 * @return 審核人 userId
	 */
	String resolve(StepDefinition stepDef, WorkflowContext context);

}
