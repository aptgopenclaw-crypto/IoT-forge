package com.taipei.iot.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * [v2 N-7] 紀錄一個 refresh token / 登入裝置的伺服端 session。
 *
 * <p>
 * {@code sessionId} 與 JWT 的 {@code jti} claim 同值，使「Redis revocation list」 與「user_session
 * 表」可以互查；refresh token rotation 時舊 row 標記為 {@code revoked=true} 並新增一筆 row 對應新的 jti。
 * </p>
 *
 * <h3>[Tenant v2 T-2] 租戶隔離設計決策：採「全域實體 + Service 層手動過濾」策略</h3>
 * <p>
 * 本實體刻意 <b>不</b> 標註 {@code @Filter(name="tenantFilter")}、不 implement
 * {@code TenantAware}，其 Repository 也不 implement {@code TenantScopedRepository}。 理由：
 * </p>
 * <ol>
 * <li><b>主鍵為全域唯一</b>：{@code sessionId} = JWT jti（128-bit 隨機字串），全域唯一， 不存在「猜 ID
 * 跨租戶讀取」風險。</li>
 * <li><b>跨租戶 session 為合法情境</b>：SUPER_ADMIN 可在多租戶間切換，同一個 refresh token rotation 鏈中
 * {@code tenantId} 欄位會隨「最近一次登入的 tenant」更新。 若加 {@code @Filter}，在 tenant B context 下將無法找到原
 * tenant A 建立的 session → 中斷 refresh-token rotation。</li>
 * <li><b>查詢時機特殊</b>：session 撤銷與查詢經常發生在 {@code TenantContext} 尚未
 * 設定（登入前）或將被清除（登出時）的時刻，{@code @Filter} 之 fail-closed 策略會 誤殺正常請求。</li>
 * </ol>
 * <p>
 * <b>對應的縱深防禦</b>：service 層 ({@code AuthServiceImpl} / {@code SessionRevocationService})
 * 必須以 {@code sessionId} + {@code userId} 雙重定位 session，禁止以 {@code findAll()} 列出全表，禁止以僅
 * {@code sessionId} 查詢後直接信任 {@code tenantId} 欄位。
 * </p>
 *
 * <p>
 * 變更此設計決策前，請通讀 {@code AuthServiceImpl.refreshToken / logout} 流程，並 確保
 * {@code TenantContext} 在所有呼叫點皆已正確設定。
 * </p>
 */
@Entity
@Table(name = "user_session")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSessionEntity {

	/** = JWT jti claim of the refresh token bound to this session */
	@Id
	@Column(name = "session_id", length = 64)
	private String sessionId;

	@Column(name = "user_id", length = 50, nullable = false)
	private String userId;

	/** Refresh token 發行時的 tenant context；super-admin 可能多 tenant，紀錄最新者 */
	@Column(name = "tenant_id", length = 50)
	private String tenantId;

	/** 簽發來源 IP（已考量 X-Forwarded-For 之 first entry 由呼叫端傳入） */
	@Column(name = "ip_address", length = 64)
	private String ipAddress;

	@Column(name = "user_agent", length = 512)
	private String userAgent;

	@Column(name = "issued_at", nullable = false)
	private LocalDateTime issuedAt;

	/** 每次 refresh 時更新，做為「最近活動時間」 */
	@Column(name = "last_seen_at", nullable = false)
	private LocalDateTime lastSeenAt;

	@Column(name = "expires_at", nullable = false)
	private LocalDateTime expiresAt;

	@Column(name = "revoked", nullable = false)
	@Builder.Default
	private Boolean revoked = false;

	@Column(name = "revoked_at")
	private LocalDateTime revokedAt;

}
