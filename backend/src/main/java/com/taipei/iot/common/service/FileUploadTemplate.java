package com.taipei.iot.common.service;

import com.taipei.iot.common.enums.ErrorCode;
import com.taipei.iot.common.event.VirusScanAuditEvent;
import com.taipei.iot.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Objects;
import java.util.Set;

/**
 * 檔案上傳 pipeline 範本：validate → (optional) virus scan → store。
 *
 * <p>
 * 把各業務模組散落的「驗證 + 掃毒 + 落地」樣板收斂到單一進入點， 確保所有上傳走相同安全流程（特別是 fail-closed 掃毒語意），
 * 並提供模組層自訂副檔名白名單的擴充點。
 *
 * <h2>使用範例</h2> <pre>{@code
 *   FileUploadTemplate.Result result = fileUploadTemplate.upload(
 *       FileUploadTemplate.UploadRequest.builder()
 *           .file(multipartFile)
 *           .subDir("announcement/" + announcementId)
 *           .additionalAllowedExtensions(Set.of("pdf"))
 *           .scanAfterStore(true)
 *           .build()
 *   );
 *   // result.relativePath() → DB 存檔欄位
 * }</pre>
 *
 * <h2>流程</h2>
 * <ol>
 * <li>{@link FileValidationService#validate(MultipartFile)} — 副檔名 / magic bytes / 大小</li>
 * <li>若 {@code additionalAllowedExtensions} 非空，再做模組層白名單檢查（更嚴格）</li>
 * <li>{@link FileStorageService#store(String, MultipartFile)} — 落地，取得 relative path</li>
 * <li>若 {@code scanAfterStore=true}，呼叫 {@link VirusScanService#scan(String)}； 結果為
 * {@code INFECTED} 或 {@code ERROR} 一律 fail-closed（刪掉檔案 + 拋業務例外）</li>
 * </ol>
 *
 * <p>
 * <b>注意</b>：本 template 不負責 DB 紀錄 / audit log 寫入； 呼叫端拿到 {@link Result}
 * 後自行決定後續處置（保留彈性以對應不同的 entity 結構）。
 *
 * <p>
 * [common v2 F-16]
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class FileUploadTemplate {

	private final FileValidationService fileValidationService;

	private final FileStorageService fileStorageService;

	private final VirusScanService virusScanService;

	private final ApplicationEventPublisher eventPublisher;

	/**
	 * 執行上傳 pipeline。
	 * @throws BusinessException 驗證 / 掃毒任一階段失敗時拋出
	 */
	public Result upload(UploadRequest request) {
		Objects.requireNonNull(request, "request must not be null");
		MultipartFile file = Objects.requireNonNull(request.file(), "file must not be null");
		String subDir = Objects.requireNonNull(request.subDir(), "subDir must not be null");

		// 1) 通用驗證
		fileValidationService.validate(file);

		// 2) 模組層白名單（更嚴格）
		Set<String> moduleWhitelist = request.additionalAllowedExtensions();
		if (moduleWhitelist != null && !moduleWhitelist.isEmpty()) {
			String ext = extractExtension(file.getOriginalFilename());
			if (ext == null || !moduleWhitelist.contains(ext)) {
				throw new BusinessException(ErrorCode.FILE_EXTENSION_NOT_ALLOWED,
						"僅允許以下類型：" + String.join(", ", moduleWhitelist));
			}
		}

		// 3) 落地
		String relativePath = fileStorageService.store(subDir, file);

		// 4) Fail-closed 掃毒
		if (request.scanAfterStore()) {
			VirusScanService.ScanResult scanResult;
			try {
				String absolutePath = fileStorageService.resolveAbsolutePath(relativePath);
				scanResult = virusScanService.scan(absolutePath);
			}
			catch (RuntimeException ex) {
				log.error("Virus scan threw unexpected exception, fail-closed (delete file): {}", relativePath, ex);
				publishAuditEvent(VirusScanAuditEvent.Result.ERROR, relativePath, file, subDir);
				safeDelete(relativePath);
				throw new BusinessException(ErrorCode.ATTACHMENT_UPLOAD_FAILED, "病毒掃描異常，請稍後重試");
			}
			if (scanResult == VirusScanService.ScanResult.INFECTED) {
				log.warn("Infected file rejected and deleted: {}", relativePath);
				publishAuditEvent(VirusScanAuditEvent.Result.INFECTED, relativePath, file, subDir);
				safeDelete(relativePath);
				throw new BusinessException(ErrorCode.FILE_VIRUS_DETECTED, "檔案疑似含有惡意內容，已被拒絕");
			}
			if (scanResult == VirusScanService.ScanResult.ERROR) {
				log.error("Virus scan returned ERROR, fail-closed (delete file): {}", relativePath);
				publishAuditEvent(VirusScanAuditEvent.Result.ERROR, relativePath, file, subDir);
				safeDelete(relativePath);
				throw new BusinessException(ErrorCode.ATTACHMENT_UPLOAD_FAILED, "病毒掃描不可用，請稍後重試");
			}
		}

		return new Result(relativePath, file.getOriginalFilename(), file.getSize(),
				file.getContentType() != null ? file.getContentType() : "application/octet-stream");
	}

	private void safeDelete(String relativePath) {
		try {
			fileStorageService.delete(relativePath);
		}
		catch (RuntimeException ex) {
			log.warn("Cleanup failed for {}: {}", relativePath, ex.getMessage());
		}
	}

	/**
	 * [common v2 F-3] 發送掃毒審計事件；any-failure best-effort，不影響原本 fail-closed 流程。
	 */
	private void publishAuditEvent(VirusScanAuditEvent.Result result, String relativePath, MultipartFile file,
			String subDir) {
		try {
			eventPublisher.publishEvent(
					new VirusScanAuditEvent(result, relativePath, file.getOriginalFilename(), file.getSize(), subDir));
		}
		catch (RuntimeException ex) {
			log.warn("Failed to publish VirusScanAuditEvent ({}): {}", result, ex.getMessage());
		}
	}

	private static String extractExtension(String fileName) {
		if (fileName == null)
			return null;
		int dot = fileName.lastIndexOf('.');
		if (dot < 0 || dot >= fileName.length() - 1)
			return null;
		return fileName.substring(dot + 1).toLowerCase();
	}

	/**
	 * 上傳請求。使用 builder 避免將來新增欄位破壞呼叫端。
	 */
	public record UploadRequest(MultipartFile file, String subDir, Set<String> additionalAllowedExtensions,
			boolean scanAfterStore) {

		public static Builder builder() {
			return new Builder();
		}

		public static final class Builder {

			private MultipartFile file;

			private String subDir;

			private Set<String> additionalAllowedExtensions;

			private boolean scanAfterStore;

			public Builder file(MultipartFile file) {
				this.file = file;
				return this;
			}

			public Builder subDir(String subDir) {
				this.subDir = subDir;
				return this;
			}

			public Builder additionalAllowedExtensions(Set<String> exts) {
				this.additionalAllowedExtensions = exts;
				return this;
			}

			public Builder scanAfterStore(boolean scan) {
				this.scanAfterStore = scan;
				return this;
			}

			public UploadRequest build() {
				return new UploadRequest(file, subDir, additionalAllowedExtensions, scanAfterStore);
			}

		}
	}

	/**
	 * 上傳結果。呼叫端據此寫入 DB（attachment 表等）。
	 */
	public record Result(String relativePath, String originalFileName, long size, String mimeType) {
	}

}
