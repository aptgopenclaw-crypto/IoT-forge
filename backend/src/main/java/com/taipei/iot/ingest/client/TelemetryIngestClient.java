package com.taipei.iot.ingest.client;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 第三方機器對機器（M2M）接入憑證。HTTP ingest 以 {@code apiKey} 查詢、{@code secretHash}（BCrypt）驗證。
 * <p>
 * 此為全域實體（非租戶過濾）：認證在租戶情境建立「之前」發生，須跨租戶以 {@code apiKey} 查得後再切換至 {@code tenantId}。
 */
@Entity
@Table(name = "telemetry_ingest_client")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TelemetryIngestClient {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "tenant_id", nullable = false, length = 50)
	private String tenantId;

	@Column(name = "client_name", nullable = false, length = 100)
	private String clientName;

	/** 公開識別碼，隨請求以 {@code X-API-Key} 標頭傳入。 */
	@Column(name = "api_key", nullable = false, unique = true, length = 64)
	private String apiKey;

	/** client secret 的 BCrypt 雜湊；驗證以 {@code X-API-Secret} 標頭比對。 */
	@Column(name = "secret_hash", nullable = false, length = 100)
	private String secretHash;

	@Column(name = "enabled", nullable = false)
	private boolean enabled;

	/** 每分鐘速率上限（null 表不限；限流於後續迭代強制）。 */
	@Column(name = "rate_limit_per_min")
	private Integer rateLimitPerMin;

	/** 允許上報的 deviceType 範圍（CSV，null 表不限；v1 不強制）。 */
	@Column(name = "device_scope", length = 200)
	private String deviceScope;

	@CreatedDate
	@Column(name = "created_at", updatable = false)
	private LocalDateTime createdAt;

	@LastModifiedDate
	@Column(name = "updated_at")
	private LocalDateTime updatedAt;

}
