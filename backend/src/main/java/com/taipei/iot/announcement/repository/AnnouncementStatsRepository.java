package com.taipei.iot.announcement.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.Collection;

/**
 * 公告已讀統計專用 repository（native SQL）。
 * <p>
 * 跨 announcement_reads / user_tenant_mapping / users / announcement_depts 數個表， 用 JPA
 * derived query 難以高效表達，故集中使用 native query。
 * <p>
 * 租戶過濾以參數帶入而非依賴 @Filter（native query 不受 Hibernate filter 影響）。
 */
public interface AnnouncementStatsRepository
		extends Repository<com.taipei.iot.announcement.entity.AnnouncementRead, Long> {

	/**
	 * 計算 scope=ALL 公告之目標受眾總人數（租戶內啟用且未軟刪的使用者）。
	 */
	@Query(value = """
			SELECT count(*) FROM user_tenant_mapping m
			JOIN users u ON u.user_id = m.user_id
			WHERE m.tenant_id = :tenantId AND m.enabled = true AND u.deleted = false
			""", nativeQuery = true)
	long countAudienceAll(@Param("tenantId") String tenantId);

	/**
	 * 計算 scope=DEPT 公告之目標受眾總人數（指定部門內啟用且未軟刪的使用者）。
	 */
	@Query(value = """
			SELECT count(*) FROM user_tenant_mapping m
			JOIN users u ON u.user_id = m.user_id
			WHERE m.tenant_id = :tenantId AND m.enabled = true AND u.deleted = false
			  AND m.dept_id IN (:deptIds)
			""", nativeQuery = true)
	long countAudienceDept(@Param("tenantId") String tenantId, @Param("deptIds") Collection<Long> deptIds);

	/**
	 * scope=ALL 公告之已讀人數（限受眾範圍內 = 仍在租戶且啟用）。
	 * <p>
	 * 離職或被停用的使用者不計入，避免比例失真。
	 */
	@Query(value = """
			SELECT count(*) FROM announcement_reads r
			JOIN user_tenant_mapping m ON m.user_id = r.user_id AND m.tenant_id = :tenantId
			JOIN users u ON u.user_id = m.user_id
			WHERE r.announcement_id = :announcementId
			  AND m.enabled = true AND u.deleted = false
			""", nativeQuery = true)
	long countReadAll(@Param("announcementId") Long announcementId, @Param("tenantId") String tenantId);

	/**
	 * scope=DEPT 公告之已讀人數（限指定部門內仍啟用之使用者）。
	 */
	@Query(value = """
			SELECT count(*) FROM announcement_reads r
			JOIN user_tenant_mapping m ON m.user_id = r.user_id AND m.tenant_id = :tenantId
			JOIN users u ON u.user_id = m.user_id
			WHERE r.announcement_id = :announcementId
			  AND m.enabled = true AND u.deleted = false
			  AND m.dept_id IN (:deptIds)
			""", nativeQuery = true)
	long countReadDept(@Param("announcementId") Long announcementId, @Param("tenantId") String tenantId,
			@Param("deptIds") Collection<Long> deptIds);

	/**
	 * scope=ALL 未讀使用者清單（分頁）。
	 * <p>
	 * 欄位順序：user_id, display_name, email, dept_id, dept_name。 採 LEFT JOIN dept_info
	 * 容許未綁定部門之使用者仍可列出。
	 */
	@Query(value = """
			SELECT u.user_id, u.display_name, u.email, m.dept_id, d.dept_name
			FROM user_tenant_mapping m
			JOIN users u ON u.user_id = m.user_id
			LEFT JOIN dept_info d ON d.dept_id = m.dept_id
			WHERE m.tenant_id = :tenantId AND m.enabled = true AND u.deleted = false
			  AND NOT EXISTS (
			      SELECT 1 FROM announcement_reads r
			      WHERE r.announcement_id = :announcementId AND r.user_id = m.user_id
			  )
			  AND (CAST(:keyword AS text) IS NULL
			       OR LOWER(u.display_name) LIKE LOWER(CONCAT('%', CAST(:keyword AS text), '%'))
			       OR LOWER(u.email) LIKE LOWER(CONCAT('%', CAST(:keyword AS text), '%')))
			ORDER BY u.display_name, u.user_id
			""", countQuery = """
			SELECT count(*) FROM user_tenant_mapping m
			JOIN users u ON u.user_id = m.user_id
			WHERE m.tenant_id = :tenantId AND m.enabled = true AND u.deleted = false
			  AND NOT EXISTS (
			      SELECT 1 FROM announcement_reads r
			      WHERE r.announcement_id = :announcementId AND r.user_id = m.user_id
			  )
			  AND (CAST(:keyword AS text) IS NULL
			       OR LOWER(u.display_name) LIKE LOWER(CONCAT('%', CAST(:keyword AS text), '%'))
			       OR LOWER(u.email) LIKE LOWER(CONCAT('%', CAST(:keyword AS text), '%')))
			""", nativeQuery = true)
	Page<Object[]> findUnreadUsersAll(@Param("announcementId") Long announcementId, @Param("tenantId") String tenantId,
			@Param("keyword") String keyword, Pageable pageable);

	/**
	 * scope=DEPT 未讀使用者清單（分頁）。
	 */
	@Query(value = """
			SELECT u.user_id, u.display_name, u.email, m.dept_id, d.dept_name
			FROM user_tenant_mapping m
			JOIN users u ON u.user_id = m.user_id
			LEFT JOIN dept_info d ON d.dept_id = m.dept_id
			WHERE m.tenant_id = :tenantId AND m.enabled = true AND u.deleted = false
			  AND m.dept_id IN (:deptIds)
			  AND NOT EXISTS (
			      SELECT 1 FROM announcement_reads r
			      WHERE r.announcement_id = :announcementId AND r.user_id = m.user_id
			  )
			  AND (CAST(:keyword AS text) IS NULL
			       OR LOWER(u.display_name) LIKE LOWER(CONCAT('%', CAST(:keyword AS text), '%'))
			       OR LOWER(u.email) LIKE LOWER(CONCAT('%', CAST(:keyword AS text), '%')))
			ORDER BY u.display_name, u.user_id
			""", countQuery = """
			SELECT count(*) FROM user_tenant_mapping m
			JOIN users u ON u.user_id = m.user_id
			WHERE m.tenant_id = :tenantId AND m.enabled = true AND u.deleted = false
			  AND m.dept_id IN (:deptIds)
			  AND NOT EXISTS (
			      SELECT 1 FROM announcement_reads r
			      WHERE r.announcement_id = :announcementId AND r.user_id = m.user_id
			  )
			  AND (CAST(:keyword AS text) IS NULL
			       OR LOWER(u.display_name) LIKE LOWER(CONCAT('%', CAST(:keyword AS text), '%'))
			       OR LOWER(u.email) LIKE LOWER(CONCAT('%', CAST(:keyword AS text), '%')))
			""", nativeQuery = true)
	Page<Object[]> findUnreadUsersDept(@Param("announcementId") Long announcementId, @Param("tenantId") String tenantId,
			@Param("deptIds") Collection<Long> deptIds, @Param("keyword") String keyword, Pageable pageable);

}
