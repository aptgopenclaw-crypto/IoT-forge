package com.taipei.iot.common.service;

import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

/**
 * 檔案儲存抽象介面。 目前實作為 LocalFileStorageService（本地磁碟）， 後續可替換為 S3/MinIO 實作。
 */
public interface FileStorageService {

	/**
	 * 儲存檔案，回傳相對路徑（可作為 file_url 存入 DB）。
	 * @param subDir 子目錄（如 "repair/123"）
	 * @param fileName 檔案名稱
	 * @param input 檔案內容
	 * @return 儲存後的相對路徑
	 */
	String store(String subDir, String fileName, InputStream input);

	/**
	 * 便利方法：直接傳入 MultipartFile。
	 */
	String store(String subDir, MultipartFile file);

	/**
	 * 讀取檔案。
	 */
	InputStream load(String path);

	/**
	 * 取得檔案的絕對路徑（供病毒掃描等外部工具使用）。
	 */
	String resolveAbsolutePath(String path);

	/**
	 * 刪除檔案。失敗時靜默吞錯（log WARN），呼叫端無法得知檔案原本是否存在。 若呼叫端需要區分「不存在」與「刪除失敗」，請改用
	 * {@link #deleteIfExists(String)}。
	 */
	void delete(String path);

	/**
	 * F-6：嘗試刪除檔案，回傳檔案是否真的存在並被刪除。
	 *
	 * <p>
	 * 語意對齊 {@link java.nio.file.Files#deleteIfExists(java.nio.file.Path)}：
	 * </p>
	 * <ul>
	 * <li>true：檔案存在且已成功刪除</li>
	 * <li>false：檔案本來就不存在；或刪除時遇 I/O 例外（此情況同時記 WARN log，呼叫端可視為 best-effort 失敗）</li>
	 * </ul>
	 *
	 * <p>
	 * 路徑非法（traversal 試圖跳出根目錄）一律拋 {@code BusinessException(VALIDATION_ERROR)}。
	 * </p>
	 * @param path 相對路徑（相對於 storage 根目錄）
	 * @return true 若實際刪除了一個既存檔案；false 若檔案不存在或刪除失敗
	 */
	boolean deleteIfExists(String path);

	/**
	 * F-6：把已儲存檔案搬到另一個子目錄並改名，回傳新的相對路徑（可存入 DB）。
	 *
	 * <p>
	 * 實作優先採用 {@link java.nio.file.StandardCopyOption#ATOMIC_MOVE}；當底層 FS 不支援時退回
	 * {@link java.nio.file.StandardCopyOption#REPLACE_EXISTING}。
	 * </p>
	 *
	 * <p>
	 * 新檔案名稱會經 sanitize（移除 {@code / \ NUL ..}）並補上 UUID 前綴避免覆蓋既有檔， 與
	 * {@link #store(String, String, java.io.InputStream)} 行為一致。
	 * </p>
	 *
	 * <p>
	 * 來源 / 目的路徑都會做 traversal 檢查；任一脫離根目錄即拋 {@code BusinessException(VALIDATION_ERROR)}。
	 * 來源檔案不存在則拋 {@code BusinessException(ATTACHMENT_NOT_FOUND)}。
	 * </p>
	 * @param fromPath 來源相對路徑（必須存在於 storage 根目錄之下）
	 * @param toSubDir 目的子目錄（相對於 storage 根目錄；不存在會自動建立並套用 0700 權限）
	 * @param newFileName 目的檔名（會經 sanitize 並補 UUID 前綴；可與來源檔名相同）
	 * @return 新的相對路徑（格式同 {@link #store(String, String, java.io.InputStream)} 的回傳值）
	 */
	String move(String fromPath, String toSubDir, String newFileName);

}
