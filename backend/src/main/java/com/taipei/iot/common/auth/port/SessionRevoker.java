package com.taipei.iot.common.auth.port;

/**
 * Port：撤銷使用者的登入 Session。
 *
 * <p>
 * {@code auth} 模組實作本介面；{@code user} 模組在密碼變更後呼叫以強制其他裝置登出， 而不需直接依賴
 * {@code auth.service.UserSessionService}。
 */
public interface SessionRevoker {

	/**
	 * 撤銷指定使用者除當前 Session 之外的所有有效 Session。
	 * @param userId 使用者 ID
	 * @param currentJti 當前 Session 的 JWT ID（保留此 session）
	 */
	void revokeAllExceptCurrent(String userId, String currentJti);

}
