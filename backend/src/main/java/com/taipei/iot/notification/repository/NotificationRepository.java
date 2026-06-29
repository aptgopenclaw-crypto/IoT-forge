package com.taipei.iot.notification.repository;

import com.taipei.iot.notification.entity.NotificationEntity;
import com.taipei.iot.tenant.TenantScopedRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface NotificationRepository extends JpaRepository<NotificationEntity, Long>, TenantScopedRepository {

	Page<NotificationEntity> findByUserIdAndArchivedAtIsNullOrderByCreatedAtDesc(String userId, Pageable pageable);

	@Query("SELECT COUNT(n) FROM NotificationEntity n WHERE n.userId = :userId AND n.read = false AND n.archivedAt IS NULL")
	long countUnreadByUserId(@Param("userId") String userId);

	@Modifying
	@Query("UPDATE NotificationEntity n SET n.read = true, n.readAt = CURRENT_TIMESTAMP, n.updatedAt = CURRENT_TIMESTAMP WHERE n.userId = :userId AND n.read = false AND n.archivedAt IS NULL")
	int markAllReadByUserId(@Param("userId") String userId);

	Page<NotificationEntity> findByUserIdAndTypeAndArchivedAtIsNullOrderByCreatedAtDesc(String userId,
			com.taipei.iot.notification.enums.NotificationType type, Pageable pageable);

	@Modifying
	@Query("UPDATE NotificationEntity n SET n.archivedAt = :now WHERE n.tenantId = :tenantId AND n.read = true AND n.createdAt < :cutoff AND n.archivedAt IS NULL")
	int archiveOldReadNotifications(@Param("tenantId") String tenantId, @Param("cutoff") LocalDateTime cutoff,
			@Param("now") LocalDateTime now);

	@Modifying
	@Query("UPDATE NotificationEntity n SET n.read = true, n.readAt = CURRENT_TIMESTAMP, n.updatedAt = CURRENT_TIMESTAMP "
			+ "WHERE n.id = :id AND n.userId = :userId AND n.read = false")
	int markReadAtomic(@Param("id") Long id, @Param("userId") String userId);

}
