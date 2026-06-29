package com.taipei.iot.announcement.service;

import com.taipei.iot.announcement.dto.AnnouncementAttachmentResponse;
import com.taipei.iot.announcement.dto.AnnouncementRequest;
import com.taipei.iot.announcement.dto.AnnouncementResponse;
import com.taipei.iot.announcement.dto.AnnouncementTranslationDto;
import com.taipei.iot.announcement.entity.Announcement;
import com.taipei.iot.announcement.entity.AnnouncementDept;
import com.taipei.iot.announcement.entity.AnnouncementScope;
import com.taipei.iot.announcement.entity.AnnouncementTranslation;
import com.taipei.iot.announcement.repository.AnnouncementDeptRepository;
import com.taipei.iot.announcement.repository.AnnouncementReadRepository;
import com.taipei.iot.announcement.repository.AnnouncementRepository;
import com.taipei.iot.announcement.repository.AnnouncementTranslationRepository;
import com.taipei.iot.auth.entity.UserEntity;
import com.taipei.iot.auth.repository.UserRepository;
import com.taipei.iot.common.dto.UserInfo;
import com.taipei.iot.common.enums.ErrorCode;
import com.taipei.iot.common.exception.BusinessException;
import com.taipei.iot.common.util.LangNormalizer;
import com.taipei.iot.common.util.PageConversionHelper;
import com.taipei.iot.common.util.SecurityContextUtils;
import com.taipei.iot.common.util.SqlLikeEscaper;
import com.taipei.iot.dept.entity.DeptInfoEntity;
import com.taipei.iot.dept.repository.DeptInfoRepository;
import com.taipei.iot.dept.enums.DataScopeEnum;
import com.taipei.iot.common.dto.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AnnouncementService {

	private final AnnouncementRepository announcementRepository;

	private final AnnouncementDeptRepository announcementDeptRepository;

	private final AnnouncementReadRepository announcementReadRepository;

	private final AnnouncementTranslationRepository announcementTranslationRepository;

	private final DeptInfoRepository deptInfoRepository;

	private final UserRepository userRepository;

	private final HtmlSanitizerService htmlSanitizerService;

	private final AnnouncementAttachmentService attachmentService;

	/**
	 * 系統預設語言；同時是前端 i18n 的 fallback，也是 announcements 主表 title/content 所代表的語言。
	 */
	public static final String DEFAULT_LANG = "zh-TW";

	/** 支援語言白名單，避免任意輸入。 */
	private static final Set<String> SUPPORTED_LANGS = Set.of("zh-TW", "zh-CN", "en");

	private static final Sort DEFAULT_SORT = Sort.by(Sort.Order.desc("pinned"), Sort.Order.asc("pinOrder").nullsLast(),
			Sort.Order.desc("publishAt"));

	// ── 前台查詢 ──

	@Transactional(readOnly = true)
	public PageResponse<AnnouncementResponse> listVisible(String category, int page, int size) {
		return listVisible(category, page, size, DEFAULT_LANG);
	}

	@Transactional(readOnly = true)
	public PageResponse<AnnouncementResponse> listVisible(String category, int page, int size, String langCode) {
		UserInfo user = SecurityContextUtils.getUserInfo();
		LocalDateTime now = LocalDateTime.now();
		Long deptId = user.getDeptId() != null ? user.getDeptId() : -1L;
		String safeCategory = normalizeCategoryFilter(category);
		String safeLang = normalizeLang(langCode);

		Pageable pageable = PageRequest.of(page, size, DEFAULT_SORT);
		Page<Announcement> pageResult = announcementRepository.findVisibleAnnouncements(deptId, safeCategory, now,
				pageable);

		List<AnnouncementResponse> content = toResponseList(pageResult.getContent(), user.getUserId(), false, safeLang);
		return PageConversionHelper.from(content, pageResult);
	}

	// ── 管理頁面查詢 ──

	@Transactional(readOnly = true)
	public PageResponse<AnnouncementResponse> listAdmin(String statusFilter, String category, String keyword, int page,
			int size) {
		return listAdmin(statusFilter, category, keyword, page, size, DEFAULT_LANG);
	}

	@Transactional(readOnly = true)
	public PageResponse<AnnouncementResponse> listAdmin(String statusFilter, String category, String keyword, int page,
			int size, String langCode) {
		UserInfo user = SecurityContextUtils.getUserInfo();
		String safeLang = normalizeLang(langCode);
		LocalDateTime now = LocalDateTime.now();
		String safeFilter = statusFilter != null ? statusFilter : "ALL";
		String safeCategory = normalizeCategoryFilter(category);
		String safeKeyword = null;
		if (keyword != null && !keyword.isBlank()) {
			safeKeyword = SqlLikeEscaper.contains(keyword.trim());
		}

		Pageable pageable = PageRequest.of(page, size, DEFAULT_SORT);
		Page<Announcement> pageResult;

		DataScopeEnum scope = DataScopeEnum.fromString(user.getDataScope());
		if (scope == DataScopeEnum.ALL) {
			// ADMIN: 看全部
			pageResult = announcementRepository.findAdminAnnouncements(safeFilter, safeCategory, safeKeyword, now,
					pageable);
		}
		else {
			// DEPT_ADMIN: 自己建立的 + 受眾包含自己部門的
			Long deptId = user.getDeptId() != null ? user.getDeptId() : -1L;
			pageResult = announcementRepository.findDeptAdminAnnouncements(user.getUserId(), deptId, safeFilter,
					safeCategory, safeKeyword, now, pageable);
		}

		List<AnnouncementResponse> content = toResponseList(pageResult.getContent(), user.getUserId(), true, safeLang);
		return PageConversionHelper.from(content, pageResult);
	}

	// ── 單筆查詢 ──

	@Transactional(readOnly = true)
	public AnnouncementResponse getById(Long id, boolean hasManagePermission) {
		return getById(id, hasManagePermission, DEFAULT_LANG);
	}

	@Transactional(readOnly = true)
	public AnnouncementResponse getById(Long id, boolean hasManagePermission, String langCode) {
		UserInfo user = SecurityContextUtils.getUserInfo();
		String safeLang = normalizeLang(langCode);
		Announcement entity = announcementRepository.findById(id)
			.orElseThrow(() -> new BusinessException(ErrorCode.ANNOUNCEMENT_NOT_FOUND));

		// 非管理員：只能看已發佈、未過期、且 scope 符合的公告
		if (!hasManagePermission) {
			LocalDateTime now = LocalDateTime.now();
			boolean published = "PUBLISHED".equals(entity.getStatus());
			boolean notExpired = entity.getExpireAt() == null || entity.getExpireAt().isAfter(now);
			boolean started = entity.getPublishAt() != null && !entity.getPublishAt().isAfter(now);
			boolean scopeMatch = "ALL".equals(entity.getScope()) || (user.getDeptId() != null
					&& announcementDeptRepository.existsByAnnouncementIdAndDeptId(entity.getId(), user.getDeptId()));

			if (!published || !notExpired || !started || !scopeMatch) {
				throw new BusinessException(ErrorCode.ANNOUNCEMENT_NOT_FOUND);
			}
		}

		return toResponse(entity, user.getUserId(), false, safeLang);
	}

	// ── 新增 ──

	@Transactional
	public AnnouncementResponse create(AnnouncementRequest request) {
		UserInfo user = SecurityContextUtils.getUserInfo();
		String displayName = resolveDisplayName(user.getUserId());

		String safeContent = htmlSanitizerService.sanitize(request.getContent());
		String contentText = htmlSanitizerService.extractText(safeContent);

		Announcement entity = Announcement.builder()
			.title(request.getTitle())
			.content(safeContent)
			.contentText(contentText)
			.status(request.getStatus())
			.scope(request.getScope())
			.category(normalizeCategory(request.getCategory()))
			.pinned(request.getPinned() != null ? request.getPinned() : false)
			.requiresAck(request.getRequiresAck() != null ? request.getRequiresAck() : false)
			.publishAt(resolvePublishAt(request))
			.expireAt(request.getExpireAt())
			.createdBy(user.getUserId())
			.createdByName(displayName)
			.build();

		// 置頂順序：pinned=true 且未提供時自動指定為現有 max+1；pinned=false 則清為 null
		entity.setPinOrder(resolvePinOrderForSave(entity.getPinned(), request.getPinOrder()));

		// DEPT_ADMIN 強制 scope=DEPT, targetDeptIds=[自己部門]
		DataScopeEnum dataScope = DataScopeEnum.fromString(user.getDataScope());
		List<Long> targetDeptIds;
		if (dataScope != DataScopeEnum.ALL) {
			entity.setScope(AnnouncementScope.DEPT.getValue());
			targetDeptIds = List.of(user.getDeptId());
		}
		else {
			targetDeptIds = request.getTargetDeptIds();
			if (AnnouncementScope.DEPT.getValue().equals(entity.getScope())) {
				if (targetDeptIds == null || targetDeptIds.isEmpty()) {
					throw new BusinessException(ErrorCode.VALIDATION_ERROR, "指定部門公告必須選擇至少一個部門");
				}
			}
		}

		announcementRepository.save(entity);

		// 儲存 junction table
		if (AnnouncementScope.DEPT.getValue().equals(entity.getScope()) && targetDeptIds != null) {
			saveAnnouncementDepts(entity.getId(), targetDeptIds);
		}

		// 儲存额外語言翻譯（zh-TW 以主表為準，不重複寫入子表）
		replaceTranslations(entity.getId(), request.getTranslations());

		return toResponse(entity, user.getUserId(), false, DEFAULT_LANG);
	}

	// ── 編輯 ──

	@Transactional
	public AnnouncementResponse update(Long id, AnnouncementRequest request) {
		UserInfo user = SecurityContextUtils.getUserInfo();

		Announcement entity = announcementRepository.findById(id)
			.orElseThrow(() -> new BusinessException(ErrorCode.ANNOUNCEMENT_NOT_FOUND));

		// DEPT_ADMIN 只能編輯自己建立的
		DataScopeEnum dataScope = DataScopeEnum.fromString(user.getDataScope());
		if (dataScope != DataScopeEnum.ALL && !entity.getCreatedBy().equals(user.getUserId())) {
			throw new BusinessException(ErrorCode.PERMISSION_DENIED);
		}

		// 樂觀鎖：必須帶 version；若 DB 版本已更新則拒絕，避免覆蓋他人變更
		if (request.getVersion() == null) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR, "缺少 version 欄位");
		}
		if (!request.getVersion().equals(entity.getVersion())) {
			throw new BusinessException(ErrorCode.ANNOUNCEMENT_VERSION_CONFLICT);
		}

		entity.setTitle(request.getTitle());
		String safeContent = htmlSanitizerService.sanitize(request.getContent());
		entity.setContent(safeContent);
		entity.setContentText(htmlSanitizerService.extractText(safeContent));
		entity.setStatus(request.getStatus());
		entity.setCategory(normalizeCategory(request.getCategory()));
		entity.setPinned(request.getPinned() != null ? request.getPinned() : false);
		entity.setPinOrder(resolvePinOrderForSave(entity.getPinned(),
				request.getPinOrder() != null ? request.getPinOrder() : entity.getPinOrder()));
		entity.setRequiresAck(request.getRequiresAck() != null ? request.getRequiresAck() : false);
		entity.setPublishAt(resolvePublishAt(request));
		entity.setExpireAt(request.getExpireAt());
		entity.setUpdatedAt(LocalDateTime.now());

		// DEPT_ADMIN 強制 scope=DEPT + 自己部門
		List<Long> targetDeptIds;
		if (dataScope != DataScopeEnum.ALL) {
			entity.setScope(AnnouncementScope.DEPT.getValue());
			targetDeptIds = List.of(user.getDeptId());
		}
		else {
			entity.setScope(request.getScope());
			targetDeptIds = request.getTargetDeptIds();
			if (AnnouncementScope.DEPT.getValue().equals(entity.getScope())) {
				if (targetDeptIds == null || targetDeptIds.isEmpty()) {
					throw new BusinessException(ErrorCode.VALIDATION_ERROR, "指定部門公告必須選擇至少一個部門");
				}
			}
		}

		try {
			announcementRepository.saveAndFlush(entity);
		}
		catch (org.springframework.dao.OptimisticLockingFailureException ex) {
			// 防止 load → save 之間發生競爭，仍由 Hibernate @Version 把關
			throw new BusinessException(ErrorCode.ANNOUNCEMENT_VERSION_CONFLICT);
		}

		// 重建 junction table
		announcementDeptRepository.deleteByAnnouncementId(entity.getId());
		if (AnnouncementScope.DEPT.getValue().equals(entity.getScope()) && targetDeptIds != null) {
			saveAnnouncementDepts(entity.getId(), targetDeptIds);
		}

		// 重建語言翻譯
		replaceTranslations(entity.getId(), request.getTranslations());

		return toResponse(entity, user.getUserId(), false, DEFAULT_LANG);
	}

	// ── 刪除 ──

	@Transactional
	public void delete(Long id) {
		UserInfo user = SecurityContextUtils.getUserInfo();

		Announcement entity = announcementRepository.findById(id)
			.orElseThrow(() -> new BusinessException(ErrorCode.ANNOUNCEMENT_NOT_FOUND));

		// DEPT_ADMIN 只能刪除自己建立的
		DataScopeEnum dataScope = DataScopeEnum.fromString(user.getDataScope());
		if (dataScope != DataScopeEnum.ALL && !entity.getCreatedBy().equals(user.getUserId())) {
			throw new BusinessException(ErrorCode.PERMISSION_DENIED);
		}

		// CASCADE 會自動刪除 announcement_depts + announcement_reads
		announcementRepository.delete(entity);
	}

	// ── 置頂順序：拖曳排序 ──

	/**
	 * 列出目前所有置頂公告（依 pin_order 排序），給拖曳排序 UI 使用。
	 * <p>
	 * ADMIN：全部；DEPT_ADMIN：自己建立的 + 受眾包含自己部門的。
	 */
	@Transactional(readOnly = true)
	public List<AnnouncementResponse> listPinned() {
		UserInfo user = SecurityContextUtils.getUserInfo();
		DataScopeEnum dataScope = DataScopeEnum.fromString(user.getDataScope());

		List<Announcement> entities;
		if (dataScope == DataScopeEnum.ALL) {
			entities = announcementRepository.findAllPinned();
		}
		else {
			Long deptId = user.getDeptId() != null ? user.getDeptId() : -1L;
			entities = announcementRepository.findPinnedForDeptAdmin(user.getUserId(), deptId);
		}
		return toResponseList(entities, user.getUserId(), true, DEFAULT_LANG);
	}

	/**
	 * 依拖曳結果重新指派 pin_order。
	 * <p>
	 * orderedIds 順序即為新排序；第一個 → pin_order=1，依此類推。
	 * <p>
	 * 權限與資料範圍：
	 * <ul>
	 * <li>所有 id 必須屬於當前租戶（findAllById 自動套用 tenantFilter）。</li>
	 * <li>DEPT_ADMIN 僅能調整自己 createdBy 的置頂；遇到他人公告即 PERMISSION_DENIED。</li>
	 * <li>必須全部為 pinned=true；否則 VALIDATION_ERROR。</li>
	 * </ul>
	 */
	@Transactional
	public void reorderPins(List<Long> orderedIds) {
		if (orderedIds == null || orderedIds.isEmpty()) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR, "orderedIds 不可為空");
		}
		// 去重後 size 應一致；否則代表重複
		long distinctCount = orderedIds.stream().distinct().count();
		if (distinctCount != orderedIds.size()) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR, "orderedIds 不可重複");
		}

		UserInfo user = SecurityContextUtils.getUserInfo();
		DataScopeEnum dataScope = DataScopeEnum.fromString(user.getDataScope());

		List<Announcement> entities = announcementRepository.findAllById(orderedIds);
		if (entities.size() != orderedIds.size()) {
			throw new BusinessException(ErrorCode.ANNOUNCEMENT_NOT_FOUND);
		}
		Map<Long, Announcement> byId = entities.stream().collect(Collectors.toMap(Announcement::getId, e -> e));

		for (Announcement e : entities) {
			if (e.getPinned() == null || !e.getPinned()) {
				throw new BusinessException(ErrorCode.VALIDATION_ERROR, "公告 " + e.getId() + " 並非置頂狀態");
			}
			if (dataScope != DataScopeEnum.ALL && !user.getUserId().equals(e.getCreatedBy())) {
				throw new BusinessException(ErrorCode.PERMISSION_DENIED);
			}
		}

		// 依清單順序由 1 開始重新指派
		int order = 1;
		for (Long id : orderedIds) {
			Announcement e = byId.get(id);
			e.setPinOrder(order++);
		}
		announcementRepository.saveAll(entities);
	}

	/**
	 * 計算寫入用 pin_order：
	 * <ul>
	 * <li>pinned=false → 一律 NULL（避免取消置頂後仍佔順序）。</li>
	 * <li>pinned=true 且傳入有值 → 採用傳入值（drag-sort 場景）。</li>
	 * <li>pinned=true 且傳入 null → 自動指派 max(pin_order)+1，加到尾端。</li>
	 * </ul>
	 */
	private Integer resolvePinOrderForSave(Boolean pinned, Integer requested) {
		if (pinned == null || !pinned)
			return null;
		if (requested != null)
			return requested;
		Integer max = announcementRepository.findMaxPinOrder();
		return (max == null ? 0 : max) + 1;
	}

	// ── private helpers ──

	private void saveAnnouncementDepts(Long announcementId, List<Long> deptIds) {
		List<AnnouncementDept> depts = deptIds.stream()
			.map(deptId -> AnnouncementDept.builder().announcementId(announcementId).deptId(deptId).build())
			.toList();
		announcementDeptRepository.saveAll(depts);
	}

	private LocalDateTime resolvePublishAt(AnnouncementRequest request) {
		return request.getPublishAt() != null ? request.getPublishAt() : LocalDateTime.now();
	}

	/**
	 * 寫入用：request 未帶或為空時 fallback 為 GENERAL。
	 * <p>
	 * 本次設計不扊計任意字串；DTO 層 @Pattern 已拒絕不法值， 這裡只處理省略 / null 的預設補上。
	 */
	private String normalizeCategory(String category) {
		if (category == null || category.isBlank())
			return "GENERAL";
		return category;
	}

	/**
	 * 查詢過濾用：blank / "ALL" 視同 null（不過濾）。
	 */
	private String normalizeCategoryFilter(String category) {
		if (category == null || category.isBlank())
			return null;
		if ("ALL".equalsIgnoreCase(category))
			return null;
		return category;
	}

	private String resolveDisplayName(String userId) {
		return userRepository.findById(userId).map(UserEntity::getDisplayName).orElse(userId);
	}

	private AnnouncementResponse toResponse(Announcement entity, String currentUserId, boolean includeEditable,
			String langCode) {
		List<AnnouncementDept> depts = announcementDeptRepository.findByAnnouncementId(entity.getId());
		List<Long> deptIds = depts.stream().map(AnnouncementDept::getDeptId).toList();
		Set<Long> deptIdSet = depts.stream().map(AnnouncementDept::getDeptId).collect(Collectors.toSet());
		Map<Long, String> nameMap = resolveDeptNameMap(deptIdSet);
		List<String> deptNames = deptIds.stream().map(id -> nameMap.getOrDefault(id, String.valueOf(id))).toList();

		List<AnnouncementTranslation> translations = announcementTranslationRepository
			.findByAnnouncementId(entity.getId());
		List<AnnouncementTranslationDto> allTranslations = synthesizeTranslations(entity, translations);
		ResolvedTranslation resolved = pickTranslation(entity, translations, langCode);

		AnnouncementResponse.AnnouncementResponseBuilder builder = AnnouncementResponse.builder()
			.id(entity.getId())
			.title(resolved.title())
			.content(resolved.content())
			.resolvedLang(resolved.lang())
			.translations(allTranslations)
			.status(entity.getStatus())
			.scope(entity.getScope())
			.category(entity.getCategory())
			.targetDeptIds(deptIds)
			.targetDeptNames(deptNames)
			.pinned(entity.getPinned())
			.requiresAck(entity.getRequiresAck() != null ? entity.getRequiresAck() : false)
			.publishAt(entity.getPublishAt())
			.expireAt(entity.getExpireAt())
			.createdBy(entity.getCreatedBy())
			.createdByName(entity.getCreatedByName())
			.createdAt(entity.getCreatedAt())
			.updatedAt(entity.getUpdatedAt())
			.version(entity.getVersion())
			.attachments(attachmentService.listByAnnouncementIds(List.of(entity.getId()))
				.getOrDefault(entity.getId(), Collections.emptyList()))
			.isRead(false); // default, overridden in list
		builder.pinOrder(entity.getPinOrder());

		if (includeEditable) {
			UserInfo user = SecurityContextUtils.getUserInfo();
			DataScopeEnum dataScope = DataScopeEnum.fromString(user.getDataScope());
			boolean editable = dataScope == DataScopeEnum.ALL || entity.getCreatedBy().equals(currentUserId);
			builder.editable(editable);
		}

		return builder.build();
	}

	private List<AnnouncementResponse> toResponseList(List<Announcement> entities, String currentUserId,
			boolean includeEditable, String langCode) {
		if (entities.isEmpty())
			return Collections.emptyList();

		// 批次載入 junction table
		List<Long> ids = entities.stream().map(Announcement::getId).toList();
		Map<Long, List<AnnouncementDept>> deptMap = announcementDeptRepository.findByAnnouncementIdIn(ids)
			.stream()
			.collect(Collectors.groupingBy(AnnouncementDept::getAnnouncementId));

		// 批次載入已讀狀態
		Set<Long> readIds = getReadAnnouncementIds(ids, currentUserId);

		// 批次載入附件
		Map<Long, List<AnnouncementAttachmentResponse>> attachmentMap = attachmentService.listByAnnouncementIds(ids);

		// 批次載入語言翻譯（供 lang resolution + 管理端編輯表單使用）
		Map<Long, List<AnnouncementTranslation>> translationMap = announcementTranslationRepository
			.findByAnnouncementIdIn(ids)
			.stream()
			.collect(Collectors.groupingBy(AnnouncementTranslation::getAnnouncementId));

		// 收集所有部門 ID 一次查詢名稱
		Set<Long> allDeptIds = deptMap.values()
			.stream()
			.flatMap(List::stream)
			.map(AnnouncementDept::getDeptId)
			.collect(Collectors.toSet());
		Map<Long, String> deptNameMap = resolveDeptNameMap(allDeptIds);

		UserInfo user = SecurityContextUtils.getUserInfo();
		DataScopeEnum dataScope = DataScopeEnum.fromString(user.getDataScope());

		return entities.stream().map(entity -> {
			List<AnnouncementDept> entityDepts = deptMap.getOrDefault(entity.getId(), Collections.emptyList());
			List<Long> deptIds = entityDepts.stream().map(AnnouncementDept::getDeptId).toList();
			List<String> deptNames = deptIds.stream()
				.map(id -> deptNameMap.getOrDefault(id, String.valueOf(id)))
				.toList();

			List<AnnouncementTranslation> entityTranslations = translationMap.getOrDefault(entity.getId(),
					Collections.emptyList());
			List<AnnouncementTranslationDto> allTranslations = synthesizeTranslations(entity, entityTranslations);
			ResolvedTranslation resolved = pickTranslation(entity, entityTranslations, langCode);

			AnnouncementResponse.AnnouncementResponseBuilder builder = AnnouncementResponse.builder()
				.id(entity.getId())
				.title(resolved.title())
				.content(resolved.content())
				.resolvedLang(resolved.lang())
				.translations(allTranslations)
				.status(entity.getStatus())
				.scope(entity.getScope())
				.category(entity.getCategory())
				.targetDeptIds(deptIds)
				.targetDeptNames(deptNames)
				.pinned(entity.getPinned())
				.requiresAck(entity.getRequiresAck() != null ? entity.getRequiresAck() : false)
				.publishAt(entity.getPublishAt())
				.expireAt(entity.getExpireAt())
				.createdBy(entity.getCreatedBy())
				.createdByName(entity.getCreatedByName())
				.createdAt(entity.getCreatedAt())
				.updatedAt(entity.getUpdatedAt())
				.version(entity.getVersion())
				.attachments(attachmentMap.getOrDefault(entity.getId(), Collections.emptyList()))
				.isRead(readIds.contains(entity.getId()));
			builder.pinOrder(entity.getPinOrder());

			if (includeEditable) {
				boolean editable = dataScope == DataScopeEnum.ALL || entity.getCreatedBy().equals(currentUserId);
				builder.editable(editable);
			}

			return builder.build();
		}).toList();
	}

	private Set<Long> getReadAnnouncementIds(List<Long> announcementIds, String userId) {
		return announcementReadRepository.findByAnnouncementIdInAndUserId(announcementIds, userId)
			.stream()
			.map(com.taipei.iot.announcement.entity.AnnouncementRead::getAnnouncementId)
			.collect(Collectors.toSet());
	}

	private Map<Long, String> resolveDeptNameMap(Set<Long> deptIds) {
		if (deptIds == null || deptIds.isEmpty())
			return Collections.emptyMap();
		return deptInfoRepository.findByDeptIdIn(deptIds)
			.stream()
			.collect(Collectors.toMap(DeptInfoEntity::getDeptId, DeptInfoEntity::getDeptName));
	}

	// ── 多語系 helpers ──

	/**
	 * 將 client 傳入的 lang code 限制在白名單內；空值 / 非法值 fallback 為 DEFAULT_LANG。
	 * <p>
	 * 底層委派至 {@link LangNormalizer#normalize(String, Set, String)}（common v2 F-17）。
	 */
	private String normalizeLang(String langCode) {
		return LangNormalizer.normalize(langCode, SUPPORTED_LANGS, DEFAULT_LANG);
	}

	/**
	 * 解析請求語言後的 title/content。
	 * <p>
	 * fallback 順序：requested lang → DEFAULT_LANG 子表 → 主表（zh-TW canonical）。
	 */
	private ResolvedTranslation pickTranslation(Announcement entity, List<AnnouncementTranslation> translations,
			String langCode) {
		String safe = normalizeLang(langCode);
		if (!DEFAULT_LANG.equals(safe)) {
			for (AnnouncementTranslation t : translations) {
				if (safe.equals(t.getLangCode())) {
					return new ResolvedTranslation(t.getTitle(), t.getContent(), safe);
				}
			}
		}
		// fallback：主表 = zh-TW canonical
		return new ResolvedTranslation(entity.getTitle(), entity.getContent(), DEFAULT_LANG);
	}

	/**
	 * 給管理端編輯表單：將 {@link Announcement#getTitle()}/{@link Announcement#getContent()} 合成為
	 * zh-TW translation； 再串接子表其他語言；最後依 SUPPORTED_LANGS 順序輸出。
	 */
	private List<AnnouncementTranslationDto> synthesizeTranslations(Announcement entity,
			List<AnnouncementTranslation> translations) {
		Map<String, AnnouncementTranslationDto> byLang = new java.util.LinkedHashMap<>();
		byLang.put(DEFAULT_LANG,
				AnnouncementTranslationDto.builder()
					.langCode(DEFAULT_LANG)
					.title(entity.getTitle())
					.content(entity.getContent())
					.build());
		for (AnnouncementTranslation t : translations) {
			if (DEFAULT_LANG.equals(t.getLangCode()))
				continue; // 已由主表代表
			byLang.put(t.getLangCode(),
					AnnouncementTranslationDto.builder()
						.langCode(t.getLangCode())
						.title(t.getTitle())
						.content(t.getContent())
						.build());
		}
		return new ArrayList<>(byLang.values());
	}

	/**
	 * 重建公告的翻譯子表。
	 * <ul>
	 * <li>清除既有翻譯後重新寫入（簡單一致，數量少）。</li>
	 * <li>會跳過 lang_code=DEFAULT_LANG（zh-TW）：以主表為唯一來源，避免雙寫不一致。</li>
	 * <li>每筆 content 都會通過 {@link HtmlSanitizerService#sanitize(String)}。</li>
	 * <li>同一 lang_code 重複時以後者覆蓋前者。</li>
	 * </ul>
	 */
	private void replaceTranslations(Long announcementId, List<AnnouncementTranslationDto> requested) {
		announcementTranslationRepository.deleteByAnnouncementId(announcementId);
		if (requested == null || requested.isEmpty())
			return;

		Map<String, AnnouncementTranslationDto> dedup = new java.util.LinkedHashMap<>();
		for (AnnouncementTranslationDto dto : requested) {
			if (dto == null || dto.getLangCode() == null)
				continue;
			String lang = dto.getLangCode();
			if (DEFAULT_LANG.equals(lang))
				continue; // 主表代表 zh-TW，子表不重複
			if (!SUPPORTED_LANGS.contains(lang)) {
				throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Unsupported lang_code: " + lang);
			}
			dedup.put(lang, dto);
		}
		if (dedup.isEmpty())
			return;

		LocalDateTime now = LocalDateTime.now();
		List<AnnouncementTranslation> rows = new ArrayList<>(dedup.size());
		for (AnnouncementTranslationDto dto : dedup.values()) {
			String safeContent = htmlSanitizerService.sanitize(dto.getContent());
			String contentText = htmlSanitizerService.extractText(safeContent);
			rows.add(AnnouncementTranslation.builder()
				.announcementId(announcementId)
				.langCode(dto.getLangCode())
				.title(dto.getTitle())
				.content(safeContent)
				.contentText(contentText)
				.createdAt(now)
				.updatedAt(now)
				.build());
		}
		announcementTranslationRepository.saveAll(rows);
	}

	/** 內部 record：lang resolution 結果。 */
	private record ResolvedTranslation(String title, String content, String lang) {
	}

}
