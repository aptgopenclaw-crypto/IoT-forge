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
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 外部設備碼 → 內部 {@code deviceCode} 映射。第三方常以自家編碼上報，HTTP ingest 依 {@code (tenantId,
 * externalCode)} 解出內部 {@code deviceCode} 再交給 telemetry 核心。
 * <p>
 * 全域實體（非租戶過濾）：以明確 {@code tenant_id} 欄位查詢，避免依賴 ThreadLocal filter 情境。
 */
@Entity
@Table(name = "device_external_ref")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceExternalRef {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "tenant_id", nullable = false, length = 50)
	private String tenantId;

	/** 來源憑證（選填，便於審計/限縮）。 */
	@Column(name = "client_id")
	private Long clientId;

	@Column(name = "external_code", nullable = false, length = 100)
	private String externalCode;

	@Column(name = "device_code", nullable = false, length = 100)
	private String deviceCode;

	@Column(name = "enabled", nullable = false)
	private boolean enabled;

	@CreatedDate
	@Column(name = "created_at", updatable = false)
	private LocalDateTime createdAt;

}
