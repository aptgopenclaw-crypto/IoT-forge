package com.taipei.iot.announcement.service;

import com.taipei.iot.announcement.dto.AnnouncementReadStatsResponse;
import com.taipei.iot.announcement.dto.AnnouncementUnreadUserResponse;
import com.taipei.iot.announcement.dto.UnreadCountResponse;
import com.taipei.iot.announcement.entity.Announcement;
import com.taipei.iot.announcement.entity.AnnouncementDept;
import com.taipei.iot.announcement.entity.AnnouncementScope;
import com.taipei.iot.announcement.repository.AnnouncementDeptRepository;
import com.taipei.iot.announcement.repository.AnnouncementReadRepository;
import com.taipei.iot.announcement.repository.AnnouncementRepository;
import com.taipei.iot.announcement.repository.AnnouncementStatsRepository;
import com.taipei.iot.common.dto.PageResponse;
import com.taipei.iot.common.dto.UserInfo;
import com.taipei.iot.common.enums.ErrorCode;
import com.taipei.iot.common.exception.BusinessException;
import com.taipei.iot.common.util.PageConversionHelper;
import com.taipei.iot.common.util.SecurityContextUtils;
import com.taipei.iot.dept.enums.DataScopeEnum;
import com.taipei.iot.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AnnouncementReadService {

	private final AnnouncementRepository announcementRepository;

	private final AnnouncementReadRepository announcementReadRepository;

	private final AnnouncementDeptRepository announcementDeptRepository;

	private final AnnouncementStatsRepository announcementStatsRepository;

	@Transactional(readOnly = true)
	public UnreadCountResponse getUnreadCount() {
		UserInfo user = SecurityContextUtils.getUserInfo();
		Long deptId = user.getDeptId() != null ? user.getDeptId() : -1L;
		long count = announcementRepository.countUnread(deptId, user.getUserId(), LocalDateTime.now());
		return UnreadCountResponse.builder().count((int) count).build();
	}

	@Transactional
	public void markAsRead(Long announcementId) {
		String userId = SecurityContextUtils.getCurrentUserId();
		// 透過 tenant-filtered repository 驗證該公告屬於當前租戶，
		// 防止跨租戶寫入 announcement_reads（native SQL INSERT 不受 @Filter 保護）
		if (!announcementRepository.existsById(announcementId)) {
			throw new BusinessException(ErrorCode.ANNOUNCEMENT_NOT_FOUND);
		}
		announcementReadRepository.markAsRead(announcementId, userId);
	}

	@Transactional
	public void markAllAsRead() {
		UserInfo user = SecurityContextUtils.getUserInfo();
		String tenantId = TenantContext.getCurrentTenantId();
		Long deptId = user.getDeptId() != null ? user.getDeptId() : -1L;
		announcementReadRepository.markAllAsRead(user.getUserId(), tenantId, deptId);
	}

	// ── 管理端：已讀統計與未讀名單 ──

	/**
	 * 取得指定公告之已讀統計（已讀人數 / 受眾總數 / 已讀比例）。
	 * <p>
	 * 權限：呼叫者需具 ANNOUNCEMENT_MANAGE（由 controller 控制）； DEPT_ADMIN 僅能查看自建公告之統計，避免越權看到其他部門名單。
	 */
	@Transactional(readOnly = true)
	public AnnouncementReadStatsResponse getReadStats(Long announcementId) {
		Announcement entity = loadAndCheckManage(announcementId);
		String tenantId = TenantContext.getCurrentTenantId();

		boolean isAll = AnnouncementScope.ALL.getValue().equals(entity.getScope());
		List<Long> deptIds = isAll ? Collections.emptyList()
				: announcementDeptRepository.findByAnnouncementId(announcementId)
					.stream()
					.map(AnnouncementDept::getDeptId)
					.toList();

		long totalAudience;
		long readCount;
		if (isAll) {
			totalAudience = announcementStatsRepository.countAudienceAll(tenantId);
			readCount = announcementStatsRepository.countReadAll(announcementId, tenantId);
		}
		else if (deptIds.isEmpty()) {
			// DEPT 公告但 junction table 為空（資料異常或剛建立未指定部門）→ 0 受眾
			totalAudience = 0;
			readCount = 0;
		}
		else {
			totalAudience = announcementStatsRepository.countAudienceDept(tenantId, deptIds);
			readCount = announcementStatsRepository.countReadDept(announcementId, tenantId, deptIds);
		}

		BigDecimal ratio = totalAudience > 0
				? BigDecimal.valueOf(readCount).divide(BigDecimal.valueOf(totalAudience), 4, RoundingMode.HALF_UP)
				: BigDecimal.ZERO;

		return AnnouncementReadStatsResponse.builder()
			.announcementId(announcementId)
			.requiresAck(Boolean.TRUE.equals(entity.getRequiresAck()))
			.totalAudience(totalAudience)
			.readCount(readCount)
			.unreadCount(Math.max(0, totalAudience - readCount))
			.readRatio(ratio)
			.build();
	}

	/**
	 * 取得指定公告之未讀使用者清單（分頁，支援關鍵字 = 名稱 / email 模糊比對）。
	 */
	@Transactional(readOnly = true)
	public PageResponse<AnnouncementUnreadUserResponse> getUnreadUsers(Long announcementId, String keyword, int page,
			int size) {
		Announcement entity = loadAndCheckManage(announcementId);
		String tenantId = TenantContext.getCurrentTenantId();

		String safeKeyword = (keyword == null || keyword.isBlank()) ? null : keyword.trim();
		Pageable pageable = PageRequest.of(page, size);

		Page<Object[]> rows;
		if (AnnouncementScope.ALL.getValue().equals(entity.getScope())) {
			rows = announcementStatsRepository.findUnreadUsersAll(announcementId, tenantId, safeKeyword, pageable);
		}
		else {
			List<Long> deptIds = announcementDeptRepository.findByAnnouncementId(announcementId)
				.stream()
				.map(AnnouncementDept::getDeptId)
				.toList();
			if (deptIds.isEmpty()) {
				return PageConversionHelper.empty();
			}
			rows = announcementStatsRepository.findUnreadUsersDept(announcementId, tenantId, deptIds, safeKeyword,
					pageable);
		}

		List<AnnouncementUnreadUserResponse> content = rows.getContent()
			.stream()
			.map(r -> AnnouncementUnreadUserResponse.builder()
				.userId((String) r[0])
				.displayName((String) r[1])
				.email((String) r[2])
				.deptId(r[3] != null ? ((Number) r[3]).longValue() : null)
				.deptName((String) r[4])
				.build())
			.toList();

		return PageConversionHelper.from(content, rows);
	}

	/**
	 * 載入公告並檢查當前使用者是否有權查看統計： ADMIN（DataScope=ALL）可看全部；DEPT_ADMIN 僅能看自建公告。
	 */
	private Announcement loadAndCheckManage(Long announcementId) {
		Announcement entity = announcementRepository.findById(announcementId)
			.orElseThrow(() -> new BusinessException(ErrorCode.ANNOUNCEMENT_NOT_FOUND));
		UserInfo user = SecurityContextUtils.getUserInfo();
		DataScopeEnum scope = DataScopeEnum.fromString(user.getDataScope());
		if (scope != DataScopeEnum.ALL && !user.getUserId().equals(entity.getCreatedBy())) {
			throw new BusinessException(ErrorCode.PERMISSION_DENIED);
		}
		return entity;
	}

}
