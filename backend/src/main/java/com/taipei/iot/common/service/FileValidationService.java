package com.taipei.iot.common.service;

import com.taipei.iot.common.enums.ErrorCode;
import com.taipei.iot.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Set;

/**
 * 檔案安全驗證服務。
 * <ul>
 * <li>副檔名白名單</li>
 * <li>Magic bytes 驗證（Apache Tika）</li>
 * <li>按類別檔案大小限制（圖片 / 文件 / 影音）</li>
 * </ul>
 */
@Slf4j
@Service
public class FileValidationService {

	private final long maxImageSize;

	private final long maxDocumentSize;

	private final long maxMediaSize;

	private final Tika tika = new Tika();

	/** 允許的副檔名（小寫） */
	private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "gif", "bmp", "webp", "pdf",
			"xlsx", "docx", "csv", "mp4", "wav", "mp3");

	private static final Set<String> IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png", "gif", "bmp", "webp");

	private static final Set<String> MEDIA_EXTENSIONS = Set.of("mp4", "wav", "mp3");

	/** 副檔名 → 允許的 MIME type 前綴（用於 Magic bytes 交叉比對） */
	private static final Map<String, Set<String>> EXTENSION_MIME_MAP = Map.ofEntries(
			Map.entry("jpg", Set.of("image/jpeg")), Map.entry("jpeg", Set.of("image/jpeg")),
			Map.entry("png", Set.of("image/png")), Map.entry("gif", Set.of("image/gif")),
			Map.entry("bmp", Set.of("image/bmp", "image/x-ms-bmp")), Map.entry("webp", Set.of("image/webp")),
			Map.entry("pdf", Set.of("application/pdf")),
			Map.entry("xlsx",
					Set.of("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/zip")),
			Map.entry("docx",
					Set.of("application/vnd.openxmlformats-officedocument.wordprocessingml.document",
							"application/zip")),
			Map.entry("csv", Set.of("text/plain", "text/csv", "application/csv")),
			Map.entry("mp4", Set.of("video/mp4")),
			Map.entry("wav", Set.of("audio/vnd.wave", "audio/wav", "audio/x-wav")),
			Map.entry("mp3", Set.of("audio/mpeg")));

	public FileValidationService(@Value("${file.validation.max-image-size:5242880}") long maxImageSize,
			@Value("${file.validation.max-document-size:20971520}") long maxDocumentSize,
			@Value("${file.validation.max-media-size:104857600}") long maxMediaSize) {
		this.maxImageSize = maxImageSize;
		this.maxDocumentSize = maxDocumentSize;
		this.maxMediaSize = maxMediaSize;
	}

	/**
	 * 驗證上傳檔案（副檔名 + Magic bytes + 大小）。
	 * @param file MultipartFile
	 * @throws BusinessException 驗證失敗時
	 */
	public void validate(MultipartFile file) {
		if (file.isEmpty()) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR, "上傳檔案不可為空");
		}
		String ext = validateExtension(file.getOriginalFilename());
		validateSize(file.getSize(), ext);
		validateMagicBytes(file, ext);
	}

	/**
	 * 依副檔名類別驗證檔案大小。
	 */
	public void validateSize(long size, String extension) {
		long limit = getMaxSizeForExtension(extension);
		if (size > limit) {
			throw new BusinessException(ErrorCode.FILE_SIZE_EXCEEDED,
					String.format("檔案大小 %d bytes 超過 %d bytes 限制", size, limit));
		}
	}

	/**
	 * 驗證檔案大小（使用圖片類別上限作為通用限制）。
	 * @deprecated 建議使用 {@link #validateSize(long, String)} 搭配副檔名
	 */
	@Deprecated
	public void validateSize(long size) {
		long limit = maxDocumentSize;
		if (size > limit) {
			throw new BusinessException(ErrorCode.FILE_SIZE_EXCEEDED,
					String.format("檔案大小 %d bytes 超過 %d bytes 限制", size, limit));
		}
	}

	private long getMaxSizeForExtension(String extension) {
		if (extension == null) {
			return maxDocumentSize;
		}
		if (IMAGE_EXTENSIONS.contains(extension)) {
			return maxImageSize;
		}
		if (MEDIA_EXTENSIONS.contains(extension)) {
			return maxMediaSize;
		}
		return maxDocumentSize;
	}

	/**
	 * 驗證副檔名白名單，回傳小寫副檔名。
	 */
	public String validateExtension(String originalFilename) {
		String ext = extractExtension(originalFilename);
		if (ext == null || !ALLOWED_EXTENSIONS.contains(ext)) {
			throw new BusinessException(ErrorCode.FILE_EXTENSION_NOT_ALLOWED,
					"不允許的檔案類型: " + (ext != null ? ext : "無副檔名"));
		}
		return ext;
	}

	/**
	 * 以 Tika 偵測 Magic bytes，交叉比對副檔名。
	 */
	public void validateMagicBytes(MultipartFile file, String extension) {
		try (InputStream is = file.getInputStream()) {
			String detectedMime = tika.detect(is, file.getOriginalFilename());

			Set<String> allowedMimes = EXTENSION_MIME_MAP.get(extension);
			if (allowedMimes != null && !allowedMimes.contains(detectedMime)) {
				log.warn("Magic bytes mismatch: extension={}, detectedMime={}, file={}", extension, detectedMime,
						file.getOriginalFilename());
				throw new BusinessException(ErrorCode.FILE_TYPE_MISMATCH,
						String.format("檔案內容 (%s) 與副檔名 (.%s) 不符", detectedMime, extension));
			}
		}
		catch (IOException e) {
			log.error("Magic bytes 偵測失敗: {}", e.getMessage());
			throw new BusinessException(ErrorCode.ATTACHMENT_UPLOAD_FAILED, "檔案驗證失敗");
		}
	}

	/**
	 * 判斷是否為圖片類型。
	 */
	public boolean isImage(String extension) {
		return extension != null && IMAGE_EXTENSIONS.contains(extension);
	}

	private String extractExtension(String filename) {
		if (filename == null || !filename.contains("."))
			return null;
		return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
	}

}
