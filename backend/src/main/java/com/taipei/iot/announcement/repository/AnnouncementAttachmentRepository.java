package com.taipei.iot.announcement.repository;

import com.taipei.iot.announcement.entity.AnnouncementAttachment;
import com.taipei.iot.tenant.TenantScopedRepository;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AnnouncementAttachmentRepository
		extends JpaRepository<AnnouncementAttachment, Long>, TenantScopedRepository {

	List<AnnouncementAttachment> findByAnnouncementIdOrderByIdAsc(Long announcementId);

	List<AnnouncementAttachment> findByAnnouncementIdInOrderByIdAsc(List<Long> announcementIds);

}
