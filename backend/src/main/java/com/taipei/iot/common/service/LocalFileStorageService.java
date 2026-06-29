package com.taipei.iot.common.service;

import com.taipei.iot.common.enums.ErrorCode;
import com.taipei.iot.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;
import java.util.UUID;

/**
 * 本地磁碟檔案儲存實作。
 * <p>
 * 後續可替換為 S3/MinIO 實作，只需實作 {@link FileStorageService} 介面。
 * </p>
 *
 * <h3>N-7 防護（2026-05-27）：顯式 POSIX 檔案權限</h3>
 * <p>
 * {@link Files#copy(InputStream, Path, java.nio.file.CopyOption...)} 預設套用作業系統 umask；
 * 若部署環境 umask 寬鬆（如 {@code 0002}），上傳檔案會被同主機其他帳號讀寫。本實作於檔案 / 目錄 建立後立即明確設定權限：
 * </p>
 * <ul>
 * <li>檔案：{@code rw-------}（僅擁有者可讀寫，0600）</li>
 * <li>目錄：{@code rwx------}（僅擁有者可進入/讀/寫，0700）</li>
 * </ul>
 * <p>
 * FileSystem 不支援 POSIX（Windows / 某些網路掛載）時自動 no-op，不影響功能。
 * </p>
 */
@Slf4j
@Service
public class LocalFileStorageService implements FileStorageService {

	private static final Set<PosixFilePermission> FILE_PERMISSIONS = PosixFilePermissions.fromString("rw-------");

	private static final Set<PosixFilePermission> DIR_PERMISSIONS = PosixFilePermissions.fromString("rwx------");

	private final Path rootLocation;

	public LocalFileStorageService(@Value("${file.storage.local.root-dir:./uploads}") String rootDir) {
		this.rootLocation = Paths.get(rootDir).toAbsolutePath().normalize();
		try {
			Files.createDirectories(this.rootLocation);
			applyDirPermissions(this.rootLocation);
		}
		catch (IOException e) {
			throw new RuntimeException("無法建立檔案儲存目錄: " + rootDir, e);
		}
	}

	@Override
	public String store(String subDir, String fileName, InputStream input) {
		try {
			String safeFileName = sanitizeFileName(fileName);
			String uniqueName = UUID.randomUUID().toString().substring(0, 8) + "_" + safeFileName;

			Path targetDir = rootLocation.resolve(subDir).normalize();
			if (!targetDir.startsWith(rootLocation)) {
				throw new BusinessException(ErrorCode.VALIDATION_ERROR, "非法的儲存路徑");
			}
			Files.createDirectories(targetDir);
			applyDirPermissions(targetDir);

			Path targetFile = targetDir.resolve(uniqueName).normalize();
			if (!targetFile.startsWith(targetDir)) {
				throw new BusinessException(ErrorCode.VALIDATION_ERROR, "非法的檔案名稱");
			}

			Files.copy(input, targetFile, StandardCopyOption.REPLACE_EXISTING);
			applyFilePermissions(targetFile);

			// 回傳相對路徑
			return rootLocation.relativize(targetFile).toString();
		}
		catch (IOException e) {
			log.error("檔案儲存失敗: {}", e.getMessage(), e);
			throw new BusinessException(ErrorCode.ATTACHMENT_UPLOAD_FAILED);
		}
	}

