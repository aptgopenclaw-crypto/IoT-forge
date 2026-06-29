package com.taipei.iot.auth.service.impl;

import com.taipei.iot.auth.repository.UserResetPasswordTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 在獨立的 {@link Propagation#REQUIRES_NEW} 交易中執行 reset token 的原子消耗， 使「token 被標記為
 * used」與後續密碼驗證 / 儲存解耦。
 *
 * <p>
 * [v2 N-5] 即便後續 {@code passwordValidator.validate} / {@code checkNotRecentlyUsed}
 * 或密碼儲存丟出例外導致呼叫端交易 rollback，本 claimer 的交易已先行 commit， token 仍維持 used=true，避免攻擊者用同一 token
 * 連續嘗試多組新密碼。
 * </p>
 */
@Component
@RequiredArgsConstructor
public class ResetPasswordTokenClaimer {

	private final UserResetPasswordTokenRepository userResetPasswordTokenRepository;

	/**
	 * 嘗試以 {@code tokenHash} 原子地把對應的 reset token 標記為 used。
	 * @return 是否成功 claim（true 表示 token 存在、未使用、未過期且本次成功消耗）。
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public boolean claim(String tokenHash) {
		int updated = userResetPasswordTokenRepository.markUsedIfValid(tokenHash, LocalDateTime.now());
		return updated == 1;
	}

}
