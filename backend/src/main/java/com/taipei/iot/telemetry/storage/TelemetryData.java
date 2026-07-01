package com.taipei.iot.telemetry.storage;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 時序遙測列，對應原生 PostgreSQL 月分區表 {@code telemetry_data}。
 *
 * <p>
 * 此表為 append-only 高量寫入，租戶隔離以查詢層的 {@code tenant_id} 條件處理（Step 5），故不掛 Hibernate
 * {@code tenantFilter}；寫入時由 {@link TelemetryStore} 帶入明確的 {@code tenantId}。
 *
 * <p>
 * 資料庫實體 PK 為複合鍵 {@code (ts, id)}（分區鍵須屬於 PK），其中 {@code id} 為
 * {@code GENERATED ALWAYS AS IDENTITY}，全域唯一，足以作為 JPA 實體識別。
 */
@Entity
@Table(name = "telemetry_data")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TelemetryData {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "tenant_id", nullable = false, length = 50)
	private String tenantId;

	@Column(name = "device_id", nullable = false)
	private Long deviceId;

	@Column(name = "device_type", nullable = false, length = 30)
	private String deviceType;

	@Column(name = "ts", nullable = false)
	private LocalDateTime ts;

	@Column(name = "received_at", nullable = false)
	private LocalDateTime receivedAt;

	@Column(name = "source", nullable = false, length = 20)
	private String source;

	@Column(name = "source_client_id", length = 50)
	private String sourceClientId;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "payload", nullable = false, columnDefinition = "jsonb")
	private Map<String, Object> payload;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "raw_payload", columnDefinition = "jsonb")
	private Map<String, Object> rawPayload;

	@Column(name = "valid", nullable = false)
	private boolean valid;

	@Column(name = "validation_msg", length = 500)
	private String validationMsg;

}
