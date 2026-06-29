package com.taipei.iot.announcement.repository;

import com.taipei.iot.announcement.entity.Announcement;
import com.taipei.iot.tenant.TenantScopedRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface AnnouncementRepository extends JpaRepository<Announcement, Long>, TenantScopedRepository {

	/**
	 * 前台查詢：已發佈 + 未過期 + 受眾範圍（ALL 或使用者部門在 announcement_depts 中）
	 * <p>
	 * 可另外限定 category；:category 為 null 表示不過濾。
	 */
	@Query("""
			SELECT a FROM Announcement a
			WHERE a.status = 'PUBLISHED'
			  AND a.publishAt <= :now
			  AND (a.expireAt IS NULL OR a.expireAt > :now)
			  AND (:category IS NULL OR a.category = :category)
			  AND (a.scope = 'ALL'
			       OR EXISTS (SELECT 1 FROM AnnouncementDept ad
			                  WHERE ad.announcementId = a.id AND ad.deptId = :userDeptId))
			""")
	Page<Announcement> findVisibleAnnouncements(@Param("userDeptId") Long userDeptId,
			@Param("category") String category, @Param("now") LocalDateTime now, Pageable pageable);

	/**
	 * 管理頁面：ADMIN 看全部
	 * <p>
	 * statusFilter 支援：ALL / DRAFT / SCHEDULED / PUBLISHED / EXPIRED。 其中 SCHEDULED
	 * 為計算狀態：status=PUBLISHED 但 publishAt > now。
	 */
	@Query("""
			SELECT a FROM Announcement a
			WHERE (:statusFilter = 'ALL' OR
			       (:statusFilter = 'DRAFT' AND a.status = 'DRAFT') OR
			       (:statusFilter = 'SCHEDULED' AND a.status = 'PUBLISHED' AND a.publishAt > :now) OR
			       (:statusFilter = 'PUBLISHED' AND a.status = 'PUBLISHED'
			           AND a.publishAt <= :now
			           AND (a.expireAt IS NULL OR a.expireAt > :now)) OR
			       (:statusFilter = 'EXPIRED' AND a.status = 'PUBLISHED' AND a.expireAt IS NOT NULL AND a.expireAt < :now))
			  AND (:category IS NULL OR a.category = :category)
			  AND (:keyword IS NULL
			       OR a.title LIKE :keyword ESCAPE '\\'
			       OR a.contentText LIKE :keyword ESCAPE '\\')
			""")
	Page<Announcement> findAdminAnnouncements(@Param("statusFilter") String statusFilter,
			@Param("category") String category, @Param("keyword") String keyword, @Param("now") LocalDateTime now,
			Pageable pageable);

	/**
	 * 管理頁面：DEPT_ADMIN 看自己建立的 + 受眾包含自己部門的
	 */
	@Query("""
			SELECT a FROM Announcement a
			WHERE (a.createdBy = :userId
			       OR (a.scope = 'DEPT' AND EXISTS (
			           SELECT 1 FROM AnnouncementDept ad
			           WHERE ad.announcementId = a.id AND ad.deptId = :userDeptId)))
			  AND (:statusFilter = 'ALL' OR
			       (:statusFilter = 'DRAFT' AND a.status = 'DRAFT') OR
			       (:statusFilter = 'SCHEDULED' AND a.status = 'PUBLISHED' AND a.publishAt > :now) OR
			       (:statusFilter = 'PUBLISHED' AND a.status = 'PUBLISHED'
			           AND a.publishAt <= :now
			           AND (a.expireAt IS NULL OR a.expireAt > :now)) OR
			       (:statusFilter = 'EXPIRED' AND a.status = 'PUBLISHED'
			           AND a.expireAt IS NOT NULL AND a.expireAt < :now))
			  AND (:category IS NULL OR a.category = :category)
			  AND (:keyword IS NULL
			       OR a.title LIKE :keyword ESCAPE '\\'
			       OR a.contentText LIKE :keyword ESCAPE '\\')
			""")
	Page<Announcement> findDeptAdminAnnouncements(@Param("userId") String userId, @Param("userDeptId") Long userDeptId,
			@Param("statusFilter") String statusFilter, @Param("category") String category,
			@Param("keyword") String keyword, @Param("now") LocalDateTime now, Pageable pageable);

	/**
	 * 未讀數量：已發佈 + 未過期 + 受眾範圍 + 未讀
	 */
	@Query("""
			SELECT COUNT(a) FROM Announcement a
			WHERE a.status = 'PUBLISHED'
			  AND a.publishAt <= :now
			  AND (a.expireAt IS NULL OR a.expireAt > :now)
			  AND (a.scope = 'ALL'
			       OR EXISTS (SELECT 1 FROM AnnouncementDept ad
			                  WHERE ad.announcementId = a.id AND ad.deptId = :userDeptId))
			  AND NOT EXISTS (SELECT 1 FROM AnnouncementRead r
			                  WHERE r.announcementId = a.id AND r.userId = :userId)
			""")
	long countUnread(@Param("userDeptId") Long userDeptId, @Param("userId") String userId,
			@Param("now") LocalDateTime now);

	/**
	 * 取得目前最大 pin_order（用於新增置頂時自動分配下一個順序）。
	 * <p>
	 * 受 tenantFilter 自動套用：僅統計當前租戶。
	 */
	@Query("SELECT COALESCE(MAX(a.pinOrder), 0) FROM Announcement a WHERE a.pinned = true")
	Integer findMaxPinOrder();

	/**
	 * 列出目前所有置頂公告（依 pin_order 排序），給拖曳排序 UI 使用。
	 * <p>
	 * DEPT_ADMIN 看自己建立的 + 受眾包含自己部門的。
	 */
	@Query("""
			SELECT a FROM Announcement a
			WHERE a.pinned = true
			  AND (a.createdBy = :userId
			       OR (a.scope = 'DEPT' AND EXISTS (
			           SELECT 1 FROM AnnouncementDept ad
			           WHERE ad.announcementId = a.id AND ad.deptId = :userDeptId)))
			ORDER BY a.pinOrder ASC NULLS LAST, a.publishAt DESC
			""")
	java.util.List<Announcement> findPinnedForDeptAdmin(@Param("userId") String userId,
			@Param("userDeptId") Long userDeptId);

	/**
	 * 列出目前所有置頂公告（依 pin_order 排序），ADMIN 視角。
	 */
	@Query("""
			SELECT a FROM Announcement a
			WHERE a.pinned = true
			ORDER BY a.pinOrder ASC NULLS LAST, a.publishAt DESC
			""")
	java.util.List<Announcement> findAllPinned();

}
