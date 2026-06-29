package com.taipei.iot.announcement.repository;

import com.taipei.iot.announcement.entity.AnnouncementRead;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AnnouncementReadRepository extends JpaRepository<AnnouncementRead, Long> {

	Optional<AnnouncementRead> findByAnnouncementIdAndUserId(Long announcementId, String userId);

	List<AnnouncementRead> findByAnnouncementIdInAndUserId(List<Long> announcementIds, String userId);

	/**
	 * ON CONFLICT DO NOTHING upsert — 避免重複 INSERT 例外
	 */
	@Modifying
	@Query(value = """
			INSERT INTO announcement_reads (announcement_id, user_id, read_at)
			VALUES (:announcementId, :userId, now())
			ON CONFLICT (announcement_id, user_id) DO NOTHING
			""", nativeQuery = true)
	void markAsRead(@Param("announcementId") Long announcementId, @Param("userId") String userId);

	/**
	 * 全部標為已讀：將使用者所有可見但未讀的公告一次 INSERT
	 */
	@Modifying
	@Query(value = """
			INSERT INTO announcement_reads (announcement_id, user_id, read_at)
			SELECT a.id, :userId, now()
			FROM announcements a
			WHERE a.tenant_id = :tenantId
			  AND a.status = 'PUBLISHED'
			  AND a.publish_at <= now()
			  AND (a.expire_at IS NULL OR a.expire_at > now())
			  AND (a.scope = 'ALL'
			       OR EXISTS (SELECT 1 FROM announcement_depts ad
			                  WHERE ad.announcement_id = a.id AND ad.dept_id = :userDeptId))
			  AND NOT EXISTS (SELECT 1 FROM announcement_reads r
			                  WHERE r.announcement_id = a.id AND r.user_id = :userId)
			ON CONFLICT (announcement_id, user_id) DO NOTHING
			""", nativeQuery = true)
	void markAllAsRead(@Param("userId") String userId, @Param("tenantId") String tenantId,
			@Param("userDeptId") Long userDeptId);

}
