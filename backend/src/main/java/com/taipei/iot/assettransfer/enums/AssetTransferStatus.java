package com.taipei.iot.assettransfer.enums;

/**
 * 資產異動申請狀態。
 *
 * <pre>
 * DRAFT      → 草稿（已建立，尚未送出）
 * PROCESSING → 審核中（流程進行中）
 * COMPLETED  → 完成（全部步驟通過）
 * REJECTED   → 退回（退回至申請人，待補件）
 * CANCELLED  → 已取消
 * </pre>
 */
public enum AssetTransferStatus {

	DRAFT, PROCESSING, COMPLETED, REJECTED, CANCELLED

}
