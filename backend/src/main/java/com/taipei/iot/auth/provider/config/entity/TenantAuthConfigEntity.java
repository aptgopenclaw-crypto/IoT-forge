package com.taipei.iot.auth.provider.config.entity;

import com.taipei.iot.auth.provider.AuthType;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 各租戶的身分驗證 provider 設定（LOCAL / LDAP / OIDC / SAML 等）。
 *
 * <h3>[Tenant v2 T-2] 租戶隔離設計決策：採「全域實體 + Service 層手動過濾」策略</h3>
 * <p>
 * 本實體刻意 <b>不</b> 標註 {@code @Filter(name="tenantFilter")}、不 implement
 * {@code TenantAware}，其 Repository 也不 implement {@code TenantScopedRepository}。 理由：
 * </p>
 * <ol>
 * <li><b>查詢早於 TenantContext 設定</b>：{@code AuthenticationDispatcher.resolveConfig} 在
 * <i>登入流程中</i> 呼叫 {@code findByTenantId(loginRequest.tenantId)}，此時
 * 使用者尚未通過驗證、{@code JwtAuthenticationFilter} 未執行、{@code TenantContext} 為 null。若改為
 * {@code TenantScopedRepository}，{@code TenantFilterAspect} 的 fail-closed 策略會丟出
 * {@code IllegalStateException} → 整個登入功能無法使用。</li>
 * <li><b>tenant_id 為 UNIQUE 主索引</b>：每個 tenant 至多一筆 config，查詢一律以
 * {@code findByTenantId(tenantId)} 顯式指定，等同已啟用「應用層 tenant filter」。</li>
 * <li><b>不存在「列舉全表」入口</b>：Repository 僅暴露 {@code findByTenantId} 與
 * {@code deleteByTenantId}（後者授權於 SUPER_ADMIN）。</li>
 * </ol>
 * <p>
 * <b>對應的縱深防禦</b>：
 * </p>
 * <ul>
 * <li>機敏欄位 ({@code configJson} 含 client secret / bind password) 已由
 * {@code AuthConfigEncryptor} 加密儲存。</li>
 * <li>{@code TenantAuthConfigController} 上的所有 endpoint 必須以 {@code @PreAuthorize}
 * 限制呼叫者只能存取自身 tenant（或 SUPER_ADMIN）。</li>
 * <li><b>禁止新增 {@code findAll()} / {@code findById()} 之類無 tenant 範圍的查詢</b>。</li>
 * </ul>
 *
 * <p>
 * 變更此設計決策前，請先重構登入流程使其在呼叫 dispatcher 前已設定 TenantContext， 或將 dispatcher 改為手動以
 * {@code TenantContext.runInSystemContext} 包裹查詢。
 * </p>
 */
@Entity
@Table(name = "tenant_auth_config")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantAuthConfigEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "tenant_id", nullable = false, unique = true)
	private String tenantId;

	@Enumerated(EnumType.STRING)
	@Column(name = "auth_type", nullable = false)
	private AuthType authType;

	@Column(name = "enabled", nullable = false)
	@Builder.Default
	private Boolean enabled = true;

	@Column(name = "config_json")
	private String configJson;

	@Column(name = "fallback_local", nullable = false)
	@Builder.Default
	private Boolean fallbackLocal = true;

	@CreatedDate
	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@LastModifiedDate
	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

}
