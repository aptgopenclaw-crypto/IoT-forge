package com.taipei.iot.assettransfer.dto;

/**
 * 退回目標步驟選項。
 *
 * @param stepId 步驟 ID（對應 workflow_definitions.steps_json 中的 step.id）
 * @param stepName 步驟名稱（顯示用）
 */
public record RejectTargetDto(String stepId, String stepName) {
}
