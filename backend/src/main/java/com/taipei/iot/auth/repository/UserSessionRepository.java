package com.taipei.iot.auth.repository;

import com.taipei.iot.auth.entity.UserSessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UserSessionRepository extends JpaRepository<UserSessionEntity, String> {

	/**
	 * 列出指定使用者目前仍有效（未撤銷且未過期）的 session，按 last_seen_at DESC。
	 */
	@Query("SELECT s FROM UserSessionEntity s "
			+ "WHERE s.userId = :userId AND s.revoked = false AND s.expiresAt > :now " + "ORDER BY s.lastSeenAt DESC")
	List<UserSessionEntity> findActiveByUserId(@Param("userId") String userId, @Param("now") LocalDateTime now);

	Optional<UserSessionEntity> findBySessionIdAndUserId(String sessionId, String userId);

	/**
	 * 原子性標記 session 為 revoked。回傳被影響列數，0 表示找不到或已是 revoked。
	 *
	 * <p>
	 * Uses its own transaction ({@code @Transactional}) so callers without an active
	 * transaction (e.g. {@code AuthServiceImpl#logout}) do not trigger Hibernate's
	 * "Executing an update/delete query without transaction" guard.
	 */
	@Transactional
	@Modifying
	@Query("UPDATE UserSessionEntity s SET s.revoked = true, s.revokedAt = :now "
			+ "WHERE s.sessionId = :sessionId AND s.revoked = false")
	int revokeById(@Param("sessionId") String sessionId, @Param("now") LocalDateTime now);

	/**
	 * 更新 last_seen_at / ip / user-agent；refresh rotation 時舊 row 仍會走
	 * {@link #revokeById}，本方法保留給未來「同 row 更新」策略使用。
	 */
	@Modifying
	@Query("UPDATE UserSessionEntity s SET s.lastSeenAt = :now, " + "s.ipAddress = :ip, s.userAgent = :ua "
			+ "WHERE s.sessionId = :sessionId")
	int touch(@Param("sessionId") String sessionId, @Param("now") LocalDateTime now, @Param("ip") String ip,
			@Param("ua") String ua);

	/**
	 * [User N-4] 批次撤銷使用者所有活動 session（排除指定 sessionId）。
	 */
	@Modifying
	@Query("UPDATE UserSessionEntity s SET s.revoked = true, s.revokedAt = :now "
			+ "WHERE s.userId = :userId AND s.revoked = false AND s.expiresAt > :now "
			+ "AND s.sessionId != :excludeSessionId")
	int revokeAllByUserIdExcept(@Param("userId") String userId, @Param("excludeSessionId") String excludeSessionId,
			@Param("now") LocalDateTime now);

	/**
	 * [User N-4] 批次撤銷使用者所有活動 session（不排除任何）。
	 */
	@Modifying
	@Query("UPDATE UserSessionEntity s SET s.revoked = true, s.revokedAt = :now "
			+ "WHERE s.userId = :userId AND s.revoked = false AND s.expiresAt > :now")
	int revokeAllByUserId(@Param("userId") String userId, @Param("now") LocalDateTime now);

}
