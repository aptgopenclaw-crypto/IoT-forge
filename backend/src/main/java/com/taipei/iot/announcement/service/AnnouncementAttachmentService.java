package com.taipei.iot.announcement.service;

import com.taipei.iot.announcement.dto.AnnouncementAttachmentResponse;
import com.taipei.iot.announcement.entity.Announcement;
import com.taipei.iot.announcement.entity.AnnouncementAttachment;
import com.taipei.iot.announcement.repository.AnnouncementAttachmentRepository;
import com.taipei.iot.announcement.repository.AnnouncementDeptRepository;
import com.taipei.iot.announcement.repository.AnnouncementRepository;
import com.taipei.iot.common.dto.UserInfo;
import com.taipei.iot.common.enums.ErrorCode;
import com.taipei.iot.common.exception.BusinessException;
import com.taipei.iot.common.service.FileStorageService;
import com.taipei.iot.common.service.FileValidationService;
import com.taipei.iot.common.util.SecurityContextUtils;
import com.taipei.iot.dept.enums.DataScopeEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 公告附件服務。
 * <p>
 * 沿用 {@link FileStorageService} / {@link FileValidationService} 做檔案落地與校驗， 中繼資料寫入
 * {@code announcement_attachments}。
 */
@Slf4j
@Service
public class AnnouncementAttachmentService {

	/** 每則公告最多附件數 */
	public static final int MAX_ATTACHMENTS_PER_ANNOUNCEMENT = 10;

	private final AnnouncementRepository announcementRepository;

	private final AnnouncementAttachmentRepository attachmentRepository;

	private final AnnouncementDeptRepository announcementDeptRepository;

	private final FileStorageService fileStorageService;

	private final FileValidationService fileValidationService;

	/**
	 * 公告附件專用副檔名白名單（全小寫）。
	 * <p>
	 * 預設僅允許 PDF。可透過 {@code announcement.attachments.allowed-extensions} 或 環境變數
	 * {@code ANNOUNCEMENT_ATTACHMENT_EXTS} 覆寫。
	 * <p>
	 * 此名單為 {@link FileValidationService} 全域白名單的縮小子集， 並在全域驗證通過後额外套用，拒絕 SVG/HTML/Office
	 * macro 檔 等高風險類型。
	 */
	private final Set<String> allowedExtensions;

	public AnnouncementAttachmentService(AnnouncementRepository announcementRepository,
			AnnouncementAttachmentRepository attachmentRepository,
			AnnouncementDeptRepository announcementDeptRepository, FileStorageService fileStorageService,
			FileValidationService fileValidationService,
			@Value("${announcement.attachments.allowed-extensions:pdf}") String allowedExtensionsConfig) {
		this.announcementRepository = announcementRepository;
		this.attachmentRepository = attachmentRepository;
		this.announcementDeptRepository = announcementDeptRepository;
		this.fileStorageService = fileStorageService;
		this.fileValidationService = fileValidationService;
		this.allowedExtensions = Arrays.stream(allowedExtensionsConfig.split(","))
			.map(String::trim)
			.filter(s -> !s.isEmpty())
			.map(String::toLowerCase)
			.collect(Collectors.toUnmodifiableSet());
	}

	/** 供前端 / 提示訊息使用 */
	public Set<String> getAllowedExtensions() {
		return allowedExtensions;
	}

	// ── 列表 / 詳情 ──

	@Transactional(readOnly = true)
	public List<AnnouncementAttachmentResponse> list(Long announcementId) {
		Announcement announcement = loadAndCheckVisible(announcementId);
		return attachmentRepository.findByAnnouncementIdOrderByIdAsc(announcement.getId())
			.stream()
			.map(this::toResponse)
			.toList();
	}

	/**
	 * 批次載入多筆公告的附件（避免 N+1）。
	 */
	@Transactional(readOnly = true)
	public Map<Long, List<AnnouncementAttachmentResponse>> listByAnnouncementIds(List<Long> announcementIds) {
		if (announcementIds == null || announcementIds.isEmpty())
			return Collections.emptyMap();
		return attachmentRepository.findByAnnouncementIdInOrderByIdAsc(announcementIds)
			.stream()
			.collect(Collectors.groupingBy(AnnouncementAttachment::getAnnouncementId,
					Collectors.mapping(this::toResponse, Collectors.toList())));
	}

	// ── 上傳 ──

