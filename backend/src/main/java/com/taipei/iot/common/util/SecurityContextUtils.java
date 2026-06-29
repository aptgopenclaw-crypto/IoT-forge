package com.taipei.iot.common.util;

import com.taipei.iot.common.dto.UserInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public final class SecurityContextUtils {

	private static final Logger log = LoggerFactory.getLogger(SecurityContextUtils.class);

	/**
	 * N-9：用於跨呼叫點僅 WARN 一次，避免在 batch / 排程任務中淹沒 log。 一旦設為 {@code true} 即不再 WARN（重啟才會重置）。
	 */
	private static final AtomicBoolean OFF_REQUEST_WARNED = new AtomicBoolean(false);

	private SecurityContextUtils() {
	}

	/**
	 * 回傳已認證使用者的 userId（即 JWT subject）。
	 * <p>
	 * 未認證或 anonymous 使用者一律回傳 {@code null}。
	 * <p>
	 * <strong>⚠ 執行緒限制：</strong>本方法依賴 {@link SecurityContextHolder} 的 ThreadLocal， 在
	 * {@code @Async} 方法、{@code CompletableFuture} 等非同步執行緒中將回傳 {@code null}。 跨執行緒攜帶上下文建議使用
	 * {@code org.springframework.security.task.DelegatingSecurityContextExecutor} 或在啟動時設定
	 * {@code SecurityContextHolder.setStrategyName(MODE_INHERITABLETHREADLOCAL)}；
	 * 必要時亦可改呼叫 {@link #requireCurrentUserIdStrict()} 以 fail-fast 語意取代靜默 null。
	 * <p>
	 * <strong>N-9 防護（2026-05-27）：</strong>若呼叫端位於非請求執行緒
	 * （{@link RequestContextHolder#getRequestAttributes()} 為 {@code null}）且取不到
	 * {@link Authentication}，會 {@code log.warn} 一次以利定位誤用點。
	 */
	public static String getCurrentUserId() {
		Authentication auth = getValidAuth();
		if (auth == null)
			return null;
		return auth.getPrincipal().toString();
	}

	/**
	 * 嚴格版 {@link #getCurrentUserId()}：取不到值即拋 {@link IllegalStateException}。
	 * <p>
	 * 適用於業務語意上「必定需要 userId」的 service 入口；可避免靜默 {@code null} 在後續寫入 {@code created_by} /
	 * audit log 時被誤帶入。
	 * </p>
	 * @throws IllegalStateException 當前 thread 無有效認證使用者
	 */
	public static String requireCurrentUserIdStrict() {
		String userId = getCurrentUserId();
		if (userId == null) {
			throw new IllegalStateException("No authenticated user in current thread; "
					+ "ensure the call originates from an authenticated request, "
					+ "or use DelegatingSecurityContextExecutor to propagate "
					+ "SecurityContext into async / scheduled threads.");
		}
		return userId;
	}

	/**
	 * 回傳已認證使用者的帳號名稱。
	 * <p>
	 * 未認證或 anonymous 使用者一律回傳 {@code null}。
	 * <p>
	 * <strong>注意：</strong>在目前的 JWT 設計中，principal 即為帳號名稱（JWT subject）， 因此本方法與
	 * {@link #getCurrentUserId()} 回傳值相同。 若未來需要區分「顯示用帳號名稱」與「不可變唯一 ID」， 應在 JWT 中加入獨立的
	 * {@code username} claim 並從 details 取出。
	 * <p>
	 * <strong>⚠ 執行緒限制：</strong>同 {@link #getCurrentUserId()}，在 {@code @Async} 或非同步執行緒中將回傳
	 * {@code null}。
	 */
	public static String getCurrentUsername() {
		Authentication auth = getValidAuth();
		if (auth == null)
			return null;
		return auth.getName();
	}

	/**
	 * 從 {@link Authentication#getDetails()} 組裝完整的 {@link UserInfo}。
	 * <p>
	 * JWT filter 負責將使用者資訊存入 details（型態為 {@code Map<String, Object>}）； 若 details
	 * 不符合預期型態，則回傳僅含 userId / username 的基本物件。
	 * <p>
	 * <strong>⚠ 執行緒限制：</strong>同 {@link #getCurrentUserId()}，在 {@code @Async} 或非同步執行緒中將回傳
	 * {@code null}。
	 */
	public static UserInfo getUserInfo() {
		Authentication auth = getValidAuth();
		if (auth == null)
			return null;

		Object details = auth.getDetails();
		if (details instanceof Map) {
			// JWT filter 保證 details 為 Map<String, Object>，此轉型安全
			@SuppressWarnings("unchecked")
			Map<String, Object> detailsMap = (Map<String, Object>) details;

			Long deptId = null;
			Object deptIdVal = detailsMap.get(JwtClaimKeys.DEPT_ID);
			if (deptIdVal instanceof Number) {
				deptId = ((Number) deptIdVal).longValue();
			}
			else if (deptIdVal instanceof String deptIdStr && !deptIdStr.isEmpty()) {
				try {
					deptId = Long.parseLong(deptIdStr);
				}
				catch (NumberFormatException ignored) {
				}
			}

			// 使用 instanceof pattern，避免非 String 型別時 ClassCastException
			String tenantId = detailsMap.get(JwtClaimKeys.TENANT_ID) instanceof String s ? s : null;
			String dataScope = detailsMap.get(JwtClaimKeys.DATA_SCOPE) instanceof String s ? s : null;

			return UserInfo.builder()
				.userId(auth.getPrincipal().toString())
				.username(auth.getName())
				.tenantId(tenantId)
				.deptId(deptId)
				.dataScope(dataScope)
				.build();
		}

		return UserInfo.builder().userId(auth.getPrincipal().toString()).username(auth.getName()).build();
	}

	/**
	 * 判斷當前使用者是否持有指定權限（authority）中的任何一個。 未認證或 anonymous 使用者一律回傳 {@code false}。
	 */
	public static boolean hasAnyAuthority(String... authorities) {
		Authentication auth = getValidAuth();
		if (auth == null)
			return false;
		Set<String> required = Set.of(authorities);
		return auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).anyMatch(required::contains);
	}

	// --- package-private hook for tests ---

	/**
	 * 重置「off-request-thread WARN once」旗標。僅供測試使用。
	 */
	static void resetOffRequestWarnedForTest() {
		OFF_REQUEST_WARNED.set(false);
	}

	// --- private helpers ---

	/**
	 * 取得有效（已認證且非 anonymous）的 Authentication；否則回傳 {@code null}。
	 * <p>
	 * N-9：在非請求 thread 上首次取不到 auth 時記一筆 WARN（process 內僅一次）， 便於 grep 找到誤把需要 SecurityContext
	 * 的程式碼放入 {@code @Async} / scheduler 的場景。
	 * </p>
	 */
	private static Authentication getValidAuth() {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth == null || !auth.isAuthenticated() || isAnonymous(auth)) {
			maybeWarnOffRequestThread();
			return null;
		}
		return auth;
	}

	/**
	 * 若呼叫端不在 Servlet 請求 thread 上（無 {@link RequestAttributes}）則 WARN 一次。 一般 HTTP
	 * 請求即便是匿名使用者也會有 RequestAttributes，因此不會誤觸。
	 */
	private static void maybeWarnOffRequestThread() {
		try {
			RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
			if (attrs == null && OFF_REQUEST_WARNED.compareAndSet(false, true)) {
				log.warn("SecurityContextUtils called from a non-request thread with no "
						+ "Authentication (likely @Async / scheduler / CompletableFuture). "
						+ "Returning null. Use DelegatingSecurityContextExecutor or pass "
						+ "userId explicitly. This warning is emitted only once per JVM.");
			}
		}
		catch (IllegalStateException ignored) {
			// RequestContextHolder may throw in rare setups — fail-silent
		}
	}

	/**
	 * 判斷是否為 anonymous 使用者。
	 * <p>
	 * 使用型別判斷而非字串比對，確保即使 anonymous principal 被自訂 （透過
	 * {@code HttpSecurity.anonymous().principal(...)}），行為仍然正確。
	 */
	private static boolean isAnonymous(Authentication auth) {
		return auth instanceof AnonymousAuthenticationToken;
	}

}
