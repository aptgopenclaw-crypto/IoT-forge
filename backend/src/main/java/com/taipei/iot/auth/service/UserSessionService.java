package com.taipei.iot.auth.service;

import com.taipei.iot.auth.dto.response.SessionDto;

import java.util.List;

/**
 * [v2 N-7] 「登入裝置／Session」管理服務。
 */
public interface UserSessionService {

	/**
	 * 列出指定 user 目前所有「未撤銷且未過期」的 session。
	 * @param userId 使用者 ID
	 * @param currentJti 來自 refresh_token cookie 的 jti（可為 null）
	 */
	List<SessionDto> listMine(String userId, String currentJti);

	/**
	 * 撤銷指定 session：寫 Redis blacklist + 標記 user_session.revoked=true。 僅允許 session
	 * 擁有者本人操作；otherwise 拋 403。
	 */
	void revoke(String userId, String sessionId);

	/**
	 * [User N-4] 撤銷使用者的所有 session（排除當前 session）。 用於密碼變更後強制其他裝置重新登入。
	 * @param userId 使用者 ID
	 * @param excludeJti 當前 session 的 JTI（保留不撤銷），可為 null（撤銷全部）
	 */
	void revokeAllExceptCurrent(String userId, String excludeJti);

}
