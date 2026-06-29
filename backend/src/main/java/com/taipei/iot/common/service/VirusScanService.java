package com.taipei.iot.common.service;

/**
 * 病毒掃描服務介面。
 * <p>
 * 實作包含 {@link NoOpVirusScanService}（開發環境）及 {@link ClamAvVirusScanService}（正式環境）。
 * </p>
 */
public interface VirusScanService {

	/**
	 * 掃描結果。
	 */
	enum ScanResult {

		/** 檔案安全 */
		CLEAN,
		/** 偵測到惡意內容 */
		INFECTED,
		/**
		 * 掃描服務不可用或發生非預期錯誤。
		 * <p>
		 * <b>呼叫端處理策略：</b>應視為<b>拒絕上傳</b>（fail-closed）， 不得將 ERROR 當作通過。若需
		 * fallback（如暫時允許上傳並於後台重掃）， 必須在呼叫端明確實作並記錄安全事件。
		 * </p>
		 */
		ERROR

	}

	/**
	 * 掃描指定路徑的檔案。
	 * @param filePath 檔案的絕對路徑
	 * @return 掃描結果
	 */
	ScanResult scan(String filePath);

}
