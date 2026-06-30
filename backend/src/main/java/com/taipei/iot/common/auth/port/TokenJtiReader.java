package com.taipei.iot.common.auth.port;

/**
 * Port：從 JWT token 字串取得 JWT ID (jti)。
 *
 * <p>
 * {@code auth} 模組實作本介面；{@code user} 模組在使用者自助變更密碼流程中 需要解析當前 refresh token 的 JTI，而不需直接依賴
 * {@code auth.security.JwtUtil}。
 */
public interface TokenJtiReader {

	/**
	 * 解析 token 字串並回傳其 JWT ID (jti)。
	 * @param token JWT token 字串
	 * @return JTI 字串
	 * @throws RuntimeException 若 token 無效或已過期
	 */
	String extractJti(String token);

}