	@Transactional
	public AnnouncementAttachmentResponse upload(Long announcementId, MultipartFile file) {
		UserInfo user = SecurityContextUtils.getUserInfo();
		Announcement announcement = loadAndCheckManage(announcementId, user);

		// 上限檢查
		long current = attachmentRepository.findByAnnouncementIdOrderByIdAsc(announcement.getId()).size();
		if (current >= MAX_ATTACHMENTS_PER_ANNOUNCEMENT) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR,
					"每則公告最多 " + MAX_ATTACHMENTS_PER_ANNOUNCEMENT + " 個附件");
		}

		// 校驗（副檔名 / Magic bytes / 大小）
		fileValidationService.validate(file);

		// 公告附件專用白名單（默認僅 PDF）——縮小攻擊面
		String ext = extractExtension(file.getOriginalFilename());
		if (ext == null || !allowedExtensions.contains(ext)) {
			throw new BusinessException(ErrorCode.FILE_EXTENSION_NOT_ALLOWED,
					"公告附件僅允許以下類型：" + String.join(", ", allowedExtensions));
		}

		// 落地
		String subDir = "announcement/" + announcement.getId();
		String relativePath = fileStorageService.store(subDir, file);

		AnnouncementAttachment entity = AnnouncementAttachment.builder()
			.announcementId(announcement.getId())
			.fileName(safeOriginalName(file.getOriginalFilename()))
			.fileSize(file.getSize())
			.mimeType(file.getContentType() != null ? file.getContentType() : "application/octet-stream")
			.filePath(relativePath)
			.createdBy(user.getUserId())
			.build();
		attachmentRepository.save(entity);
		log.info("Announcement attachment uploaded: announcementId={}, attachmentId={}, file={}", announcement.getId(),
				entity.getId(), entity.getFileName());

		return toResponse(entity);
	}

	// ── 下載 ──

	/**
	 * 取得附件下載用的 InputStream + 中繼資料；呼叫端負責關閉串流。
	 */
	@Transactional(readOnly = true)
	public DownloadHandle download(Long announcementId, Long attachmentId) {
		Announcement announcement = loadAndCheckVisible(announcementId);
		AnnouncementAttachment attachment = attachmentRepository.findById(attachmentId)
			.orElseThrow(() -> new BusinessException(ErrorCode.ATTACHMENT_NOT_FOUND));
		if (!attachment.getAnnouncementId().equals(announcement.getId())) {
			throw new BusinessException(ErrorCode.ATTACHMENT_NOT_FOUND);
		}
		InputStream stream = fileStorageService.load(attachment.getFilePath());
		return new DownloadHandle(stream, attachment.getFileName(), attachment.getMimeType(), attachment.getFileSize());
	}

	public record DownloadHandle(InputStream stream, String fileName, String mimeType, long size) {
	}

	// ── 刪除 ──

	@Transactional
	public void delete(Long announcementId, Long attachmentId) {
		UserInfo user = SecurityContextUtils.getUserInfo();
		Announcement announcement = loadAndCheckManage(announcementId, user);
		AnnouncementAttachment attachment = attachmentRepository.findById(attachmentId)
			.orElseThrow(() -> new BusinessException(ErrorCode.ATTACHMENT_NOT_FOUND));
		if (!attachment.getAnnouncementId().equals(announcement.getId())) {
			throw new BusinessException(ErrorCode.ATTACHMENT_NOT_FOUND);
		}
		// 先刪 DB（在交易內），再刪實體檔案（容錯：檔案不存在亦不拋）
		attachmentRepository.delete(attachment);
		fileStorageService.delete(attachment.getFilePath());
		log.info("Announcement attachment deleted: announcementId={}, attachmentId={}", announcement.getId(),
				attachmentId);
	}

	// ── helpers ──

	private Announcement loadAndCheckVisible(Long announcementId) {
		Announcement entity = announcementRepository.findById(announcementId)
			.orElseThrow(() -> new BusinessException(ErrorCode.ANNOUNCEMENT_NOT_FOUND));

		UserInfo user = SecurityContextUtils.getUserInfo();
		if (hasManagePermission()) {
			return entity;
		}
		LocalDateTime now = LocalDateTime.now();
		boolean published = "PUBLISHED".equals(entity.getStatus());
		boolean notExpired = entity.getExpireAt() == null || entity.getExpireAt().isAfter(now);
		boolean started = entity.getPublishAt() != null && !entity.getPublishAt().isAfter(now);
		boolean scopeMatch = "ALL".equals(entity.getScope()) || (user.getDeptId() != null
				&& announcementDeptRepository.existsByAnnouncementIdAndDeptId(entity.getId(), user.getDeptId()));
		if (!published || !notExpired || !started || !scopeMatch) {
			throw new BusinessException(ErrorCode.ANNOUNCEMENT_NOT_FOUND);
		}
		return entity;
	}

	private Announcement loadAndCheckManage(Long announcementId, UserInfo user) {
		Announcement entity = announcementRepository.findById(announcementId)
			.orElseThrow(() -> new BusinessException(ErrorCode.ANNOUNCEMENT_NOT_FOUND));
		DataScopeEnum dataScope = DataScopeEnum.fromString(user.getDataScope());
		if (dataScope != DataScopeEnum.ALL && !entity.getCreatedBy().equals(user.getUserId())) {
			throw new BusinessException(ErrorCode.PERMISSION_DENIED);
		}
		return entity;
	}

	private boolean hasManagePermission() {
		var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
		if (auth == null)
			return false;
		return auth.getAuthorities().stream().anyMatch(a -> "ANNOUNCEMENT_MANAGE".equals(a.getAuthority()));
	}

	private String safeOriginalName(String original) {
		if (original == null || original.isBlank())
			return "unnamed";
		// 去除路徑分隔符與控制字元
		String cleaned = original.replaceAll("[/\\\\\\x00]", "_");
		return cleaned.length() > 255 ? cleaned.substring(0, 255) : cleaned;
	}

	private String extractExtension(String filename) {
		if (filename == null || !filename.contains("."))
			return null;
		return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
	}

	private AnnouncementAttachmentResponse toResponse(AnnouncementAttachment entity) {
		return AnnouncementAttachmentResponse.builder()
			.id(entity.getId())
			.announcementId(entity.getAnnouncementId())
			.fileName(entity.getFileName())
			.fileSize(entity.getFileSize())
			.mimeType(entity.getMimeType())
			.createdAt(entity.getCreatedAt())
			.build();
	}

}
