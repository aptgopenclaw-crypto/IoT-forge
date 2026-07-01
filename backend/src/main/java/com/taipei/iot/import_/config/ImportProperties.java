package com.taipei.iot.import_.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "import")
public class ImportProperties {

	/** 單次匯入最大筆數 */
	private int maxRows = 500;

	/** 上傳檔案大小上限 (單位: bytes)，預設 10MB */
	private long maxFileSize = 10 * 1024 * 1024;

	/** 允許的副檔名，逗號分隔 */
	private String allowedExtensions = "xlsx,csv";

	public int getMaxRows() {
		return maxRows;
	}

	public void setMaxRows(int maxRows) {
		this.maxRows = maxRows;
	}

	public long getMaxFileSize() {
		return maxFileSize;
	}

	public void setMaxFileSize(long maxFileSize) {
		this.maxFileSize = maxFileSize;
	}

	public String getAllowedExtensions() {
		return allowedExtensions;
	}

	public void setAllowedExtensions(String allowedExtensions) {
		this.allowedExtensions = allowedExtensions;
	}

}
