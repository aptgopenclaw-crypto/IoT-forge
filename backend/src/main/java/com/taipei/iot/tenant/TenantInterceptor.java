package com.taipei.iot.tenant;

import com.taipei.iot.common.enums.ErrorCode;
import com.taipei.iot.common.enums.SecurityEvent;
import com.taipei.iot.common.exception.BusinessException;
import com.taipei.iot.common.util.SecurityLogger;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 將請求對應的 {@code tenantId} 寫入 {@link TenantContext}，供下游 service/repository 使用。
 *
 * <h3>模式行為</h3>
 * <ul>
 * <li><b>{@code multi}</b>：不做任何事；{@code JwtAuthenticationFilter} 已先依 JWT 設定。</li>
 * <li><b>{@code single}</b>：強制覆寫為 {@code tenant.default-id}。<br>
 * <b>[Tenant v2 T-5]</b> 若 JWT 同時帶有與 {@code defaultId} 不同的 {@code tenantId} （且非 SYSTEM
 * context），會記錄 {@link SecurityEvent#TENANT_MODE_MISMATCH} 並 拋出
 * {@link ErrorCode#TENANT_MODE_MISMATCH}（403）。這可避免「production 被誤設為 single 模式 → 跨租戶 JWT
 * 被靜默歸併到 DEFAULT」的資料污染。</li>
 * </ul>
 *
 * <h3>啟動期觀測</h3>
 * <p>
 * {@link #logMode()} 會於 bean 初始化後印出當前 {@code tenant.mode} 與 {@code tenant.default-id}，
 * 方便運維在 boot log 直接確認部署設定。
 * </p>
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class TenantInterceptor implements HandlerInterceptor {

	static final String MODE_SINGLE = "single";

	private final TenantProperties tenantProperties;

	@PostConstruct
	void logMode() {
		if (MODE_SINGLE.equals(tenantProperties.getMode())) {
			log.warn(
					"[TenantInterceptor] tenant.mode=single — all requests will be pinned to "
							+ "tenant.default-id='{}'. JWT tenantId mismatches will be rejected with 403.",
					tenantProperties.getDefaultId());
		}
		else {
			log.info("[TenantInterceptor] tenant.mode={} — tenantId derived from JWT per request.",
					tenantProperties.getMode());
		}
	}

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
		if (!MODE_SINGLE.equals(tenantProperties.getMode())) {
			// multi 模式：由 AUTH 的 JwtAuthenticationFilter 從 JWT 設定
			return true;
		}

		String defaultId = tenantProperties.getDefaultId();
		String jwtTenant = TenantContext.getCurrentTenantId();

		// [Tenant v2 T-5] 若 JWT 已帶 tenantId 且與 defaultId 不一致，拒絕請求並記錄安全事件。
		// SYSTEM context（內部排程 / cross-tenant 工具）以及尚未經 JWT 設定（jwtTenant=null
		// 的公開端點，例如 /v1/auth/login）放行。
		if (jwtTenant != null && !TenantContext.isSystemContext() && !jwtTenant.equals(defaultId)) {
			SecurityLogger.warn(SecurityEvent.TENANT_MODE_MISMATCH, request.getRemoteAddr(),
					"path=" + request.getRequestURI() + " jwtTenant=" + jwtTenant + " defaultId=" + defaultId);
			// 清掉 ThreadLocal 避免後續流程誤用
			TenantContext.clear();
			throw new BusinessException(ErrorCode.TENANT_MODE_MISMATCH);
		}

		// 強制覆寫為部署預設值（與 JWT 一致時也統一走此路徑）
		TenantContext.setCurrentTenantId(defaultId);
		return true;
	}

	@Override
	public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
			Exception ex) {
		TenantContext.clear();
	}

}