	@Override
	public String store(String subDir, MultipartFile file) {
		if (file.isEmpty()) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR, "上傳檔案不可為空");
		}

		try {
			return store(subDir, file.getOriginalFilename(), file.getInputStream());
		}
		catch (IOException e) {
			log.error("讀取上傳檔案失敗: {}", e.getMessage(), e);
			throw new BusinessException(ErrorCode.ATTACHMENT_UPLOAD_FAILED);
		}
	}

	@Override
	public InputStream load(String path) {
		try {
			Path file = rootLocation.resolve(path).normalize();
			if (!file.startsWith(rootLocation)) {
				throw new BusinessException(ErrorCode.VALIDATION_ERROR, "非法的檔案路徑");
			}
			if (!Files.exists(file)) {
				throw new BusinessException(ErrorCode.ATTACHMENT_NOT_FOUND);
			}
			return Files.newInputStream(file);
		}
		catch (IOException e) {
			log.error("讀取檔案失敗: {}", e.getMessage(), e);
			throw new BusinessException(ErrorCode.ATTACHMENT_NOT_FOUND);
		}
	}

	@Override
	public void delete(String path) {
		try {
			Path file = resolveSafePath(path);
			Files.deleteIfExists(file);
		}
		catch (IOException e) {
			log.warn("刪除檔案失敗: {}", e.getMessage());
		}
	}

	@Override
	public boolean deleteIfExists(String path) {
		Path file = resolveSafePath(path);
		try {
			return Files.deleteIfExists(file);
		}
		catch (IOException e) {
			// F-6：與既有 delete() 的 best-effort 語意一致 — 不再向上拋，改 log WARN 並回 false。
			log.warn("刪除檔案失敗（檔案可能仍存在）: path={}, error={}", path, e.getMessage());
			return false;
		}
	}

	@Override
	public String move(String fromPath, String toSubDir, String newFileName) {
		Path source = resolveSafePath(fromPath);
		if (!Files.exists(source)) {
			throw new BusinessException(ErrorCode.ATTACHMENT_NOT_FOUND);
		}

		try {
			String safeFileName = sanitizeFileName(newFileName);
			String uniqueName = UUID.randomUUID().toString().substring(0, 8) + "_" + safeFileName;

			Path targetDir = rootLocation.resolve(toSubDir).normalize();
			if (!targetDir.startsWith(rootLocation)) {
				throw new BusinessException(ErrorCode.VALIDATION_ERROR, "非法的儲存路徑");
			}
			Files.createDirectories(targetDir);
			applyDirPermissions(targetDir);

			Path target = targetDir.resolve(uniqueName).normalize();
			if (!target.startsWith(targetDir)) {
				throw new BusinessException(ErrorCode.VALIDATION_ERROR, "非法的檔案名稱");
			}

			try {
				Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
			}
			catch (AtomicMoveNotSupportedException atomicEx) {
				// 跨 mount point / 某些網路 FS 不支援 atomic：退回非原子搬移
				log.debug("ATOMIC_MOVE 不被 FS 支援，退回 REPLACE_EXISTING: {}", atomicEx.getMessage());
				Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
			}
			applyFilePermissions(target);

			return rootLocation.relativize(target).toString();
		}
		catch (IOException e) {
			log.error("檔案移動失敗: from={}, toSubDir={}, error={}", fromPath, toSubDir, e.getMessage(), e);
			throw new BusinessException(ErrorCode.ATTACHMENT_UPLOAD_FAILED);
		}
	}

	/**
	 * F-6：集中 path traversal 檢查，供 load / delete / deleteIfExists / move /
	 * resolveAbsolutePath 共用。
	 */
	private Path resolveSafePath(String path) {
		Path file = rootLocation.resolve(path).normalize();
		if (!file.startsWith(rootLocation)) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR, "非法的檔案路徑");
		}
		return file;
	}

	@Override
	public String resolveAbsolutePath(String path) {
		Path file = rootLocation.resolve(path).normalize();
		if (!file.startsWith(rootLocation)) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR, "非法的檔案路徑");
		}
		return file.toAbsolutePath().toString();
	}

	/**
	 * 清除檔案名稱中的路徑穿越字元。
	 */
	private String sanitizeFileName(String fileName) {
		if (fileName == null || fileName.isBlank()) {
			return "unnamed";
		}
		// 移除路徑分隔符號與 null bytes
		return fileName.replaceAll("[/\\\\\\x00]", "_").replaceAll("\\.\\.", "_");
	}

	/**
	 * N-7：將檔案權限收緊至 {@code rw-------}（0600）。 非 POSIX FileSystem（如 Windows）時 silent no-op。
	 */
	private void applyFilePermissions(Path file) {
		applyPosixPermissions(file, FILE_PERMISSIONS, "file");
	}

	/**
	 * N-7：將目錄權限收緊至 {@code rwx------}（0700）。 非 POSIX FileSystem 時 silent no-op。
	 */
	private void applyDirPermissions(Path dir) {
		applyPosixPermissions(dir, DIR_PERMISSIONS, "directory");
	}

	private void applyPosixPermissions(Path path, Set<PosixFilePermission> perms, String kind) {
		FileSystem fs = path.getFileSystem();
		if (!fs.supportedFileAttributeViews().contains("posix")) {
			return; // Windows / 非 POSIX 掛載：無能為力
		}
		try {
			Files.setPosixFilePermissions(path, perms);
		}
		catch (IOException | UnsupportedOperationException e) {
			// 失敗不阻擋主流程（檔案已寫入），但記 WARN 讓維運可見
			log.warn("Failed to apply POSIX permissions on {} {}: {}", kind, path, e.getMessage());
		}
	}

}
