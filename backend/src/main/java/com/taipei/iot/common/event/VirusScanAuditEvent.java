package com.taipei.iot.common.event;

/**
 * 病毒掃描審計事件。
 *
 * <p>
 * 當 {@link com.taipei.iot.common.service.FileUploadTemplate} 偵測到掃毒結果為 {@code INFECTED} 或
 * {@code ERROR} 時發出，由 audit 模組的 listener 翻譯成 {@code user_event_log} 紀錄，供 SIEM / ELK 撈取告警。
 *
 * <p>
 * 使用 Spring {@code ApplicationEventPublisher} 解耦：common 模組不依賴 audit 模組， 由 audit 模組以
 * {@code @EventListener} 訂閱本事件並寫入 audit_log。
 *
 * <p>
 * [common v2 F-3]
 *
 * @param result 掃毒結果（{@code INFECTED} 或 {@code ERROR}）
 * @param relativePath 相對儲存路徑（含 subDir / 檔名）
 * @param originalFileName 使用者上傳時的原始檔名
 * @param size 檔案大小（bytes）
 * @param subDir 模組指定的子目錄（如 {@code announcement/123}）
 */
public record VirusScanAuditEvent(Result result, String relativePath, String originalFileName, long size,
		String subDir) {

	public enum Result {

		/** 偵測到惡意內容 */
		INFECTED,
		/** 掃描器不可用 / fail-closed */
		ERROR

	}
}
