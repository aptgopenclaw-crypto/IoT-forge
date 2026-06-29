package com.taipei.iot.announcement.repository;

import com.taipei.iot.announcement.entity.AnnouncementDept;
import com.taipei.iot.announcement.entity.AnnouncementDeptId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AnnouncementDeptRepository extends JpaRepository<AnnouncementDept, AnnouncementDeptId> {

	List<AnnouncementDept> findByAnnouncementId(Long announcementId);

	void deleteByAnnouncementId(Long announcementId);

	List<AnnouncementDept> findByAnnouncementIdIn(List<Long> announcementIds);

	boolean existsByAnnouncementIdAndDeptId(Long announcementId, Long deptId);

}
