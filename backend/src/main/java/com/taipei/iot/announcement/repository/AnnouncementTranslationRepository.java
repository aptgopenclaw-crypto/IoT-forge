package com.taipei.iot.announcement.repository;

import com.taipei.iot.announcement.entity.AnnouncementTranslation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface AnnouncementTranslationRepository extends JpaRepository<AnnouncementTranslation, Long> {

	List<AnnouncementTranslation> findByAnnouncementId(Long announcementId);

	List<AnnouncementTranslation> findByAnnouncementIdIn(Collection<Long> announcementIds);

	@Modifying
	@Query("DELETE FROM AnnouncementTranslation t WHERE t.announcementId = :announcementId")
	void deleteByAnnouncementId(@Param("announcementId") Long announcementId);

}
