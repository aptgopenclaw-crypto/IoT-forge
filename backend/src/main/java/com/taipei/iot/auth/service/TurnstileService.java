package com.taipei.iot.auth.service;

/**
 * Cloudflare Turnstile 驗證服務。
 *
 * <p>
 * 與圖片驗證碼（{@link CaptchaService}）並存，提供第二種驗證方式：
 * <ul>
 * <li>圖片驗證碼：傳統方式，由後端產生圖片，使用者手動輸入文字</li>
 * <li>Turnstile：前端嵌入 Cloudflare widget，使用者幾乎無感，後端驗證 token</li>
 * </ul>
 *
 * <p>
 * 前端在登入時可選擇使用哪種方式，後端根據請求中的參數決定走哪條驗證路徑。
 */
public interface TurnstileService {

	/**
	 * 驗證 Cloudflare Turnstile token。
	 * @param token 前端 Turnstile widget 回傳的 token（cf-turnstile-response）
	 * @param remoteIp 使用者的 IP（可選，增加驗證準確度）
	 * @return true 表示驗證通過
	 */
	boolean verify(String token, String remoteIp);

	/**
	 * Turnstile 功能是否已啟用（有設定 secret key 才啟用）。
	 */
	boolean isEnabled();

}
