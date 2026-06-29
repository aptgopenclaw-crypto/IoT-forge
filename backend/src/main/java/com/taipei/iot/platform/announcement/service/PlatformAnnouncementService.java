package com.taipei.iot.platform.announcement.service;

import com.taipei.iot.announcement.service.HtmlSanitizerService;
import com.taipei.iot.auth.entity.UserEntity;
import com.taipei.iot.auth.repository.UserRepository;
import com.taipei.iot.common.dto.PageResponse;
import com.taipei.iot.common.enums.ErrorCode;
import com.taipei.iot.common.exception.BusinessException;
import com.taipei.iot.common.dto.UserInfo;
import com.taipei.iot.common.util.SecurityContextUtils;
import com.taipei.iot.platform.announcement.dto.PlatformAnnouncementRequest;
import com.taipei.iot.platform.announcement.dto.PlatformAnnouncementResponse;
import com.taipei.iot.platform.announcement.entity.PlatformAnnouncement;
import com.taipei.iot.platform.announcement.repository.PlatformAnnouncementRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlatformAnnouncementService {

	private final PlatformAnnouncementRepository repository;

	private final UserRepository userRepository;

	private final HtmlSanitizerService htmlSanitizerService;

	// ── 管理端列表 ──

	@Transactional(readOnly = true)
	public PageResponse<PlatformAnnouncementResponse> listAdmin(String statusFilter, String category, String keyword,
			int page, int size) {
		Page<PlatformAnnouncement> result = repository.findAdminList(statusFilter, category, keyword,
				LocalDateTime.now(), PageRequest.of(page, size));
		return toPageResponse(result);
	}

	// ── 前台列表（已發佈且未過期） ──

	@Transactional(readOnly = true)
	public PageResponse<PlatformAnnouncementResponse> listPublished(String category, int page, int size) {
		Page<PlatformAnnouncement> result = repository.findPublished(category, LocalDateTime.now(),
				PageRequest.of(page, size));
		return toPageResponse(result);
	}

	// ── 單筆查詢 ──

	@Transactional(readOnly = true)
	public PlatformAnnouncementResponse getById(Long id) {
		PlatformAnnouncement entity = repository.findById(id)
			.orElseThrow(() -> new BusinessException(ErrorCode.ANNOUNCEMENT_NOT_FOUND, "平台公告不存在: " + id));
		return toResponse(entity);
	}

	// ── 新增 ──

	@Transactional
	public PlatformAnnouncementResponse create(PlatformAnnouncementRequest request) {
		UserInfo user = SecurityContextUtils.getUserInfo();
		String displayName = resolveDisplayName(user.getUserId());

		String safeContent = htmlSanitizerService.sanitize(request.getContent());
		String contentText = htmlSanitizerService.extractText(safeContent);

		PlatformAnnouncement entity = PlatformAnnouncement.builder()
			.title(request.getTitle())
			.content(safeContent)
			.contentText(contentText)
			.status(request.getStatus())
			.category(request.getCategory() != null ? request.getCategory() : "SYSTEM")
			.publishAt(resolvePublishAt(request))
			.expireAt(request.getExpireAt())
			.createdBy(user.getUserId())
			.createdByName(displayName)
			.build();

		repository.save(entity);
		log.info("Platform announcement created: id={}, title={}, by={}", entity.getId(), entity.getTitle(),
				user.getUserId());
		return toResponse(entity);
	}

	// ── 更新 ──

	@Transactional
	public PlatformAnnouncementResponse update(Long id, PlatformAnnouncementRequest request) {
		PlatformAnnouncement entity = repository.findById(id)
			.orElseThrow(() -> new BusinessException(ErrorCode.ANNOUNCEMENT_NOT_FOUND, "平台公告不存在: " + id));

		String safeContent = htmlSanitizerService.sanitize(request.getContent());
		String contentText = htmlSanitizerService.extractText(safeContent);

		entity.setTitle(request.getTitle());
		entity.setContent(safeContent);
		entity.setContentText(contentText);
		entity.setStatus(request.getStatus());
		entity.setCategory(request.getCategory() != null ? request.getCategory() : entity.getCategory());
		entity.setPublishAt(resolvePublishAt(request));
		entity.setExpireAt(request.getExpireAt());

		repository.save(entity);
		log.info("Platform announcement updated: id={}", id);
		return toResponse(entity);
	}

	// ── 刪除 ──

	@Transactional
	public void delete(Long id) {
		if (!repository.existsById(id)) {
			throw new BusinessException(ErrorCode.ANNOUNCEMENT_NOT_FOUND, "平台公告不存在: " + id);
		}
		repository.deleteById(id);
		log.info("Platform announcement deleted: id={}", id);
	}

	// ── private helpers ──

	private String resolveDisplayName(String userId) {
		return userRepository.findById(userId).map(UserEntity::getDisplayName).orElse(userId);
	}

	private LocalDateTime resolvePublishAt(PlatformAnnouncementRequest request) {
		if ("PUBLISHED".equals(request.getStatus()) && request.getPublishAt() == null) {
			return LocalDateTime.now();
		}
		return request.getPublishAt();
	}

	private PlatformAnnouncementResponse toResponse(PlatformAnnouncement entity) {
		return PlatformAnnouncementResponse.builder()
			.id(entity.getId())
			.title(entity.getTitle())
			.content(entity.getContent())
			.status(entity.getStatus())
			.category(entity.getCategory())
			.publishAt(entity.getPublishAt())
			.expireAt(entity.getExpireAt())
			.createdBy(entity.getCreatedBy())
			.createdByName(entity.getCreatedByName())
			.createdAt(entity.getCreatedAt())
			.updatedAt(entity.getUpdatedAt())
			.build();
	}

	private PageResponse<PlatformAnnouncementResponse> toPageResponse(Page<PlatformAnnouncement> page) {
		return PageResponse.<PlatformAnnouncementResponse>builder()
			.content(page.getContent().stream().map(this::toResponse).toList())
			.totalElements(page.getTotalElements())
			.totalPages(page.getTotalPages())
			.page(page.getNumber())
			.size(page.getSize())
			.build();
	}

}
