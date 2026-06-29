package com.taipei.iot.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 速率限制註解 — 標註在 Controller 方法上，限制同一 IP 或同一使用者在指定時間窗口內的請求次數。
 *
 * <p>
 * 使用範例： <pre>
 * // 同一 IP 每分鐘最多 10 次請求（預設）
 * &#64;RateLimit(key = "login", limit = 10, period = 60)
 * &#64;PostMapping("/login")
 * public BaseResponse&lt;?&gt; login(...) { ... }
 *
 * // 已認證使用者依 userId 計數，匿名請求退回 IP（F-5）
 * &#64;RateLimit(key = "create-order", limit = 30, period = 60, strategy = KeyStrategy.USER_OR_IP)
 * &#64;PostMapping("/orders")
 * public BaseResponse&lt;?&gt; createOrder(...) { ... }
 * </pre>
 *
 * <p>
 * 底層透過 Redis INCR + EXPIRE 實作固定窗口計數，由 {@code RateLimitInterceptor} 攔截處理。
 *
 * <h3>Key 組成規則</h3>
 * <p>
 * Redis key 由 {@link #strategy()} 決定：
 * <ul>
 * <li>{@link KeyStrategy#IP}（預設）：{@code rate_limit:{key}:{clientIp}}</li>
 * <li>{@link KeyStrategy#USER_OR_IP}：
 * <ul>
 * <li>已認證：{@code rate_limit:{key}:user:{userId}}</li>
 * <li>匿名：{@code rate_limit:{key}:ip:{clientIp}}</li>
 * </ul>
 * </li>
 * </ul>
 *
 * <h3>F-5：strategy=USER_OR_IP 解決什麼？</h3>
 * <p>
 * 當多名使用者位於同一 NAT / VPN / 公司外網時，純 IP 計數會讓正常使用者互相誤殺； 同時惡意攻擊者繞 IP 也只是換 client
 * 的問題。{@code USER_OR_IP}：
 * <ul>
 * <li>已登入使用者各自有獨立 bucket，<b>降低共用 IP 的誤殺</b>。</li>
 * <li>匿名請求（如登入端點本身）仍依 IP 計數，<b>保留對未認證 brute-force 的防護</b>。</li>
 * </ul>
 * <p>
 * 對需要保護「同 IP 多帳號嘗試」的端點（如 /login、/forgot-password）， <b>仍應使用預設
 * {@link KeyStrategy#IP}</b>，因為這類端點的攻擊者本身就是匿名。
 * </p>
 *
 * <h3>Redis 不可用時</h3>
 * <p>
 * 攔截器會自動退回 JVM in-memory 計數器（非分散式，僅保護單節點）。
 * </p>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

	/**
	 * 限流 key 前綴（與 client identifier 組合成 Redis key； identifier 由 {@link #strategy()} 決定為
	 * IP 或 userId）。
	 */
	String key();

	/**
	 * 時間窗口內允許的最大請求次數
	 */
	int limit() default 10;

	/**
	 * 時間窗口長度（秒）
	 */
	int period() default 60;

	/**
	 * F-5：計數 key 取得策略。預設 {@link KeyStrategy#IP}（背向相容既有端點）。
	 */
	KeyStrategy strategy() default KeyStrategy.IP;

	/**
	 * F-5：速率限制 key 的計算策略。
	 */
	enum KeyStrategy {

		/**
		 * 依 client IP 計數（背向相容預設）。
		 * <p>
		 * 適用於匿名 / 未認證端點，如 {@code /login}、{@code /forgot-password}。
		 * </p>
		 */
		IP,

		/**
		 * 已認證使用者依 userId 計數，匿名請求退回 IP。
		 * <p>
		 * 適用於已認證端點，可降低同 NAT/VPN 出口 IP 的誤殺。
		 * </p>
		 */
		USER_OR_IP

	}

}
