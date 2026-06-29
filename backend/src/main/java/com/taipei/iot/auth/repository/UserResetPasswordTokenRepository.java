package com.taipei.iot.auth.repository;

import com.taipei.iot.auth.entity.UserResetPasswordTokenEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface UserResetPasswordTokenRepository extends JpaRepository<UserResetPasswordTokenEntity, String> {

	Optional<UserResetPasswordTokenEntity> findByTokenHash(String tokenHash);

	/**
	 * 原子性「消耗」一個尚未使用且未過期的 reset token。
	 *
	 * <p>
	 * 取代「先 SELECT 驗證、最後才 UPDATE used=true」的非原子性模式， 避免兩個 concurrent request 都讀到 used=false
	 * 後都成功改密碼的 race condition。
	 * </p>
	 * @return 被影響的列數；1 代表本 request 成功 claim token，0 代表 token 不存在、已被使用或已過期
	 */
	@Modifying
	@Query("UPDATE UserResetPasswordTokenEntity t SET t.used = true "
			+ "WHERE t.tokenHash = :hash AND t.used = false AND t.expiredAt > :now")
	int markUsedIfValid(@Param("hash") String hash, @Param("now") LocalDateTime now);

}
