package com.taipei.iot.platform.announcement.repository;

import com.taipei.iot.platform.announcement.entity.PlatformAnnouncement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface PlatformAnnouncementRepository extends JpaRepository<PlatformAnnouncement, Long> {

	/**
	 * 管理端列表：依狀態 / 分類 / 關鍵字篩選。
	 */
	@Query("""
			SELECT a FROM PlatformAnnouncement a
			WHERE (:statusFilter IS NULL OR :statusFilter = 'ALL' OR
			       CASE WHEN :statusFilter = 'EXPIRED' THEN (a.status = 'PUBLISHED' AND a.expireAt IS NOT NULL AND a.expireAt < :now)
			            ELSE a.status = :statusFilter END)
			  AND (:category IS NULL OR a.category = :category)
			  AND (:keyword IS NULL OR LOWER(a.title) LIKE LOWER(CONCAT('%', CAST(:keyword AS text), '%'))
			       OR LOWER(a.contentText) LIKE LOWER(CONCAT('%', CAST(:keyword AS text), '%')))
			ORDER BY a.createdAt DESC
			""")
	Page<PlatformAnnouncement> findAdminList(@Param("statusFilter") String statusFilter,
			@Param("category") String category, @Param("keyword") String keyword, @Param("now") LocalDateTime now,
			Pageable pageable);

	/**
	 * 前台查詢：已發佈且未過期的平台公告（供租戶端公告欄使用）。
	 */
	@Query("""
			SELECT a FROM PlatformAnnouncement a
			WHERE a.status = 'PUBLISHED'
			  AND (a.publishAt IS NULL OR a.publishAt <= :now)
			  AND (a.expireAt IS NULL OR a.expireAt > :now)
			  AND (:category IS NULL OR a.category = :category)
			ORDER BY a.createdAt DESC
			""")
	Page<PlatformAnnouncement> findPublished(@Param("category") String category, @Param("now") LocalDateTime now,
			Pageable pageable);

}
